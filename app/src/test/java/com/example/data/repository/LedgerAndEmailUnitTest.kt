package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.LedgerEntry
import com.example.data.model.UserAccount
import com.example.data.network.ResendEmailApi
import com.example.data.network.ResendEmailPayload
import com.example.data.network.ResendEmailResponse
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.util.UUID

class LedgerAndEmailUnitTest : FirestoreEmulatorTestBase() {

    @Test
    fun ledgerReconciliation_handlesCreditDebitSettleAndDetectsMismatch() {
        val ops = EnterpriseOpsRepository(firestore, auth)
        val matchedUser = UserAccount(userId = "u1", email = "u1@test.com", cashBalanceUsd = 3500.0)
        val badUser = UserAccount(userId = "u2", email = "u2@test.com", cashBalanceUsd = 999.0)

        val entries = listOf(
            LedgerEntry(userId = "u1", direction = "CREDIT", amountUsd = 5000.0),
            LedgerEntry(userId = "u1", direction = "DEBIT", amountUsd = 1000.0),
            LedgerEntry(userId = "u1", direction = "DEBIT", amountUsd = 500.0),
            LedgerEntry(userId = "u1", direction = "SETTLE", amountUsd = 500.0),
            LedgerEntry(userId = "u2", direction = "CREDIT", amountUsd = 500.0)
        )

        val report = ops.reconcileAccounts(listOf(matchedUser, badUser), entries)
        assertEquals(2, report.totalUsersChecked)
        assertEquals(1, report.matchedUsers)
        assertEquals(1, report.mismatchedUsers.size)
        assertEquals("u2", report.mismatchedUsers.first().userId)
        assertEquals(499.0, report.mismatchedUsers.first().deltaUsd, 0.01)
    }

    @Test
    fun emailDeliveryRepository_queuesHonestlyWithoutKey_andSendsWithValidKey() = runBlocking {
        val uid = signInTestUser("email_${UUID.randomUUID()}@test.com")

        // 1. Placeholder key -> must report unconfigured & queue as awaiting_provider_config
        val unconfiguredRepo = EmailDeliveryRepository(
            db = firestore,
            auth = auth,
            apiKeyProvider = { "MY_RESEND_API_KEY" },
            fromEmailProvider = { "onboarding@resend.dev" }
        )
        assertFalse(unconfiguredRepo.getProviderStatus().isConfigured)
        val queuedJob = withTimeout(5000L) {
            unconfiguredRepo.enqueueAndDispatchEmail(
                userId = uid,
                recipientEmail = "client@example.com",
                templateKey = "KYC_APPROVED",
                variables = mapOf("name" to "Client", "reason" to ""),
                idempotencyKey = "idem_unconf_${UUID.randomUUID()}"
            )
        }.getOrThrow()
        assertEquals("awaiting_provider_config", queuedJob.status)

        // 2. Real API key + mock HTTP 200 Resend endpoint -> delivers and records providerMessageId
        val fakeApi = object : ResendEmailApi {
            var calls = 0
            override suspend fun sendEmail(
                bearerToken: String,
                idempotencyKey: String,
                payload: ResendEmailPayload
            ): Response<ResendEmailResponse> {
                calls++
                return Response.success(ResendEmailResponse(id = "re_msg_998877"))
            }
        }
        val liveRepo = EmailDeliveryRepository(
            db = firestore,
            auth = auth,
            resendApi = fakeApi,
            apiKeyProvider = { "re_test_valid_secret_key" },
            fromEmailProvider = { "onboarding@resend.dev" }
        )
        assertTrue(liveRepo.getProviderStatus().isConfigured)
        val sameIdemKey = "idem_live_${UUID.randomUUID()}"
        val sentJob = withTimeout(5000L) {
            liveRepo.enqueueAndDispatchEmail(
                userId = uid,
                recipientEmail = "client@example.com",
                templateKey = "KYC_APPROVED",
                variables = mapOf("name" to "Client", "reason" to ""),
                idempotencyKey = sameIdemKey
            )
        }.getOrThrow()
        assertEquals("sent", sentJob.status)
        assertEquals("re_msg_998877", sentJob.providerMessageId)
        assertEquals(1, fakeApi.calls)

        // 3. Re-invoking with same idempotencyKey must NOT call Resend again
        val dedupedJob = withTimeout(5000L) {
            liveRepo.enqueueAndDispatchEmail(
                userId = uid,
                recipientEmail = "client@example.com",
                templateKey = "KYC_APPROVED",
                variables = mapOf("name" to "Client", "reason" to ""),
                idempotencyKey = sameIdemKey
            )
        }.getOrThrow()
        assertEquals("sent", dedupedJob.status)
        assertEquals(1, fakeApi.calls)
    }
}
