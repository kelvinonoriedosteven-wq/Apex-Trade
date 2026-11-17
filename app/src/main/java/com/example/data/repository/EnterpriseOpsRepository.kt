package com.example.data.repository

import android.os.Build
import com.example.data.model.AmlRiskAlert
import com.example.data.model.AppNotification
import com.example.data.model.AuditLogEntry
import com.example.data.model.LedgerEntry
import com.example.data.model.MakerCheckerApproval
import com.example.data.model.NotificationPreferences
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.StaffRoleAssignment
import com.example.data.model.SupportTicket
import com.example.data.model.UserAccount
import com.example.data.model.UserSessionRecord
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

data class LedgerReconciliationReport(
    val totalUsersChecked: Int,
    val matchedUsers: Int,
    val mismatchedUsers: List<LedgerUserDiscrepancy>
)

data class LedgerUserDiscrepancy(
    val userId: String,
    val email: String,
    val reportedCashUsd: Double,
    val ledgerComputedCashUsd: Double,
    val deltaUsd: Double
)

class EnterpriseOpsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val emailRepo: EmailDeliveryRepository = EmailDeliveryRepository(db, auth)
) {
    private fun requireUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Google Sign-In required.")

    // --- Notifications ---

    fun observeUserNotifications(userId: String): Flow<List<AppNotification>> = flow {
        val query = db.collection("notifications").whereEqualTo("userId", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(AppNotification::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { !it.isArchived }
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    suspend fun createNotification(
        userId: String,
        title: String,
        message: String,
        category: String,
        severity: String = "INFO",
        actionRoute: String? = null
    ) {
        runCatching {
            val docRef = db.collection("notifications").document()
            docRef.set(
                buildMap<String, Any> {
                    put("userId", userId)
                    put("title", title.take(140))
                    put("message", message.take(600))
                    put("category", category.take(40))
                    put("severity", severity)
                    put("isRead", false)
                    put("isArchived", false)
                    if (!actionRoute.isNullOrBlank()) put("actionRoute", actionRoute.take(60))
                    put("createdAt", FieldValue.serverTimestamp())
                }
            ).await()
        }
    }

    suspend fun markNotificationRead(notifId: String): Result<Unit> = runCatching {
        db.collection("notifications").document(notifId).update("isRead", true).await()
        Unit
    }

    suspend fun markAllNotificationsRead(userId: String): Result<Unit> = runCatching {
        val snap = db.collection("notifications")
            .whereEqualTo("userId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .await()
        val batch = db.batch()
        snap.documents.forEach { doc -> batch.update(doc.reference, "isRead", true) }
        if (!snap.isEmpty) batch.commit().await()
        Unit
    }

    suspend fun archiveNotification(notifId: String): Result<Unit> = runCatching {
        db.collection("notifications").document(notifId).update("isArchived", true).await()
        Unit
    }

    // --- Ledger & Reconciliation ---

    fun observeUserLedger(userId: String): Flow<List<LedgerEntry>> = flow {
        val query = db.collection("ledger_entries").whereEqualTo("userId", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(LedgerEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    fun observeAllLedgerEntries(): Flow<List<LedgerEntry>> = flow {
        val col = db.collection("ledger_entries")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(LedgerEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    fun reconcileAccounts(
        users: List<UserAccount>,
        ledgerEntries: List<LedgerEntry>
    ): LedgerReconciliationReport {
        val entriesByUser = ledgerEntries.groupBy { it.userId }
        val discrepancies = mutableListOf<LedgerUserDiscrepancy>()
        var matched = 0
        for (user in users) {
            val entries = entriesByUser[user.userId].orEmpty()
            if (entries.isEmpty() && user.cashBalanceUsd == 0.0) {
                matched++
                continue
            }
            val netLedger = entries.sumOf { e ->
                when (e.direction) {
                    "CREDIT" -> e.amountUsd
                    "DEBIT" -> -e.amountUsd
                    else -> 0.0
                }
            }
            val delta = user.cashBalanceUsd - netLedger
            if (abs(delta) <= 0.02) {
                matched++
            } else {
                discrepancies.add(
                    LedgerUserDiscrepancy(
                        userId = user.userId,
                        email = user.email,
                        reportedCashUsd = user.cashBalanceUsd,
                        ledgerComputedCashUsd = netLedger,
                        deltaUsd = delta
                    )
                )
            }
        }
        return LedgerReconciliationReport(
            totalUsersChecked = users.size,
            matchedUsers = matched,
            mismatchedUsers = discrepancies
        )
    }

    // --- Support Tickets ---

    fun observeUserTickets(userId: String): Flow<List<SupportTicket>> = flow {
        val query = db.collection("support_tickets").whereEqualTo("userId", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.updatedAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    fun observeAllTickets(): Flow<List<SupportTicket>> = flow {
        val col = db.collection("support_tickets")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<SupportTicket> { if (it.status == "open" || it.status == "in_progress") 0 else 1 }
                                .thenByDescending { it.updatedAt?.seconds ?: Long.MAX_VALUE }
                        )
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    suspend fun createSupportTicket(
        displayName: String,
        email: String,
        subject: String,
        category: String,
        priority: String,
        message: String
    ): Result<Unit> {
        val uid = requireUid()
        require(subject.trim().length >= 3) { "Enter a brief ticket subject." }
        require(message.trim().length >= 5) { "Describe your question or issue." }
        val docRef = db.collection("support_tickets").document()
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "userDisplayName" to displayName.ifBlank { "Global Trader" }.take(100),
                    "userEmail" to email.ifBlank { "$uid@apextrade.user" }.take(200),
                    "subject" to subject.trim().take(140),
                    "category" to category,
                    "priority" to priority,
                    "status" to "open",
                    "message" to message.trim().take(2000),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            createNotification(
                userId = uid,
                title = "Support Ticket Opened",
                message = "Ticket \"${subject.trim().take(60)}\" received. Team response within 24 hours.",
                category = "SUPPORT",
                severity = "INFO",
                actionRoute = "account"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminReplySupportTicket(
        ticket: SupportTicket,
        status: String,
        reply: String
    ): Result<Unit> {
        val uid = requireUid()
        require(status in listOf("open", "in_progress", "resolved", "closed")) { "Invalid ticket status" }
        val docRef = db.collection("support_tickets").document(ticket.id)
        return try {
            val cleanReply = reply.trim().take(2000)
            docRef.update(
                mapOf(
                    "status" to status,
                    "adminReply" to cleanReply,
                    "repliedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                action = "SUPPORT_TICKET_UPDATED",
                targetType = "support_ticket",
                targetId = ticket.id,
                details = "Set status=$status for ${ticket.userEmail}"
            )
            createNotification(
                userId = ticket.userId,
                title = "Support Reply: ${ticket.subject.take(40)}",
                message = cleanReply.ifBlank { "Ticket status updated to $status." },
                category = "SUPPORT",
                severity = if (status == "resolved") "SUCCESS" else "INFO",
                actionRoute = "account"
            )
            if (cleanReply.isNotBlank()) {
                emailRepo.enqueueAndDispatchEmail(
                    userId = ticket.userId,
                    recipientEmail = ticket.userEmail,
                    templateKey = "SUPPORT_REPLY",
                    variables = mapOf(
                        "name" to ticket.userDisplayName,
                        "subject" to ticket.subject,
                        "reply" to cleanReply
                    ),
                    idempotencyKey = "ticket_reply_${ticket.id}_${cleanReply.hashCode()}"
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Announcements ---

    fun observeActiveAnnouncements(): Flow<List<PlatformAnnouncement>> = flow {
        val col = db.collection("announcements")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(PlatformAnnouncement::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .filter { it.isActive }
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    suspend fun adminPublishAnnouncement(
        title: String,
        message: String,
        severity: String,
        targetAudience: String
    ): Result<Unit> {
        val uid = requireUid()
        require(title.trim().length >= 3 && message.trim().length >= 5) { "Title and message are required." }
        val docRef = db.collection("announcements").document()
        return try {
            docRef.set(
                mapOf(
                    "title" to title.trim().take(140),
                    "message" to message.trim().take(800),
                    "severity" to severity,
                    "targetAudience" to targetAudience,
                    "isActive" to true,
                    "createdByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog(
                action = "ANNOUNCEMENT_PUBLISHED",
                targetType = "announcement",
                targetId = docRef.id,
                details = "${title.trim()} [$severity / $targetAudience]"
            )
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun adminDeactivateAnnouncement(announcementId: String): Result<Unit> = runCatching {
        db.collection("announcements").document(announcementId).update(
            mapOf("isActive" to false, "updatedAt" to FieldValue.serverTimestamp())
        ).await()
        Unit
    }

    // --- AML Risk Alerts, Maker-Checker Approvals, Audit Logs, Staff Roles ---

    fun observeAllAmlAlerts(): Flow<List<AmlRiskAlert>> = flow {
        val col = db.collection("aml_alerts")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(AmlRiskAlert::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<AmlRiskAlert> { if (it.status == "open" || it.status == "escalated") 0 else 1 }
                                .thenByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                        )
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    suspend fun createAmlAlert(
        userId: String,
        userEmail: String,
        alertType: String,
        severity: String,
        amountUsd: Double,
        referenceId: String,
        summary: String
    ) {
        runCatching {
            val docRef = db.collection("aml_alerts").document()
            docRef.set(
                mapOf(
                    "userId" to userId,
                    "userEmail" to userEmail,
                    "alertType" to alertType,
                    "severity" to severity,
                    "amountUsd" to amountUsd,
                    "referenceId" to referenceId,
                    "summary" to summary.take(400),
                    "status" to "open",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    suspend fun adminResolveAmlAlert(alertId: String, status: String, note: String): Result<Unit> {
        val uid = requireUid()
        return runCatching {
            db.collection("aml_alerts").document(alertId).update(
                mapOf(
                    "status" to status,
                    "resolutionNote" to note.trim().take(300),
                    "resolvedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog("AML_ALERT_UPDATED", "aml_alert", alertId, "Status -> $status ($note)")
            Unit
        }
    }

    fun observePendingApprovals(): Flow<List<MakerCheckerApproval>> = flow {
        val col = db.collection("pending_approvals")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(MakerCheckerApproval::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareBy<MakerCheckerApproval> { if (it.status == "pending_checker") 0 else 1 }
                                .thenByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                        )
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    fun observeAuditLogs(): Flow<List<AuditLogEntry>> = flow {
        val col = db.collection("audit_logs")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(AuditLogEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    suspend fun writeAuditLog(action: String, targetType: String, targetId: String, details: String) {
        val user = auth.currentUser ?: return
        runCatching {
            db.collection("audit_logs").document().set(
                mapOf(
                    "actorUid" to user.uid,
                    "actorEmail" to (user.email ?: "admin@apextrade"),
                    "action" to action.take(80),
                    "targetType" to targetType.take(60),
                    "targetId" to targetId.take(128),
                    "details" to details.take(400),
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
    }

    fun observeStaffRoles(): Flow<List<StaffRoleAssignment>> = flow {
        val col = db.collection("staff_roles")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(StaffRoleAssignment::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    suspend fun adminAssignStaffRole(user: UserAccount, role: String): Result<Unit> {
        val uid = requireUid()
        return runCatching {
            db.collection("staff_roles").document(user.userId).set(
                mapOf(
                    "userId" to user.userId,
                    "email" to user.email,
                    "displayName" to user.displayName,
                    "role" to role,
                    "assignedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            writeAuditLog("STAFF_ROLE_ASSIGNED", "user", user.userId, "Assigned $role to ${user.email}")
            Unit
        }
    }

    // --- Sessions & Notification Preferences ---

    fun observeUserSessions(userId: String): Flow<List<UserSessionRecord>> = flow {
        val query = db.collection("user_sessions").whereEqualTo("userId", userId)
        emitAll(
            query.snapshots()
                .map { snap ->
                    snap.toObjects(UserSessionRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.lastActiveAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    suspend fun registerDeviceSession(sessionId: String) {
        val uid = auth.currentUser?.uid ?: return
        val deviceLabel = "${Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }} ${Build.MODEL.orEmpty()} (Android ${Build.VERSION.RELEASE})".trim()
        runCatching {
            val docRef = db.collection("user_sessions").document(sessionId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "deviceName" to deviceLabel.take(100),
                        "lastActiveAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "deviceName" to deviceLabel.take(100),
                        "platform" to "Android",
                        "appVersion" to "2.0 Enterprise",
                        "status" to "active",
                        "lastActiveAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
    }

    suspend fun revokeDeviceSession(sessionId: String): Result<Unit> = runCatching {
        db.collection("user_sessions").document(sessionId).update(
            mapOf("status" to "revoked", "lastActiveAt" to FieldValue.serverTimestamp())
        ).await()
        Unit
    }

    fun observeNotificationPreferences(userId: String): Flow<NotificationPreferences> = flow {
        val docRef = db.collection("notification_preferences").document(userId)
        emitAll(
            docRef.snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(NotificationPreferences::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?: NotificationPreferences(id = userId, userId = userId)
                    } else {
                        NotificationPreferences(id = userId, userId = userId)
                    }
                }
                .catch { error ->
                    val fs = generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fs == null && error is CancellationException) throw error
                    emit(NotificationPreferences(id = userId, userId = userId))
                }
        )
    }

    suspend fun updateNotificationPreferences(prefs: NotificationPreferences): Result<Unit> {
        val uid = requireUid()
        return runCatching {
            db.collection("notification_preferences").document(uid).set(
                mapOf(
                    "userId" to uid,
                    "inAppEnabled" to prefs.inAppEnabled,
                    "emailSecurityEnabled" to prefs.emailSecurityEnabled,
                    "emailFundingEnabled" to prefs.emailFundingEnabled,
                    "emailTradeReceiptsEnabled" to prefs.emailTradeReceiptsEnabled,
                    "emailAnnouncementsEnabled" to prefs.emailAnnouncementsEnabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Unit
        }
    }
}
