package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.BrokerConfig
import com.example.data.model.DepositRequest
import com.example.data.model.FundingMethod
import com.example.data.model.KycSubmission
import com.example.data.model.MarketAsset
import com.example.data.model.PortfolioHolding
import com.example.data.model.TradeOrder
import com.example.data.model.UserAccount
import com.example.data.model.WatchlistEntry
import com.example.data.model.WithdrawalRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

class BrokerRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val emailRepo: EmailDeliveryRepository = EmailDeliveryRepository(db, auth),
    private val opsRepo: EnterpriseOpsRepository = EnterpriseOpsRepository(db, auth, emailRepo)
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun appendLedgerInTx(
        tx: Transaction,
        userId: String,
        entryType: String,
        direction: String,
        amountUsd: Double,
        balanceBeforeUsd: Double,
        balanceAfterUsd: Double,
        referenceType: String,
        referenceId: String,
        description: String,
        actorUid: String
    ) {
        val ledgerRef = db.collection("ledger_entries").document()
        tx.set(
            ledgerRef,
            mapOf(
                "userId" to userId,
                "entryType" to entryType,
                "direction" to direction,
                "amountUsd" to amountUsd,
                "balanceBeforeUsd" to balanceBeforeUsd,
                "balanceAfterUsd" to balanceAfterUsd,
                "referenceType" to referenceType,
                "referenceId" to referenceId,
                "description" to description.take(300),
                "actorUid" to actorUid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }

    // --- User Account & Profile ---

    fun observeUserAccount(userId: String): Flow<UserAccount?> = flow {
        val docRef = db.collection("users").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun ensureUserProfile(
        displayName: String,
        email: String,
        photoUrl: String?
    ): Result<UserAccount> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        val generatedReferral = "APEX-${uid.take(6).uppercase()}"
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                val account = existing.toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserAccount(id = uid, userId = uid, displayName = displayName, email = email, referralCode = generatedReferral)
                if (account.referralCode.isBlank()) {
                    runCatching {
                        docRef.update(
                            mapOf(
                                "referralCode" to generatedReferral,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                }
                Result.success(if (account.referralCode.isBlank()) account.copy(referralCode = generatedReferral) else account)
            } else {
                val cleanName = displayName.ifBlank { email.substringBefore("@").ifBlank { "Global Trader" } }.take(100)
                val cleanEmail = email.ifBlank { "$uid@apextrade.user" }.take(200)
                val payload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", cleanName)
                    put("email", cleanEmail)
                    if (!photoUrl.isNullOrBlank()) {
                        put("photoUrl", photoUrl.take(500))
                    }
                    put("cashBalanceUsd", 0.0)
                    put("totalDepositedUsd", 0.0)
                    put("pendingWithdrawalUsd", 0.0)
                    put("kycStatus", "not_submitted")
                    put("traderMode", "beginner")
                    put("accountStatus", "active")
                    put("referralCode", generatedReferral)
                    put("twoFactorPinEnabled", false)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
                val created = docRef.get().await()
                    .toObject(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: UserAccount(id = uid, userId = uid, displayName = cleanName, email = cleanEmail, referralCode = generatedReferral)
                opsRepo.createNotification(
                    userId = uid,
                    title = "Welcome to ApexTrade",
                    message = "Complete KYC identity verification in Portfolio to unlock deposits & withdrawals.",
                    category = "SECURITY",
                    severity = "INFO",
                    actionRoute = "portfolio"
                )
                Result.success(created)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTraderMode(mode: String): Result<Unit> {
        val uid = requireUserId()
        require(mode in listOf("beginner", "pro")) { "Invalid trader mode" }
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "traderMode" to mode,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateTwoFactorPinEnabled(enabled: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "twoFactorPinEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            opsRepo.createNotification(
                userId = uid,
                title = if (enabled) "App Security PIN Enabled" else "App Security PIN Disabled",
                message = if (enabled) "2FA Security PIN lock is now active on this device." else "App PIN lock was turned off in Security Settings.",
                category = "SECURITY",
                severity = if (enabled) "SUCCESS" else "WARNING",
                actionRoute = "account"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun applyReferralCode(code: String): Result<Unit> {
        val uid = requireUserId()
        val clean = code.trim().uppercase().take(32)
        require(clean.length >= 4) { "Enter a valid referral code." }
        val docRef = db.collection("users").document(uid)
        return try {
            docRef.update(
                mapOf(
                    "referredBy" to clean,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- KYC Verification Flow (Pending 24h Review) ---

    fun observeUserKyc(userId: String): Flow<KycSubmission?> = flow {
        val docRef = db.collection("kyc_requests").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(KycSubmission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    } else null
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun submitKyc(
        userEmail: String,
        fullLegalName: String,
        country: String,
        dateOfBirth: String,
        idType: String,
        idNumber: String,
        docImageBase64: String
    ): Result<Unit> {
        val uid = requireUserId()
        val kycRef = db.collection("kyc_requests").document(uid)
        val userRef = db.collection("users").document(uid)
        return try {
            val cleanEmail = userEmail.ifBlank { "$uid@apextrade.user" }.take(200)
            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val currentKyc = userSnap.getString("kycStatus") ?: "not_submitted"
                if (currentKyc == "approved") {
                    throw IllegalStateException("KYC is already approved.")
                }
                val existingKyc = tx.get(kycRef)
                if (existingKyc.exists()) {
                    tx.update(
                        kycRef,
                        mapOf(
                            "fullLegalName" to fullLegalName.trim().take(120),
                            "country" to country.trim().take(80),
                            "dateOfBirth" to dateOfBirth.trim().take(30),
                            "idType" to idType,
                            "idNumber" to idNumber.trim().take(60),
                            "docImageBase64" to docImageBase64,
                            "status" to "pending",
                            "rejectionReason" to FieldValue.delete(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    tx.set(
                        kycRef,
                        mapOf(
                            "userId" to uid,
                            "userEmail" to cleanEmail,
                            "fullLegalName" to fullLegalName.trim().take(120),
                            "country" to country.trim().take(80),
                            "dateOfBirth" to dateOfBirth.trim().take(30),
                            "idType" to idType,
                            "idNumber" to idNumber.trim().take(60),
                            "docImageBase64" to docImageBase64,
                            "status" to "pending",
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                tx.update(
                    userRef,
                    mapOf(
                        "kycStatus" to "pending",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            opsRepo.createNotification(
                userId = uid,
                title = "KYC Submitted (Pending)",
                message = "System will review and approve within 24 hours.",
                category = "KYC",
                severity = "INFO",
                actionRoute = "portfolio"
            )
            emailRepo.enqueueAndDispatchEmail(
                userId = uid,
                recipientEmail = cleanEmail,
                templateKey = "KYC_SUBMITTED",
                variables = mapOf("name" to fullLegalName.trim(), "idType" to idType),
                idempotencyKey = "kyc_submit_${uid}_${System.currentTimeMillis() / 60_000L}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, kycRef.path)
            Result.failure(e)
        }
    }

    // --- Deposit Flow (Pending until Admin Approves) ---

    fun observeUserDeposits(userId: String): Flow<List<DepositRequest>> = flow {
        val query = db.collection("deposits").whereEqualTo("userId", userId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DepositRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "deposits")
                    }
                    throw error
                }
        )
    }

    suspend fun submitDepositRequest(
        amountUsd: Double,
        method: FundingMethod,
        walletAddressUsed: String,
        txHash: String,
        proofImageBase64: String,
        depositIdOverride: String? = null
    ): Result<Unit> {
        val uid = requireUserId()
        require(amountUsd >= 10.0 && amountUsd <= 10_000_000.0) { "Deposit amount must be at least $10.00" }
        require(txHash.trim().length >= 4) { "Enter a valid transaction hash or reference ID" }
        require(proofImageBase64.length >= 10) { "Please upload your proof of deposit screenshot" }

        val userRef = db.collection("users").document(uid)
        val depositRef = if (depositIdOverride.isNullOrBlank()) {
            db.collection("deposits").document()
        } else {
            db.collection("deposits").document(depositIdOverride)
        }

        return try {
            var userDisplayName = "Global Trader"
            var userEmail = "$uid@apextrade.user"
            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val kycStatus = userSnap.getString("kycStatus") ?: "not_submitted"
                if (kycStatus != "approved") {
                    throw IllegalStateException("KYC verification must be approved before making a deposit.")
                }
                userDisplayName = userSnap.getString("displayName") ?: "Global Trader"
                userEmail = userSnap.getString("email") ?: "$uid@apextrade.user"
                tx.set(
                    depositRef,
                    mapOf(
                        "userId" to uid,
                        "userDisplayName" to userDisplayName,
                        "userEmail" to userEmail,
                        "amountUsd" to amountUsd,
                        "method" to method.wireValue,
                        "walletAddressUsed" to walletAddressUsed.take(300),
                        "txHash" to txHash.trim().take(180),
                        "proofImageBase64" to proofImageBase64,
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            val amtStr = "$${String.format("%.2f", amountUsd)}"
            opsRepo.createNotification(
                userId = uid,
                title = "Deposit Submitted ($amtStr)",
                message = "Proof uploaded (${method.displayName}). System will review and approve within 24 hours.",
                category = "DEPOSIT",
                severity = "INFO",
                actionRoute = "portfolio"
            )
            emailRepo.enqueueAndDispatchEmail(
                userId = uid,
                recipientEmail = userEmail,
                templateKey = "DEPOSIT_SUBMITTED",
                variables = mapOf("name" to userDisplayName, "amount" to amtStr, "method" to method.displayName, "txHash" to txHash.trim()),
                idempotencyKey = "dep_submit_${depositRef.id}"
            )
            if (amountUsd >= 10_000.0) {
                opsRepo.createAmlAlert(
                    userId = uid,
                    userEmail = userEmail,
                    alertType = "LARGE_DEPOSIT",
                    severity = if (amountUsd >= 50_000.0) "CRITICAL" else "HIGH",
                    amountUsd = amountUsd,
                    referenceId = depositRef.id,
                    summary = "Large deposit proof submitted ($amtStr via ${method.displayName})"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, depositRef.path)
            Result.failure(e)
        }
    }

    // --- Withdrawal Flow (Pending until Admin Pays Manually) ---

    fun observeUserWithdrawals(userId: String): Flow<List<WithdrawalRequest>> = flow {
        val query = db.collection("withdrawals").whereEqualTo("userId", userId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WithdrawalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "withdrawals")
                    }
                    throw error
                }
        )
    }

    suspend fun submitWithdrawalRequest(
        amountUsd: Double,
        method: FundingMethod,
        destinationDetails: String,
        accountHolderName: String,
        withdrawalIdOverride: String? = null
    ): Result<Unit> {
        val uid = requireUserId()
        require(amountUsd >= 10.0) { "Minimum withdrawal is $10.00" }
        require(destinationDetails.trim().length >= 5) { "Enter valid destination wallet address or bank details" }
        require(accountHolderName.trim().length >= 2) { "Enter beneficiary / account holder name" }

        val userRef = db.collection("users").document(uid)
        val withdrawalRef = if (withdrawalIdOverride.isNullOrBlank()) {
            db.collection("withdrawals").document()
        } else {
            db.collection("withdrawals").document(withdrawalIdOverride)
        }

        return try {
            var userDisplayName = "Global Trader"
            var userEmail = "$uid@apextrade.user"
            db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                val status = userSnap.getString("accountStatus") ?: "active"
                if (status == "suspended") {
                    throw IllegalStateException("Account suspended. Withdrawals disabled.")
                }
                val kycStatus = userSnap.getString("kycStatus") ?: "not_submitted"
                if (kycStatus != "approved") {
                    throw IllegalStateException("KYC verification must be approved before requesting a withdrawal.")
                }
                val cashBalance = userSnap.getDouble("cashBalanceUsd") ?: 0.0
                val pendingWd = userSnap.getDouble("pendingWithdrawalUsd") ?: 0.0
                if (cashBalance + 1e-6 < amountUsd) {
                    throw IllegalStateException("Insufficient available cash balance for withdrawal.")
                }
                val nextCash = (cashBalance - amountUsd).coerceAtLeast(0.0)
                userDisplayName = userSnap.getString("displayName") ?: "Global Trader"
                userEmail = userSnap.getString("email") ?: "$uid@apextrade.user"

                tx.update(
                    userRef,
                    mapOf(
                        "cashBalanceUsd" to nextCash,
                        "pendingWithdrawalUsd" to pendingWd + amountUsd,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                tx.set(
                    withdrawalRef,
                    mapOf(
                        "userId" to uid,
                        "userDisplayName" to userDisplayName,
                        "userEmail" to userEmail,
                        "amountUsd" to amountUsd,
                        "method" to method.wireValue,
                        "destinationDetails" to destinationDetails.trim().take(350),
                        "accountHolderName" to accountHolderName.trim().take(120),
                        "status" to "pending",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                appendLedgerInTx(
                    tx = tx,
                    userId = uid,
                    entryType = "WITHDRAWAL_RESERVE",
                    direction = "DEBIT",
                    amountUsd = amountUsd,
                    balanceBeforeUsd = cashBalance,
                    balanceAfterUsd = nextCash,
                    referenceType = "withdrawal",
                    referenceId = withdrawalRef.id,
                    description = "Reserved for pending ${method.displayName} payout",
                    actorUid = uid
                )
            }.await()

            val amtStr = "$${String.format("%.2f", amountUsd)}"
            opsRepo.createNotification(
                userId = uid,
                title = "Withdrawal Pending ($amtStr)",
                message = "Reserved for ${method.displayName}. System will review and approve within 24 hours.",
                category = "WITHDRAWAL",
                severity = "INFO",
                actionRoute = "portfolio"
            )
            emailRepo.enqueueAndDispatchEmail(
                userId = uid,
                recipientEmail = userEmail,
                templateKey = "WITHDRAWAL_REQUESTED",
                variables = mapOf("name" to userDisplayName, "amount" to amtStr, "method" to method.displayName),
                idempotencyKey = "wd_req_${withdrawalRef.id}"
            )
            if (amountUsd >= 5_000.0) {
                opsRepo.createAmlAlert(
                    userId = uid,
                    userEmail = userEmail,
                    alertType = "LARGE_WITHDRAWAL",
                    severity = if (amountUsd >= 25_000.0) "CRITICAL" else "HIGH",
                    amountUsd = amountUsd,
                    referenceId = withdrawalRef.id,
                    summary = "High-value withdrawal request ($amtStr to ${destinationDetails.take(40)})"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, withdrawalRef.path)
            Result.failure(e)
        }
    }

    // --- Holdings & Watchlist ---

    fun observeHoldings(userId: String): Flow<List<PortfolioHolding>> = flow {
        val colRef = db.collection("users").document(userId).collection("holdings")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(PortfolioHolding::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeWatchlist(userId: String): Flow<Set<String>> = flow {
        val colRef = db.collection("users").document(userId).collection("watchlist")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WatchlistEntry::class.java)
                        .map { it.symbol }
                        .toSet()
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun toggleWatchlist(asset: MarketAsset, shouldWatch: Boolean): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
            .collection("watchlist").document(asset.sanitizedDocId)
        return try {
            if (shouldWatch) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "symbol" to asset.symbol,
                        "addedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (shouldWatch) OperationType.CREATE else OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- User Trades & Live Broker Config ---

    fun observeUserTrades(userId: String): Flow<List<TradeOrder>> = flow {
        val query = db.collection("trades").whereEqualTo("userId", userId)
        emitAll(
            query.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TradeOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, "trades")
                    }
                    throw error
                }
        )
    }

    fun observeBrokerConfig(): Flow<BrokerConfig> = flow {
        val docRef = db.collection("broker_config").document("global")
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(BrokerConfig::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: BrokerConfig()
                    } else {
                        BrokerConfig()
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, docRef.path)
                    }
                    throw error
                }
        )
    }

    // --- Trade Execution (MARKET, LIMIT, STOP, STOP_LIMIT + Pending Orders + Double-Entry Ledger) ---

    suspend fun executeTrade(
        asset: MarketAsset,
        side: String,
        orderType: String,
        quantity: Double,
        executionPriceUsd: Double,
        brokerConfig: BrokerConfig,
        triggerPriceUsd: Double? = null,
        tradeIdOverride: String? = null
    ): Result<TradeOrder> {
        val uid = requireUserId()
        require(side in listOf("BUY", "SELL")) { "Side must be BUY or SELL" }
        require(orderType in listOf("MARKET", "LIMIT", "STOP", "STOP_LIMIT")) { "Unsupported order type" }
        require(quantity > 0.0) { "Quantity must be positive" }
        require(executionPriceUsd > 0.0) { "Price must be positive" }

        val userRef = db.collection("users").document(uid)
        val holdingRef = userRef.collection("holdings").document(asset.sanitizedDocId)
        val tradeRef = if (tradeIdOverride.isNullOrBlank()) {
            db.collection("trades").document()
        } else {
            db.collection("trades").document(tradeIdOverride)
        }

        val immediateFill = when (orderType) {
            "MARKET" -> true
            "LIMIT" -> (side == "BUY" && executionPriceUsd >= asset.currentPriceUsd) ||
                (side == "SELL" && executionPriceUsd <= asset.currentPriceUsd)
            "STOP" -> {
                val trig = triggerPriceUsd ?: executionPriceUsd
                (side == "BUY" && asset.currentPriceUsd >= trig) ||
                    (side == "SELL" && asset.currentPriceUsd <= trig)
            }
            else -> false // STOP_LIMIT rests in order book pending trigger
        }

        val effectivePrice = if (orderType == "MARKET" || (orderType == "LIMIT" && immediateFill)) {
            if (orderType == "MARKET") executionPriceUsd else asset.currentPriceUsd
        } else {
            executionPriceUsd
        }

        val grossAmount = quantity * effectivePrice
        val commissionBps = brokerConfig.commissionBpsFor(asset.assetClass)
        val commissionUsd = grossAmount * (commissionBps / 10_000.0)
        val netTotalUsd = if (side == "BUY") grossAmount + commissionUsd else (grossAmount - commissionUsd).coerceAtLeast(0.0)

        return try {
            if (brokerConfig.tradingHalted) {
                return Result.failure(IllegalStateException("Global trading is currently halted by Brokerage Admin."))
            }
            if (asset.symbol in brokerConfig.haltedSymbols) {
                return Result.failure(IllegalStateException("${asset.symbol} is temporarily halted by Brokerage Admin."))
            }
            if (grossAmount < brokerConfig.minOrderUsd) {
                return Result.failure(IllegalStateException("Minimum order size is $${String.format("%.2f", brokerConfig.minOrderUsd)}."))
            }

            val orderStatus = if (immediateFill) "FILLED" else "PENDING"
            val executedOrder = db.runTransaction { tx ->
                val userSnap = tx.get(userRef)
                if (!userSnap.exists()) throw IllegalStateException("Brokerage account not initialized.")
                val status = userSnap.getString("accountStatus") ?: "active"
                if (status == "suspended") throw IllegalStateException("Account suspended by Admin. Trading disabled.")
                val cashBalance = userSnap.getDouble("cashBalanceUsd") ?: 0.0
                val displayName = userSnap.getString("displayName") ?: "Global Trader"
                val userEmail = userSnap.getString("email")
                val holdingSnap = tx.get(holdingRef)

                if (side == "BUY") {
                    if (cashBalance + 1e-6 < netTotalUsd) {
                        throw IllegalStateException("Insufficient buying power. Submit a Deposit or reduce order size.")
                    }
                    val newCash = (cashBalance - netTotalUsd).coerceAtLeast(0.0)
                    tx.update(userRef, mapOf("cashBalanceUsd" to newCash, "updatedAt" to FieldValue.serverTimestamp()))
                    appendLedgerInTx(
                        tx = tx,
                        userId = uid,
                        entryType = if (immediateFill) "TRADE_BUY_DEBIT" else "TRADE_ORDER_RESERVE",
                        direction = "DEBIT",
                        amountUsd = netTotalUsd,
                        balanceBeforeUsd = cashBalance,
                        balanceAfterUsd = newCash,
                        referenceType = "trade",
                        referenceId = tradeRef.id,
                        description = "$orderType BUY ${String.format("%.4f", quantity)} ${asset.symbol} @ $${String.format("%.2f", effectivePrice)}",
                        actorUid = uid
                    )

                    if (immediateFill) {
                        if (holdingSnap.exists()) {
                            val oldQty = holdingSnap.getDouble("quantity") ?: 0.0
                            val oldAvg = holdingSnap.getDouble("avgBuyPriceUsd") ?: effectivePrice
                            val totalQty = oldQty + quantity
                            val newAvg = ((oldQty * oldAvg) + grossAmount) / totalQty
                            tx.update(holdingRef, mapOf("quantity" to totalQty, "avgBuyPriceUsd" to newAvg, "updatedAt" to FieldValue.serverTimestamp()))
                        } else {
                            tx.set(
                                holdingRef,
                                mapOf(
                                    "userId" to uid,
                                    "symbol" to asset.symbol,
                                    "assetName" to asset.name,
                                    "assetClass" to asset.assetClass.wireValue,
                                    "countryCode" to asset.countryCode,
                                    "exchange" to asset.exchange,
                                    "quantity" to quantity,
                                    "avgBuyPriceUsd" to effectivePrice,
                                    "createdAt" to FieldValue.serverTimestamp(),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                } else {
                    if (!holdingSnap.exists()) throw IllegalStateException("No open position in ${asset.symbol} to sell.")
                    val oldQty = holdingSnap.getDouble("quantity") ?: 0.0
                    if (oldQty + 1e-8 < quantity) throw IllegalStateException("Insufficient ${asset.symbol} shares to sell.")
                    if (immediateFill) {
                        val remaining = oldQty - quantity
                        val newCash = cashBalance + netTotalUsd
                        tx.update(userRef, mapOf("cashBalanceUsd" to newCash, "updatedAt" to FieldValue.serverTimestamp()))
                        appendLedgerInTx(
                            tx = tx,
                            userId = uid,
                            entryType = "TRADE_SELL_CREDIT",
                            direction = "CREDIT",
                            amountUsd = netTotalUsd,
                            balanceBeforeUsd = cashBalance,
                            balanceAfterUsd = newCash,
                            referenceType = "trade",
                            referenceId = tradeRef.id,
                            description = "$orderType SELL ${String.format("%.4f", quantity)} ${asset.symbol} @ $${String.format("%.2f", effectivePrice)}",
                            actorUid = uid
                        )
                        if (abs(remaining) < 1e-8 || remaining <= 0.0) {
                            tx.delete(holdingRef)
                        } else {
                            val oldAvg = holdingSnap.getDouble("avgBuyPriceUsd") ?: effectivePrice
                            tx.update(holdingRef, mapOf("quantity" to remaining, "avgBuyPriceUsd" to oldAvg, "updatedAt" to FieldValue.serverTimestamp()))
                        }
                    }
                }

                val tradePayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("userDisplayName", displayName)
                    if (!userEmail.isNullOrBlank()) put("userEmail", userEmail)
                    put("symbol", asset.symbol)
                    put("assetName", asset.name)
                    put("assetClass", asset.assetClass.wireValue)
                    put("countryCode", asset.countryCode)
                    put("exchange", asset.exchange)
                    put("side", side)
                    put("orderType", orderType)
                    put("quantity", quantity)
                    put("executionPriceUsd", effectivePrice)
                    if (triggerPriceUsd != null) put("triggerPriceUsd", triggerPriceUsd)
                    put("grossAmountUsd", grossAmount)
                    put("commissionBps", commissionBps)
                    put("commissionUsd", commissionUsd)
                    put("netTotalUsd", netTotalUsd)
                    put("status", orderStatus)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                tx.set(tradeRef, tradePayload)

                TradeOrder(
                    id = tradeRef.id,
                    userId = uid,
                    userDisplayName = displayName,
                    userEmail = userEmail,
                    symbol = asset.symbol,
                    assetName = asset.name,
                    assetClass = asset.assetClass.wireValue,
                    countryCode = asset.countryCode,
                    exchange = asset.exchange,
                    side = side,
                    orderType = orderType,
                    quantity = quantity,
                    executionPriceUsd = effectivePrice,
                    triggerPriceUsd = triggerPriceUsd,
                    grossAmountUsd = grossAmount,
                    commissionBps = commissionBps,
                    commissionUsd = commissionUsd,
                    netTotalUsd = netTotalUsd,
                    status = orderStatus
                )
            }.await()

            opsRepo.createNotification(
                userId = uid,
                title = "${executedOrder.side} ${executedOrder.symbol} ($orderStatus)",
                message = "${executedOrder.orderType} ${String.format("%.4f", quantity)} @ $${String.format("%.2f", effectivePrice)} • Net $${String.format("%.2f", netTotalUsd)}",
                category = "TRADE",
                severity = if (immediateFill) "SUCCESS" else "INFO",
                actionRoute = "orders"
            )
            if (!executedOrder.userEmail.isNullOrBlank()) {
                emailRepo.enqueueAndDispatchEmail(
                    userId = uid,
                    recipientEmail = executedOrder.userEmail,
                    templateKey = "TRADE_RECEIPT",
                    variables = mapOf(
                        "side" to executedOrder.side,
                        "symbol" to executedOrder.symbol,
                        "status" to orderStatus,
                        "orderType" to orderType,
                        "qty" to String.format("%.4f", quantity),
                        "price" to "$${String.format("%.2f", effectivePrice)}",
                        "gross" to "$${String.format("%.2f", grossAmount)}",
                        "fee" to "$${String.format("%.2f", commissionUsd)}",
                        "net" to "$${String.format("%.2f", netTotalUsd)}"
                    ),
                    idempotencyKey = "trade_${executedOrder.id}_$orderStatus"
                )
            }
            Result.success(executedOrder)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, tradeRef.path)
            Result.failure(e)
        }
    }

    suspend fun cancelPendingOrder(tradeId: String, reason: String = "Cancelled by user"): Result<Unit> {
        val actorUid = requireUserId()
        val tradeRef = db.collection("trades").document(tradeId)
        return try {
            var targetUid = actorUid
            var symbol = ""
            db.runTransaction { tx ->
                val tradeSnap = tx.get(tradeRef)
                if (!tradeSnap.exists()) throw IllegalStateException("Order not found.")
                if (tradeSnap.getString("status") != "PENDING") throw IllegalStateException("Only PENDING orders can be cancelled.")
                targetUid = tradeSnap.getString("userId") ?: actorUid
                symbol = tradeSnap.getString("symbol") ?: ""
                val side = tradeSnap.getString("side") ?: "BUY"
                val netTotalUsd = tradeSnap.getDouble("netTotalUsd") ?: 0.0

                val userRef = db.collection("users").document(targetUid)
                val userSnap = if (side == "BUY" && netTotalUsd > 0.0) tx.get(userRef) else null

                tx.update(
                    tradeRef,
                    mapOf(
                        "status" to "CANCELLED",
                        "cancelReason" to reason.take(200),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (userSnap != null && side == "BUY" && netTotalUsd > 0.0) {
                    val cashBefore = userSnap.getDouble("cashBalanceUsd") ?: 0.0
                    val cashAfter = cashBefore + netTotalUsd
                    tx.update(userRef, mapOf("cashBalanceUsd" to cashAfter, "updatedAt" to FieldValue.serverTimestamp()))
                    appendLedgerInTx(
                        tx = tx,
                        userId = targetUid,
                        entryType = "ORDER_CANCEL_REFUND",
                        direction = "CREDIT",
                        amountUsd = netTotalUsd,
                        balanceBeforeUsd = cashBefore,
                        balanceAfterUsd = cashAfter,
                        referenceType = "trade",
                        referenceId = tradeId,
                        description = "Refund reserved funds for cancelled $symbol order",
                        actorUid = actorUid
                    )
                }
            }.await()
            opsRepo.createNotification(
                userId = targetUid,
                title = "Order Cancelled ($symbol)",
                message = reason,
                category = "TRADE",
                severity = "WARNING",
                actionRoute = "orders"
            )
            if (actorUid != targetUid) {
                opsRepo.writeAuditLog("ADMIN_CANCELLED_ORDER", "trade", tradeId, "Cancelled pending $symbol order: $reason")
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerPendingOrderFill(order: TradeOrder, asset: MarketAsset, livePriceUsd: Double): Result<Unit> {
        val uid = requireUserId()
        val tradeRef = db.collection("trades").document(order.id)
        val userRef = db.collection("users").document(uid)
        val holdingRef = userRef.collection("holdings").document(asset.sanitizedDocId)

        return try {
            db.runTransaction { tx ->
                val tradeSnap = tx.get(tradeRef)
                if (!tradeSnap.exists() || tradeSnap.getString("status") != "PENDING") return@runTransaction
                val userSnap = tx.get(userRef)
                val holdingSnap = tx.get(holdingRef)

                val fillGross = order.quantity * livePriceUsd
                val fillFee = fillGross * (order.commissionBps / 10_000.0)
                val fillNet = if (order.side == "BUY") fillGross + fillFee else (fillGross - fillFee).coerceAtLeast(0.0)

                if (order.side == "BUY") {
                    if (holdingSnap.exists()) {
                        val oldQty = holdingSnap.getDouble("quantity") ?: 0.0
                        val oldAvg = holdingSnap.getDouble("avgBuyPriceUsd") ?: livePriceUsd
                        val totalQty = oldQty + order.quantity
                        val newAvg = ((oldQty * oldAvg) + fillGross) / totalQty
                        tx.update(holdingRef, mapOf("quantity" to totalQty, "avgBuyPriceUsd" to newAvg, "updatedAt" to FieldValue.serverTimestamp()))
                    } else {
                        tx.set(
                            holdingRef,
                            mapOf(
                                "userId" to uid,
                                "symbol" to asset.symbol,
                                "assetName" to asset.name,
                                "assetClass" to asset.assetClass.wireValue,
                                "countryCode" to asset.countryCode,
                                "exchange" to asset.exchange,
                                "quantity" to order.quantity,
                                "avgBuyPriceUsd" to livePriceUsd,
                                "createdAt" to FieldValue.serverTimestamp(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                    // Refund positive slippage if reserved > actual net
                    val refundDelta = (order.netTotalUsd - fillNet)
                    if (refundDelta > 0.01) {
                        val cashBefore = userSnap.getDouble("cashBalanceUsd") ?: 0.0
                        val cashAfter = cashBefore + refundDelta
                        tx.update(userRef, mapOf("cashBalanceUsd" to cashAfter, "updatedAt" to FieldValue.serverTimestamp()))
                        appendLedgerInTx(
                            tx, uid, "ORDER_CANCEL_REFUND", "CREDIT", refundDelta,
                            cashBefore, cashAfter, "trade", order.id,
                            "Price improvement refund on ${asset.symbol} fill", uid
                        )
                    }
                } else {
                    val oldQty = holdingSnap.getDouble("quantity") ?: 0.0
                    if (oldQty + 1e-8 < order.quantity) {
                        tx.update(tradeRef, mapOf("status" to "CANCELLED", "cancelReason" to "Insufficient holdings at trigger", "updatedAt" to FieldValue.serverTimestamp()))
                        return@runTransaction
                    }
                    val remaining = oldQty - order.quantity
                    val cashBefore = userSnap.getDouble("cashBalanceUsd") ?: 0.0
                    val cashAfter = cashBefore + fillNet
                    tx.update(userRef, mapOf("cashBalanceUsd" to cashAfter, "updatedAt" to FieldValue.serverTimestamp()))
                    appendLedgerInTx(
                        tx, uid, "TRADE_SELL_CREDIT", "CREDIT", fillNet,
                        cashBefore, cashAfter, "trade", order.id,
                        "${order.orderType} SELL triggered ${asset.symbol} @ $${String.format("%.2f", livePriceUsd)}", uid
                    )
                    if (abs(remaining) < 1e-8 || remaining <= 0.0) {
                        tx.delete(holdingRef)
                    } else {
                        val oldAvg = holdingSnap.getDouble("avgBuyPriceUsd") ?: livePriceUsd
                        tx.update(holdingRef, mapOf("quantity" to remaining, "avgBuyPriceUsd" to oldAvg, "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }

                tx.update(
                    tradeRef,
                    mapOf(
                        "status" to "FILLED",
                        "executionPriceUsd" to livePriceUsd,
                        "grossAmountUsd" to fillGross,
                        "commissionUsd" to fillFee,
                        "netTotalUsd" to fillNet,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }.await()

            opsRepo.createNotification(
                userId = uid,
                title = "Triggered Order Filled: ${order.symbol}",
                message = "${order.orderType} ${order.side} executed at $${String.format("%.2f", livePriceUsd)}",
                category = "TRADE",
                severity = "SUCCESS",
                actionRoute = "orders"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Admin Control Panel (Locked by 25046371) ---

    suspend fun unlockAdminWithPasscode(passcode: String): Result<Unit> {
        val uid = requireUserId()
        if (passcode != ADMIN_PASSCODE) {
            return Result.failure(SecurityException("Invalid Admin Passcode"))
        }
        val docRef = db.collection("admins").document(uid)
        return try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "passcode" to passcode,
                        "grantedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            opsRepo.writeAuditLog("ADMIN_SESSION_UNLOCKED", "admin", uid, "Unlocked Admin Command Center via security gate")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeAllUsers(): Flow<List<UserAccount>> = flow {
        val colRef = db.collection("users")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(UserAccount::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.cashBalanceUsd }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeAllTrades(): Flow<List<TradeOrder>> = flow {
        val colRef = db.collection("trades")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(TradeOrder::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeAllKycRequests(): Flow<List<KycSubmission>> = flow {
        val colRef = db.collection("kyc_requests")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(KycSubmission::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<KycSubmission> { if (it.status == "pending") 0 else 1 }
                                .thenByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                        )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeAllDeposits(): Flow<List<DepositRequest>> = flow {
        val colRef = db.collection("deposits")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(DepositRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<DepositRequest> { if (it.status == "pending") 0 else 1 }
                                .thenByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                        )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    fun observeAllWithdrawals(): Flow<List<WithdrawalRequest>> = flow {
        val colRef = db.collection("withdrawals")
        emitAll(
            colRef.snapshots()
                .map { snapshot ->
                    snapshot.toObjects(WithdrawalRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<WithdrawalRequest> { if (it.status == "pending") 0 else 1 }
                                .thenByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                        )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, colRef.path)
                    }
                    throw error
                }
        )
    }

    suspend fun adminReviewKyc(
        targetUserId: String,
        approve: Boolean,
        rejectionReason: String? = null
    ): Result<Unit> {
        requireUserId()
        val kycRef = db.collection("kyc_requests").document(targetUserId)
        val userRef = db.collection("users").document(targetUserId)
        val newStatus = if (approve) "approved" else "rejected"
        return try {
            var targetEmail = ""
            var targetName = "Trader"
            val reasonClean = if (approve) null else (rejectionReason?.trim()?.ifBlank { "Document did not match KYC compliance requirements." }?.take(280))
            db.runTransaction { tx ->
                val kycSnap = tx.get(kycRef)
                targetEmail = kycSnap.getString("userEmail").orEmpty()
                targetName = kycSnap.getString("fullLegalName") ?: "Trader"
                val kycUpdates = buildMap<String, Any> {
                    put("status", newStatus)
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (reasonClean != null) put("rejectionReason", reasonClean)
                    else put("rejectionReason", FieldValue.delete())
                }
                val userUpdates = buildMap<String, Any> {
                    put("kycStatus", newStatus)
                    put("updatedAt", FieldValue.serverTimestamp())
                    if (reasonClean != null) put("kycRejectionReason", reasonClean)
                    else put("kycRejectionReason", FieldValue.delete())
                }
                tx.update(kycRef, kycUpdates)
                tx.update(userRef, userUpdates)
            }.await()

            opsRepo.writeAuditLog("KYC_${newStatus.uppercase()}", "kyc_request", targetUserId, reasonClean ?: "Approved KYC compliance")
            opsRepo.createNotification(
                userId = targetUserId,
                title = if (approve) "KYC Verified" else "KYC Rejected",
                message = if (approve) "Deposits and withdrawals are now unlocked." else (reasonClean ?: "Please resubmit KYC."),
                category = "KYC",
                severity = if (approve) "SUCCESS" else "ALERT",
                actionRoute = "portfolio"
            )
            if (targetEmail.isNotBlank()) {
                emailRepo.enqueueAndDispatchEmail(
                    userId = targetUserId,
                    recipientEmail = targetEmail,
                    templateKey = if (approve) "KYC_APPROVED" else "KYC_REJECTED",
                    variables = mapOf("name" to targetName, "reason" to (reasonClean ?: "")),
                    idempotencyKey = "kyc_${newStatus}_${targetUserId}_${System.currentTimeMillis() / 10_000L}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, kycRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminReviewDeposit(
        depositId: String,
        approve: Boolean,
        adminNote: String? = null
    ): Result<Unit> {
        val adminUid = requireUserId()
        val depRef = db.collection("deposits").document(depositId)
        return try {
            var targetUid = ""
            var targetEmail = ""
            var targetName = "Trader"
            var amountUsd = 0.0
            var method = "USDT_TRC20"

            db.runTransaction { tx ->
                val depSnap = tx.get(depRef)
                if (!depSnap.exists()) throw IllegalStateException("Deposit not found")
                val status = depSnap.getString("status") ?: "pending"
                if (status != "pending") throw IllegalStateException("Deposit already processed ($status)")
                targetUid = depSnap.getString("userId") ?: throw IllegalStateException("Missing userId")
                targetEmail = depSnap.getString("userEmail").orEmpty()
                targetName = depSnap.getString("userDisplayName") ?: "Trader"
                amountUsd = depSnap.getDouble("amountUsd") ?: 0.0
                method = depSnap.getString("method") ?: "USDT_TRC20"
                val userRef = db.collection("users").document(targetUid)
                val userSnap = tx.get(userRef)

                val updates = buildMap<String, Any> {
                    put("status", if (approve) "approved" else "rejected")
                    if (!adminNote.isNullOrBlank()) put("adminNote", adminNote.trim().take(280))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                tx.update(depRef, updates)

                if (approve && userSnap.exists()) {
                    val currentCash = userSnap.getDouble("cashBalanceUsd") ?: 0.0
                    val currentTotalDep = userSnap.getDouble("totalDepositedUsd") ?: 0.0
                    val nextCash = currentCash + amountUsd
                    tx.update(
                        userRef,
                        mapOf(
                            "cashBalanceUsd" to nextCash,
                            "totalDepositedUsd" to currentTotalDep + amountUsd,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    appendLedgerInTx(
                        tx = tx,
                        userId = targetUid,
                        entryType = "DEPOSIT_CREDIT",
                        direction = "CREDIT",
                        amountUsd = amountUsd,
                        balanceBeforeUsd = currentCash,
                        balanceAfterUsd = nextCash,
                        referenceType = "deposit",
                        referenceId = depositId,
                        description = "Admin approved $method deposit proof",
                        actorUid = adminUid
                    )
                }
            }.await()

            val amtStr = "$${String.format("%.2f", amountUsd)}"
            val methodLabel = FundingMethod.fromWire(method).displayName
            opsRepo.writeAuditLog(
                action = if (approve) "DEPOSIT_APPROVED" else "DEPOSIT_REJECTED",
                targetType = "deposit",
                targetId = depositId,
                details = "$amtStr ($methodLabel) for $targetEmail"
            )
            opsRepo.createNotification(
                userId = targetUid,
                title = if (approve) "Deposit Credited ($amtStr)" else "Deposit Declined ($amtStr)",
                message = if (approve) "$amtStr credited to your cash wallet." else (adminNote ?: "Deposit proof rejected."),
                category = "DEPOSIT",
                severity = if (approve) "SUCCESS" else "ALERT",
                actionRoute = "portfolio"
            )
            if (targetEmail.isNotBlank()) {
                emailRepo.enqueueAndDispatchEmail(
                    userId = targetUid,
                    recipientEmail = targetEmail,
                    templateKey = if (approve) "DEPOSIT_APPROVED" else "DEPOSIT_REJECTED",
                    variables = mapOf("name" to targetName, "amount" to amtStr, "method" to methodLabel, "reason" to (adminNote ?: "")),
                    idempotencyKey = "dep_review_${depositId}_${if (approve) "approved" else "rejected"}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, depRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminProcessWithdrawal(
        withdrawalId: String,
        markPaid: Boolean,
        payoutReference: String,
        adminNote: String? = null,
        makerCheckerThresholdUsd: Double = 5_000.0
    ): Result<String> {
        val adminUid = requireUserId()
        val adminEmail = auth.currentUser?.email ?: "admin@apextrade"
        val wdRef = db.collection("withdrawals").document(withdrawalId)
        return try {
            // Check threshold if paying out
            val preSnap = wdRef.get().await()
            if (!preSnap.exists()) return Result.failure(IllegalStateException("Withdrawal request not found"))
            val preAmt = preSnap.getDouble("amountUsd") ?: 0.0
            val targetUid = preSnap.getString("userId") ?: ""
            val targetEmail = preSnap.getString("userEmail").orEmpty()
            val targetName = preSnap.getString("userDisplayName") ?: "Trader"
            val method = preSnap.getString("method") ?: "USDT_TRC20"

            if (markPaid && preAmt >= makerCheckerThresholdUsd) {
                val approvalRef = db.collection("pending_approvals").document("mc_wd_$withdrawalId")
                val existingMc = approvalRef.get().await()
                if (existingMc.exists() && existingMc.getString("status") == "pending_checker") {
                    return Result.success("Already queued in Maker-Checker for second Admin sign-off.")
                }
                approvalRef.set(
                    mapOf(
                        "actionType" to "LARGE_WITHDRAWAL_PAYOUT",
                        "targetUserId" to targetUid,
                        "targetReferenceId" to withdrawalId,
                        "amountUsd" to preAmt,
                        "payloadSummary" to "Manual Payout $${String.format("%.2f", preAmt)} ($method) to $targetEmail",
                        "payoutReference" to payoutReference.trim().take(180),
                        "makerUid" to adminUid,
                        "makerEmail" to adminEmail,
                        "status" to "pending_checker",
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                opsRepo.writeAuditLog(
                    "MAKER_CHECKER_QUEUED",
                    "withdrawal",
                    withdrawalId,
                    "Queued $${String.format("%.2f", preAmt)} payout for second Admin Checker"
                )
                return Result.success("Queued in Maker-Checker (>= $${String.format("%.0f", makerCheckerThresholdUsd)} requires second Admin sign-off).")
            }

            db.runTransaction { tx ->
                val wdSnap = tx.get(wdRef)
                if (!wdSnap.exists()) throw IllegalStateException("Withdrawal request not found")
                val status = wdSnap.getString("status") ?: "pending"
                if (status != "pending") throw IllegalStateException("Withdrawal already processed ($status)")
                val userRef = db.collection("users").document(targetUid)
                val userSnap = tx.get(userRef)

                val updates = buildMap<String, Any> {
                    put("status", if (markPaid) "paid" else "rejected")
                    if (payoutReference.isNotBlank()) put("payoutReference", payoutReference.trim().take(180))
                    if (!adminNote.isNullOrBlank()) put("adminNote", adminNote.trim().take(280))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                tx.update(wdRef, updates)

                if (userSnap.exists()) {
                    val currentCash = userSnap.getDouble("cashBalanceUsd") ?: 0.0
                    val currentPending = userSnap.getDouble("pendingWithdrawalUsd") ?: 0.0
                    val nextPending = (currentPending - preAmt).coerceAtLeast(0.0)
                    val nextCash = if (markPaid) currentCash else currentCash + preAmt
                    tx.update(
                        userRef,
                        mapOf(
                            "cashBalanceUsd" to nextCash,
                            "pendingWithdrawalUsd" to nextPending,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    appendLedgerInTx(
                        tx = tx,
                        userId = targetUid,
                        entryType = if (markPaid) "WITHDRAWAL_SETTLE" else "WITHDRAWAL_REFUND",
                        direction = if (markPaid) "SETTLE" else "CREDIT",
                        amountUsd = preAmt,
                        balanceBeforeUsd = currentCash,
                        balanceAfterUsd = nextCash,
                        referenceType = "withdrawal",
                        referenceId = withdrawalId,
                        description = if (markPaid) "Manual payout settled (Ref: $payoutReference)" else "Withdrawal rejected & refunded",
                        actorUid = adminUid
                    )
                }
            }.await()

            val amtStr = "$${String.format("%.2f", preAmt)}"
            val methodLabel = FundingMethod.fromWire(method).displayName
            opsRepo.writeAuditLog(
                action = if (markPaid) "WITHDRAWAL_PAID" else "WITHDRAWAL_REJECTED",
                targetType = "withdrawal",
                targetId = withdrawalId,
                details = "$amtStr ($methodLabel) Ref=$payoutReference"
            )
            opsRepo.createNotification(
                userId = targetUid,
                title = if (markPaid) "Withdrawal Paid ($amtStr)" else "Withdrawal Refunded ($amtStr)",
                message = if (markPaid) "Manual payout completed. Ref: $payoutReference" else (adminNote ?: "Refunded to cash balance."),
                category = "WITHDRAWAL",
                severity = if (markPaid) "SUCCESS" else "WARNING",
                actionRoute = "portfolio"
            )
            if (targetEmail.isNotBlank()) {
                emailRepo.enqueueAndDispatchEmail(
                    userId = targetUid,
                    recipientEmail = targetEmail,
                    templateKey = if (markPaid) "WITHDRAWAL_PAID" else "WITHDRAWAL_REJECTED",
                    variables = mapOf(
                        "name" to targetName,
                        "amount" to amtStr,
                        "method" to methodLabel,
                        "payoutRef" to payoutReference,
                        "reason" to (adminNote ?: "")
                    ),
                    idempotencyKey = "wd_done_${withdrawalId}_${if (markPaid) "paid" else "rejected"}"
                )
            }
            Result.success(if (markPaid) "Manual Payout Recorded as Paid!" else "Withdrawal Rejected & Refunded")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, wdRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminProcessMakerCheckerApproval(
        approvalId: String,
        approve: Boolean,
        rejectionReason: String? = null
    ): Result<Unit> {
        val checkerUid = requireUserId()
        val checkerEmail = auth.currentUser?.email ?: "checker@apextrade"
        val approvalRef = db.collection("pending_approvals").document(approvalId)
        return try {
            var targetUid = ""
            var refId = ""
            var amountUsd = 0.0
            var payoutRef = ""
            db.runTransaction { tx ->
                val snap = tx.get(approvalRef)
                if (!snap.exists()) throw IllegalStateException("Approval item not found.")
                if (snap.getString("status") != "pending_checker") throw IllegalStateException("Item already decided.")
                if (snap.getString("makerUid") == checkerUid) {
                    throw IllegalStateException("Dual-Control Violation: Checker must be a different Admin than Maker.")
                }
                targetUid = snap.getString("targetUserId") ?: ""
                refId = snap.getString("targetReferenceId") ?: ""
                amountUsd = snap.getDouble("amountUsd") ?: 0.0
                payoutRef = snap.getString("payoutReference") ?: "CHECKER_APPROVED"
                val actionType = snap.getString("actionType") ?: ""
                val wdRef = db.collection("withdrawals").document(refId)
                val userRef = db.collection("users").document(targetUid)
                val wdSnap = if (approve && actionType == "LARGE_WITHDRAWAL_PAYOUT" && refId.isNotBlank()) tx.get(wdRef) else null
                val userSnap = if (approve && actionType == "LARGE_WITHDRAWAL_PAYOUT" && targetUid.isNotBlank()) tx.get(userRef) else null

                val approvalUpdates = buildMap<String, Any> {
                    put("status", if (approve) "approved" else "rejected")
                    put("checkerUid", checkerUid)
                    put("checkerEmail", checkerEmail)
                    if (!rejectionReason.isNullOrBlank()) put("rejectionReason", rejectionReason.trim().take(200))
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                tx.update(approvalRef, approvalUpdates)

                if (approve && actionType == "LARGE_WITHDRAWAL_PAYOUT" && wdSnap != null) {
                    if (wdSnap.exists() && wdSnap.getString("status") == "pending") {
                        tx.update(
                            wdRef,
                            mapOf(
                                "status" to "paid",
                                "payoutReference" to payoutRef,
                                "adminNote" to "Dual-control Maker-Checker approved",
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        if (userSnap != null && userSnap.exists()) {
                            val currCash = userSnap.getDouble("cashBalanceUsd") ?: 0.0
                            val currPending = userSnap.getDouble("pendingWithdrawalUsd") ?: 0.0
                            tx.update(
                                userRef,
                                mapOf(
                                    "pendingWithdrawalUsd" to (currPending - amountUsd).coerceAtLeast(0.0),
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                            appendLedgerInTx(
                                tx, targetUid, "WITHDRAWAL_SETTLE", "SETTLE", amountUsd,
                                currCash, currCash, "withdrawal", refId,
                                "Dual-control Checker payout settled (Ref: $payoutRef)", checkerUid
                            )
                        }
                    }
                }
            }.await()
            opsRepo.writeAuditLog(
                if (approve) "MAKER_CHECKER_APPROVED" else "MAKER_CHECKER_REJECTED",
                "pending_approval",
                approvalId,
                "Amount $${String.format("%.2f", amountUsd)} • Ref $refId"
            )
            if (approve && targetUid.isNotBlank()) {
                opsRepo.createNotification(
                    userId = targetUid,
                    title = "High-Value Payout Settled",
                    message = "Dual-control verification completed ($${String.format("%.2f", amountUsd)}). Ref: $payoutRef",
                    category = "WITHDRAWAL",
                    severity = "SUCCESS",
                    actionRoute = "portfolio"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun adminUpdateUserStatusAndBalance(
        targetUserId: String,
        accountStatus: String,
        cashBalanceUsd: Double
    ): Result<Unit> {
        val adminUid = requireUserId()
        require(accountStatus in listOf("active", "suspended")) { "Invalid status" }
        require(cashBalanceUsd >= 0.0) { "Cash balance cannot be negative" }
        val docRef = db.collection("users").document(targetUserId)
        return try {
            db.runTransaction { tx ->
                val snap = tx.get(docRef)
                val oldCash = snap.getDouble("cashBalanceUsd") ?: 0.0
                val delta = cashBalanceUsd - oldCash
                tx.update(
                    docRef,
                    mapOf(
                        "accountStatus" to accountStatus,
                        "cashBalanceUsd" to cashBalanceUsd,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                if (abs(delta) > 0.001) {
                    appendLedgerInTx(
                        tx = tx,
                        userId = targetUserId,
                        entryType = "ADMIN_ADJUSTMENT",
                        direction = if (delta >= 0.0) "CREDIT" else "DEBIT",
                        amountUsd = abs(delta),
                        balanceBeforeUsd = oldCash,
                        balanceAfterUsd = cashBalanceUsd,
                        referenceType = "admin_adjustment",
                        referenceId = "adj_${System.currentTimeMillis()}",
                        description = "Admin cash balance adjustment",
                        actorUid = adminUid
                    )
                }
            }.await()
            opsRepo.writeAuditLog(
                "USER_UPDATED",
                "user",
                targetUserId,
                "status=$accountStatus, cashBalanceUsd=$${String.format("%.2f", cashBalanceUsd)}"
            )
            opsRepo.createNotification(
                userId = targetUserId,
                title = "Account Updated by Administration",
                message = "Account status: ${accountStatus.uppercase()} • Available cash: $${String.format("%.2f", cashBalanceUsd)}",
                category = "SECURITY",
                severity = if (accountStatus == "suspended") "ALERT" else "INFO",
                actionRoute = "account"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminUpdateBrokerConfig(config: BrokerConfig): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("broker_config").document("global")
        return try {
            docRef.set(
                mapOf(
                    "stockCommissionBps" to config.stockCommissionBps.coerceIn(0.0, 500.0),
                    "etfCommissionBps" to config.etfCommissionBps.coerceIn(0.0, 500.0),
                    "cryptoCommissionBps" to config.cryptoCommissionBps.coerceIn(0.0, 500.0),
                    "forexCommissionBps" to config.forexCommissionBps.coerceIn(0.0, 500.0),
                    "commodityCommissionBps" to config.commodityCommissionBps.coerceIn(0.0, 500.0),
                    "indexCommissionBps" to config.indexCommissionBps.coerceIn(0.0, 500.0),
                    "bondCommissionBps" to config.bondCommissionBps.coerceIn(0.0, 500.0),
                    "futuresCommissionBps" to config.futuresCommissionBps.coerceIn(0.0, 500.0),
                    "minOrderUsd" to config.minOrderUsd.coerceIn(1.0, 10_000.0),
                    "makerCheckerThresholdUsd" to config.makerCheckerThresholdUsd.coerceIn(100.0, 10_000_000.0),
                    "tradingHalted" to config.tradingHalted,
                    "haltedSymbols" to config.haltedSymbols.take(100),
                    "usdtTrc20Address" to config.usdtTrc20Address.trim().ifBlank { "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE" }.take(200),
                    "usdtErc20Address" to config.usdtErc20Address.trim().ifBlank { "0x742d35Cc6634C0532925a3b844Bc454e4438f44e" }.take(200),
                    "btcWalletAddress" to config.btcWalletAddress.trim().ifBlank { "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh" }.take(200),
                    "ethWalletAddress" to config.ethWalletAddress.trim().ifBlank { "0x742d35Cc6634C0532925a3b844Bc454e4438f44e" }.take(200),
                    "bankWireDetails" to config.bankWireDetails.trim().ifBlank { "ApexTrade Custody • SWIFT: APEXUS33" }.take(400),
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            opsRepo.writeAuditLog(
                "BROKER_CONFIG_UPDATED",
                "broker_config",
                "global",
                "Halted=${config.tradingHalted}, Stock=${config.stockCommissionBps}bps, Crypto=${config.cryptoCommissionBps}bps, MC=$${config.makerCheckerThresholdUsd}"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val ADMIN_PASSCODE = "25046371"
    }
}
