package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.BrokerConfig
import com.example.data.model.FundingMethod
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BrokerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun fullKycDepositTradeWithdrawalAdminFlow_succeedsEndToEnd() = runBlocking {
        val traderEmail = "trader_${UUID.randomUUID()}@test.com"
        val traderUid = signInTestUser(traderEmail)
        val traderRepo = BrokerRepository(firestore, auth)

        // 1. Account initializes at $0.0 and not_submitted KYC
        val profileRes = withTimeout(TIMEOUT_MS) {
            traderRepo.ensureUserProfile("Test Trader", traderEmail, null)
        }
        assertTrue(profileRes.isSuccess)
        val created = profileRes.getOrThrow()
        assertEquals(0.0, created.cashBalanceUsd, 0.01)
        assertEquals("not_submitted", created.kycStatus)

        // 2. Deposit blocked before KYC approved
        val preKycDepId = "dep_${UUID.randomUUID().toString().replace("-", "_")}"
        val blockedDep = withTimeout(TIMEOUT_MS) {
            traderRepo.submitDepositRequest(
                amountUsd = 5000.0,
                method = FundingMethod.USDT_TRC20,
                walletAddressUsed = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE",
                txHash = "0xprekyc1234",
                proofImageBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
                depositIdOverride = preKycDepId
            )
        }
        assertTrue("Deposit should fail before KYC approval", blockedDep.isFailure)

        // 3. Submit KYC -> starts in pending status
        val kycRes = withTimeout(TIMEOUT_MS) {
            traderRepo.submitKyc(
                userEmail = traderEmail,
                fullLegalName = "Test Trader",
                country = "United States",
                dateOfBirth = "1995-03-21",
                idType = "Passport",
                idNumber = "P1234567",
                docImageBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRg=="
            )
        }
        assertTrue("KYC submit failed: ${kycRes.exceptionOrNull()}", kycRes.isSuccess)
        val pendingKyc = withTimeout(FLOW_TIMEOUT_MS) {
            traderRepo.observeUserKyc(traderUid).first { it != null }
        }!!
        assertEquals("pending", pendingKyc.status)

        // 4. Admin unlocks with 25046371 and approves KYC
        val adminEmail = "admin_${UUID.randomUUID()}@test.com"
        signInTestUser(adminEmail)
        val adminRepo = BrokerRepository(firestore, auth)
        val unlockRes = withTimeout(TIMEOUT_MS) { adminRepo.unlockAdminWithPasscode("25046371") }
        assertTrue(unlockRes.isSuccess)
        val approveKycRes = withTimeout(TIMEOUT_MS) { adminRepo.adminReviewKyc(traderUid, approve = true) }
        assertTrue("Admin KYC approve failed: ${approveKycRes.exceptionOrNull()}", approveKycRes.isSuccess)

        // 5. Switch back to Trader -> submit pending Deposit with proof
        signInTestUser(traderEmail)
        val depId = "dep_${UUID.randomUUID().toString().replace("-", "_")}"
        val depRes = withTimeout(TIMEOUT_MS) {
            traderRepo.submitDepositRequest(
                amountUsd = 5000.0,
                method = FundingMethod.USDT_TRC20,
                walletAddressUsed = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE",
                txHash = "0xverifiedhash9988",
                proofImageBase64 = "data:image/jpeg;base64,/9j/4AAQSkZJRg==",
                depositIdOverride = depId
            )
        }
        assertTrue("Deposit submit failed: ${depRes.exceptionOrNull()}", depRes.isSuccess)
        val pendingDeposits = withTimeout(FLOW_TIMEOUT_MS) {
            traderRepo.observeUserDeposits(traderUid).first { list -> list.any { it.id == depId } }
        }
        assertEquals("pending", pendingDeposits.first { it.id == depId }.status)

        // 6. Admin approves deposit -> user balance becomes $5,000
        signInTestUser(adminEmail)
        val approveDepRes = withTimeout(TIMEOUT_MS) {
            adminRepo.adminReviewDeposit(depId, approve = true, adminNote = "Verified on TRON")
        }
        assertTrue("Admin deposit approve failed: ${approveDepRes.exceptionOrNull()}", approveDepRes.isSuccess)

        // 7. Trader executes BUY trade
        signInTestUser(traderEmail)
        val fundedAccount = withTimeout(FLOW_TIMEOUT_MS) {
            traderRepo.observeUserAccount(traderUid).first { (it?.cashBalanceUsd ?: 0.0) >= 5000.0 }
        }!!
        assertEquals(5000.0, fundedAccount.cashBalanceUsd, 0.01)

        val asset = GlobalMarketCatalog.initialAssets.first { it.symbol == "AAPL" }
        val tradeRes = withTimeout(TIMEOUT_MS) {
            traderRepo.executeTrade(
                asset = asset,
                side = "BUY",
                orderType = "MARKET",
                quantity = 2.0,
                executionPriceUsd = 225.0,
                brokerConfig = BrokerConfig(),
                tradeIdOverride = "trade_${UUID.randomUUID().toString().replace("-", "_")}"
            )
        }
        assertTrue("Expected trade success, got: ${tradeRes.exceptionOrNull()}", tradeRes.isSuccess)
        val holdings = withTimeout(FLOW_TIMEOUT_MS) {
            traderRepo.observeHoldings(traderUid).first { it.isNotEmpty() }
        }
        assertTrue(holdings.any { it.symbol == "AAPL" && it.quantity == 2.0 })

        // 8. Trader submits pending Withdrawal -> reserves cash into pendingWithdrawalUsd
        val wdId = "wd_${UUID.randomUUID().toString().replace("-", "_")}"
        val wdRes = withTimeout(TIMEOUT_MS) {
            traderRepo.submitWithdrawalRequest(
                amountUsd = 1000.0,
                method = FundingMethod.BTC,
                destinationDetails = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
                accountHolderName = "Test Trader",
                withdrawalIdOverride = wdId
            )
        }
        assertTrue("Withdrawal submit failed: ${wdRes.exceptionOrNull()}", wdRes.isSuccess)
        val wdPendingAcc = withTimeout(FLOW_TIMEOUT_MS) {
            traderRepo.observeUserAccount(traderUid).first { (it?.pendingWithdrawalUsd ?: 0.0) >= 1000.0 }
        }!!
        assertEquals(1000.0, wdPendingAcc.pendingWithdrawalUsd, 0.01)

        // 9. Admin marks withdrawal as paid manually
        signInTestUser(adminEmail)
        val payRes = withTimeout(TIMEOUT_MS) {
            adminRepo.adminProcessWithdrawal(
                withdrawalId = wdId,
                markPaid = true,
                payoutReference = "btc_tx_manual_778899",
                adminNote = "Paid manually"
            )
        }
        assertTrue("Admin manual payout failed: ${payRes.exceptionOrNull()}", payRes.isSuccess)

        signInTestUser(traderEmail)
        val paidWdList = withTimeout(FLOW_TIMEOUT_MS) {
            traderRepo.observeUserWithdrawals(traderUid).first { list -> list.any { it.id == wdId && it.status == "paid" } }
        }
        assertEquals("btc_tx_manual_778899", paidWdList.first { it.id == wdId }.payoutReference)

        // 10. Double-entry ledger reconciles user cash balance accurately (0 discrepancy)
        val opsRepo = EnterpriseOpsRepository(firestore, auth)
        val userLedger = withTimeout(FLOW_TIMEOUT_MS) { opsRepo.observeUserLedger(traderUid).first { it.isNotEmpty() } }
        val finalAccount = withTimeout(FLOW_TIMEOUT_MS) { traderRepo.observeUserAccount(traderUid).first { it != null } }!!
        val report = opsRepo.reconcileAccounts(listOf(finalAccount), userLedger)
        assertEquals("Expected zero ledger reconciliation discrepancies: ${report.mismatchedUsers}", 0, report.mismatchedUsers.size)
    }

    @Test
    fun highValueWithdrawal_queuesInMakerChecker_andEnforcesDifferentChecker() = runBlocking {
        val traderEmail = "whale_${UUID.randomUUID()}@test.com"
        val traderUid = signInTestUser(traderEmail)
        val traderRepo = BrokerRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) { traderRepo.ensureUserProfile("Whale Trader", traderEmail, null) }
        val kycRes = withTimeout(TIMEOUT_MS) {
            traderRepo.submitKyc(
                traderEmail,
                "Whale Trader",
                "US",
                "1990-01-01",
                "Passport",
                "P998877",
                "data:image/jpeg;base64,/9j/4AAQSkZJRg=="
            )
        }
        assertTrue("KYC submit failed: ${kycRes.exceptionOrNull()}", kycRes.isSuccess)

        val makerEmail = "maker_${UUID.randomUUID()}@test.com"
        signInTestUser(makerEmail)
        val makerRepo = BrokerRepository(firestore, auth)
        assertTrue(withTimeout(TIMEOUT_MS) { makerRepo.unlockAdminWithPasscode("25046371") }.isSuccess)
        assertTrue(withTimeout(TIMEOUT_MS) { makerRepo.adminReviewKyc(traderUid, true) }.isSuccess)
        assertTrue(withTimeout(TIMEOUT_MS) { makerRepo.adminUpdateUserStatusAndBalance(traderUid, "active", 12_000.0) }.isSuccess)

        signInTestUser(traderEmail)
        val wdId = "wd_high_${UUID.randomUUID().toString().replace("-", "_")}"
        val wdRes = withTimeout(TIMEOUT_MS) {
            traderRepo.submitWithdrawalRequest(6_000.0, FundingMethod.BANK_WIRE, "SWIFT APEXUS33", "Whale Trader", wdId)
        }
        assertTrue("High-value withdrawal submit failed: ${wdRes.exceptionOrNull()}", wdRes.isSuccess)

        // Admin Maker tries to pay $6,000 (>= $5,000 threshold) -> queued in Maker-Checker
        signInTestUser(makerEmail)
        val queueRes = withTimeout(TIMEOUT_MS) {
            makerRepo.adminProcessWithdrawal(wdId, true, "WIRE_REF_1", "Approve wire", makerCheckerThresholdUsd = 5_000.0)
        }
        assertTrue("Queueing failed: ${queueRes.exceptionOrNull()}", queueRes.isSuccess)
        assertTrue(queueRes.getOrThrow().contains("Maker-Checker"))

        // Same Maker trying to self-approve must fail dual control
        val selfCheckRes = withTimeout(TIMEOUT_MS) {
            makerRepo.adminProcessMakerCheckerApproval("mc_wd_$wdId", true)
        }
        assertTrue("Same Maker must be blocked from acting as Checker", selfCheckRes.isFailure)

        // Second Admin Checker succeeds and settles payout
        val checkerEmail = "checker_${UUID.randomUUID()}@test.com"
        signInTestUser(checkerEmail)
        val checkerRepo = BrokerRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) { checkerRepo.unlockAdminWithPasscode("25046371") }
        val checkerApproveRes = withTimeout(TIMEOUT_MS) {
            checkerRepo.adminProcessMakerCheckerApproval("mc_wd_$wdId", true)
        }
        assertTrue("Different Checker approval failed: ${checkerApproveRes.exceptionOrNull()}", checkerApproveRes.isSuccess)
    }

    @Test
    fun pendingLimitOrder_reservesAndCancelsWithFullRefund() = runBlocking {
        val traderEmail = "limit_${UUID.randomUUID()}@test.com"
        val traderUid = signInTestUser(traderEmail)
        val repo = BrokerRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) { repo.ensureUserProfile("Limit Trader", traderEmail, null) }

        signInTestUser("admin_${UUID.randomUUID()}@test.com")
        val adminRepo = BrokerRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) { adminRepo.unlockAdminWithPasscode("25046371") }
        withTimeout(TIMEOUT_MS) { adminRepo.adminUpdateUserStatusAndBalance(traderUid, "active", 2_000.0) }

        signInTestUser(traderEmail)
        val asset = GlobalMarketCatalog.initialAssets.first { it.symbol == "AAPL" }
        val farBelowPrice = (asset.currentPriceUsd * 0.5).coerceAtLeast(10.0)
        val orderRes = withTimeout(TIMEOUT_MS) {
            repo.executeTrade(
                asset = asset,
                side = "BUY",
                orderType = "LIMIT",
                quantity = 2.0,
                executionPriceUsd = farBelowPrice,
                brokerConfig = BrokerConfig(),
                tradeIdOverride = "ord_lim_${UUID.randomUUID().toString().replace("-", "_")}"
            )
        }
        assertTrue(orderRes.isSuccess)
        val order = orderRes.getOrThrow()
        assertEquals("PENDING", order.status)

        val cancelRes = withTimeout(TIMEOUT_MS) { repo.cancelPendingOrder(order.id, "Test cancel") }
        assertTrue("Pending order cancel failed: ${cancelRes.exceptionOrNull()}", cancelRes.isSuccess)

        val accountAfter = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserAccount(traderUid).first { it != null && it.cashBalanceUsd >= 1999.9 }
        }!!
        assertEquals(2_000.0, accountAfter.cashBalanceUsd, 0.01)
    }

    @Test
    fun observeUserTrades_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = BrokerRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserTrades("some_uid").first()
            }
        } catch (e: Exception) {
            caught = e
        }

        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    @Test
    fun adminUnlock_wrongPasscodeFails_exactPasscode25046371UpdatesWalletsAndCommissions() = runBlocking {
        signInTestUser("admin_${UUID.randomUUID()}@test.com")
        val repo = BrokerRepository(firestore, auth)

        val badUnlock = withTimeout(TIMEOUT_MS) { repo.unlockAdminWithPasscode("00000000") }
        assertTrue(badUnlock.isFailure)

        val goodUnlock = withTimeout(TIMEOUT_MS) { repo.unlockAdminWithPasscode("25046371") }
        assertTrue("Admin unlock failed: ${goodUnlock.exceptionOrNull()}", goodUnlock.isSuccess)

        val configRes = withTimeout(TIMEOUT_MS) {
            repo.adminUpdateBrokerConfig(
                BrokerConfig(
                    stockCommissionBps = 15.0,
                    cryptoCommissionBps = 30.0,
                    usdtTrc20Address = "TNewAdminWalletAddress998877"
                )
            )
        }
        assertTrue("Config update failed: ${configRes.exceptionOrNull()}", configRes.isSuccess)

        val liveConfig = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeBrokerConfig().first { it.stockCommissionBps == 15.0 }
        }
        assertEquals(30.0, liveConfig.cryptoCommissionBps, 0.01)
        assertEquals("TNewAdminWalletAddress998877", liveConfig.usdtTrc20Address)
    }

    @Test
    fun crossUserIsolation_cannotReadAnotherTradersHoldings() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val aliceRepo = BrokerRepository(firestore, auth)
        withTimeout(TIMEOUT_MS) {
            aliceRepo.ensureUserProfile("Alice", "alice@test.com", null)
        }

        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = BrokerRepository(firestore, auth)

        var caught: Exception? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeHoldings(aliceUid).first()
            }
        } catch (e: Exception) {
            caught = e
        }

        val firestoreEx = checkNotNull(caught?.unwrapFirestoreException()) {
            "Expected PERMISSION_DENIED on cross-user holdings read, got $caught"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
    }

    private companion object {
        const val TIMEOUT_MS = 6000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
