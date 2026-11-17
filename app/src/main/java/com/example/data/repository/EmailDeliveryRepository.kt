package com.example.data.repository

import com.example.BuildConfig
import com.example.data.model.EmailJob
import com.example.data.model.EmailSuppression
import com.example.data.model.EmailTemplate
import com.example.data.network.ResendEmailApi
import com.example.data.network.ResendEmailPayload
import com.example.data.network.ResendNetworkClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

data class EmailProviderStatus(
    val isConfigured: Boolean,
    val providerName: String,
    val fromAddress: String,
    val statusDetail: String
)

class EmailDeliveryRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val resendApi: ResendEmailApi = ResendNetworkClient.api,
    private val apiKeyProvider: () -> String = { BuildConfig.RESEND_API_KEY },
    private val fromEmailProvider: () -> String = { BuildConfig.RESEND_FROM_EMAIL }
) {
    fun getProviderStatus(): EmailProviderStatus {
        val key = apiKeyProvider().trim()
        val from = fromEmailProvider().trim().ifBlank { "onboarding@resend.dev" }
        val configured = key.isNotBlank() && key != "MY_RESEND_API_KEY"
        return EmailProviderStatus(
            isConfigured = configured,
            providerName = "Resend REST API",
            fromAddress = from,
            statusDetail = if (configured) {
                "Configured & Ready ($from)"
            } else {
                "Awaiting RESEND_API_KEY in AI Studio Secrets. Jobs queue honestly without faking delivery."
            }
        )
    }

    fun observeEmailJobs(): Flow<List<EmailJob>> = flow {
        val col = db.collection("email_jobs")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(EmailJob::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    fun observeEmailTemplates(): Flow<List<EmailTemplate>> = flow {
        val col = db.collection("email_templates")
        emitAll(
            col.snapshots()
                .map { snap ->
                    val stored = snap.toObjects(EmailTemplate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .associateBy { it.templateKey }
                    defaultTemplates().map { def -> stored[def.templateKey] ?: def }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(defaultTemplates())
                }
        )
    }

    fun observeEmailSuppressions(): Flow<List<EmailSuppression>> = flow {
        val col = db.collection("email_suppressions")
        emitAll(
            col.snapshots()
                .map { snap ->
                    snap.toObjects(EmailSuppression::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    emit(emptyList())
                }
        )
    }

    suspend fun saveEmailTemplate(template: EmailTemplate): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(SecurityException("Sign in required"))
        val docRef = db.collection("email_templates").document(template.templateKey)
        return try {
            docRef.set(
                mapOf(
                    "templateKey" to template.templateKey,
                    "name" to template.name.trim().take(120),
                    "subject" to template.subject.trim().take(200),
                    "htmlBody" to template.htmlBody.trim().take(8000),
                    "textBody" to template.textBody.trim().take(4000),
                    "isEnabled" to template.isEnabled,
                    "updatedByUid" to uid,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addSuppression(email: String, reason: String): Result<Unit> {
        val uid = auth.currentUser?.uid ?: return Result.failure(SecurityException("Sign in required"))
        val normalized = email.trim().lowercase()
        if (normalized.length < 3) return Result.failure(IllegalArgumentException("Invalid email"))
        val docId = emailKeyHash(normalized)
        val docRef = db.collection("email_suppressions").document(docId)
        return try {
            docRef.set(
                mapOf(
                    "email" to normalized,
                    "reason" to reason,
                    "addedByUid" to uid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeSuppression(suppressionId: String): Result<Unit> {
        return try {
            db.collection("email_suppressions").document(suppressionId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enqueueAndDispatchEmail(
        userId: String,
        recipientEmail: String,
        templateKey: String,
        variables: Map<String, String>,
        idempotencyKey: String
    ): Result<EmailJob> {
        val cleanEmail = recipientEmail.trim().lowercase()
        if (!cleanEmail.contains("@") || cleanEmail.endsWith(".local")) {
            return Result.failure(IllegalArgumentException("No valid deliverable email address."))
        }
        val safeJobId = sanitizeId(idempotencyKey)
        val jobRef = db.collection("email_jobs").document(safeJobId)

        return try {
            // Check idempotency so duplicate events do not double-send
            val existingJob = runCatching { jobRef.get().await() }.getOrNull()
            if (existingJob != null && existingJob.exists()) {
                val existing = existingJob.toObject(EmailJob::class.java)
                if (existing != null && existing.status == "sent") {
                    return Result.success(existing)
                }
            }

            // Check suppression list & user preferences
            val supRef = db.collection("email_suppressions").document(emailKeyHash(cleanEmail))
            val isSuppressed = runCatching { supRef.get().await().exists() }.getOrDefault(false)
            val prefSnap = runCatching { db.collection("notification_preferences").document(userId).get().await() }.getOrNull()
            val prefEnabled = when (templateKey) {
                "SECURITY_ALERT" -> prefSnap?.getBoolean("emailSecurityEnabled") ?: true
                "TRADE_RECEIPT" -> prefSnap?.getBoolean("emailTradeReceiptsEnabled") ?: true
                "ANNOUNCEMENT" -> prefSnap?.getBoolean("emailAnnouncementsEnabled") ?: true
                else -> prefSnap?.getBoolean("emailFundingEnabled") ?: true
            }

            val tpl = resolveTemplate(templateKey)
            if (!tpl.isEnabled) {
                return Result.failure(IllegalStateException("Template $templateKey is disabled by Admin."))
            }

            val subject = renderVars(tpl.subject, variables)
            val html = renderVars(tpl.htmlBody, variables)
            val text = renderVars(tpl.textBody, variables)

            if (isSuppressed || !prefEnabled) {
                val reason = if (isSuppressed) "Address on suppression/bounce list" else "User opted out in preferences"
                val suppressedPayload = mapOf(
                    "idempotencyKey" to idempotencyKey,
                    "userId" to userId,
                    "recipientEmail" to cleanEmail,
                    "templateKey" to templateKey,
                    "subject" to subject,
                    "htmlBody" to html,
                    "textBody" to text,
                    "status" to "suppressed",
                    "provider" to "resend",
                    "attempts" to 0,
                    "lastError" to reason,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                jobRef.set(suppressedPayload).await()
                return Result.success(
                    EmailJob(
                        id = safeJobId,
                        idempotencyKey = idempotencyKey,
                        userId = userId,
                        recipientEmail = cleanEmail,
                        templateKey = templateKey,
                        subject = subject,
                        htmlBody = html,
                        textBody = text,
                        status = "suppressed",
                        lastError = reason
                    )
                )
            }

            val status = getProviderStatus()
            if (!status.isConfigured) {
                val queuedPayload = mapOf(
                    "idempotencyKey" to idempotencyKey,
                    "userId" to userId,
                    "recipientEmail" to cleanEmail,
                    "templateKey" to templateKey,
                    "subject" to subject,
                    "htmlBody" to html,
                    "textBody" to text,
                    "status" to "awaiting_provider_config",
                    "provider" to "resend",
                    "attempts" to 0,
                    "lastError" to "RESEND_API_KEY not configured in AI Studio Secrets; email remains queued.",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                jobRef.set(queuedPayload).await()
                return Result.success(
                    EmailJob(
                        id = safeJobId,
                        idempotencyKey = idempotencyKey,
                        userId = userId,
                        recipientEmail = cleanEmail,
                        templateKey = templateKey,
                        subject = subject,
                        htmlBody = html,
                        textBody = text,
                        status = "awaiting_provider_config",
                        lastError = "RESEND_API_KEY not configured in AI Studio Secrets; email remains queued."
                    )
                )
            }

            // Persist queued job before dispatch
            jobRef.set(
                mapOf(
                    "idempotencyKey" to idempotencyKey,
                    "userId" to userId,
                    "recipientEmail" to cleanEmail,
                    "templateKey" to templateKey,
                    "subject" to subject,
                    "htmlBody" to html,
                    "textBody" to text,
                    "status" to "queued",
                    "provider" to "resend",
                    "attempts" to 0,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            dispatchExistingJob(
                EmailJob(
                    id = safeJobId,
                    idempotencyKey = idempotencyKey,
                    userId = userId,
                    recipientEmail = cleanEmail,
                    templateKey = templateKey,
                    subject = subject,
                    htmlBody = html,
                    textBody = text,
                    status = "queued",
                    attempts = 0
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun dispatchExistingJob(job: EmailJob): Result<EmailJob> {
        val status = getProviderStatus()
        if (!status.isConfigured) {
            return Result.failure(IllegalStateException(status.statusDetail))
        }
        val key = apiKeyProvider().trim()
        val jobRef = db.collection("email_jobs").document(job.id)
        var lastError: String? = null
        var attemptCount = job.attempts
        val maxRetries = 2

        for (attempt in 1..maxRetries) {
            attemptCount += 1
            try {
                val resp = resendApi.sendEmail(
                    bearerToken = "Bearer $key",
                    idempotencyKey = job.idempotencyKey,
                    payload = ResendEmailPayload(
                        from = status.fromAddress,
                        to = listOf(job.recipientEmail),
                        subject = job.subject,
                        html = job.htmlBody,
                        text = job.textBody
                    )
                )
                if (resp.isSuccessful) {
                    val providerId = resp.body()?.id ?: "resend_${System.currentTimeMillis()}"
                    runCatching {
                        jobRef.update(
                            mapOf(
                                "status" to "sent",
                                "providerMessageId" to providerId,
                                "attempts" to attemptCount,
                                "lastError" to FieldValue.delete(),
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ).await()
                    }
                    return Result.success(
                        job.copy(
                            status = "sent",
                            providerMessageId = providerId,
                            attempts = attemptCount,
                            lastError = null
                        )
                    )
                } else {
                    lastError = "HTTP ${resp.code()}: ${resp.errorBody()?.string()?.take(200) ?: resp.message()}"
                    if (resp.code() in 400..499 && resp.code() != 429) {
                        break // Non-retryable 4xx error
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network failure during Resend dispatch"
            }
            if (attempt < maxRetries) {
                delay(400L * attempt)
            }
        }

        runCatching {
            jobRef.update(
                mapOf(
                    "status" to "failed",
                    "attempts" to attemptCount,
                    "lastError" to (lastError ?: "Delivery failed").take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        return Result.failure(IllegalStateException(lastError ?: "Email delivery failed"))
    }

    private suspend fun resolveTemplate(templateKey: String): EmailTemplate {
        val doc = runCatching { db.collection("email_templates").document(templateKey).get().await() }.getOrNull()
        val stored = doc?.takeIf { it.exists() }?.toObject(EmailTemplate::class.java)
        return stored ?: defaultTemplates().firstOrNull { it.templateKey == templateKey }
            ?: defaultTemplates().first()
    }

    private fun renderVars(template: String, vars: Map<String, String>): String {
        var out = template
        vars.forEach { (k, v) ->
            out = out.replace("{{$k}}", v)
        }
        return out
    }

    private fun sanitizeId(raw: String): String =
        raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)

    fun emailKeyHash(email: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(40)
    }

    companion object {
        fun defaultTemplates(): List<EmailTemplate> = listOf(
            wrapTemplate(
                key = "KYC_SUBMITTED",
                name = "KYC Verification Received",
                subject = "ApexTrade: KYC Verification Pending (24h Review)",
                headline = "KYC Submission Under Review",
                body = "Hello {{name}}, your identity documents ({{idType}}) are in Pending status. Our compliance system will review and approve within 24 hours."
            ),
            wrapTemplate(
                key = "KYC_APPROVED",
                name = "KYC Identity Approved",
                subject = "ApexTrade: Your KYC Identity Verification Is Approved",
                headline = "Identity Verification Approved",
                body = "Hello {{name}}, your KYC compliance review is approved. Deposits and withdrawals are now unlocked in your Portfolio wallet."
            ),
            wrapTemplate(
                key = "KYC_REJECTED",
                name = "KYC Verification Rejected",
                subject = "ApexTrade: Action Required on KYC Submission",
                headline = "KYC Resubmission Required",
                body = "Hello {{name}}, your KYC review could not be approved. Reason: {{reason}}. Please upload a clearer document from your Portfolio."
            ),
            wrapTemplate(
                key = "DEPOSIT_SUBMITTED",
                name = "Deposit Proof Submitted",
                subject = "ApexTrade: Deposit Proof Pending Verification ({{amount}})",
                headline = "Deposit Proof Received",
                body = "Hello {{name}}, your {{method}} deposit of {{amount}} (Ref: {{txHash}}) is Pending. System will review and approve within 24 hours."
            ),
            wrapTemplate(
                key = "DEPOSIT_APPROVED",
                name = "Deposit Credited",
                subject = "ApexTrade: Deposit Approved & Credited ({{amount}})",
                headline = "Funds Credited to Cash Wallet",
                body = "Hello {{name}}, your deposit of {{amount}} via {{method}} has been verified and credited to your available trading balance."
            ),
            wrapTemplate(
                key = "DEPOSIT_REJECTED",
                name = "Deposit Proof Rejected",
                subject = "ApexTrade: Deposit Verification Declined ({{amount}})",
                headline = "Deposit Could Not Be Verified",
                body = "Hello {{name}}, your deposit submission of {{amount}} was declined. Note: {{reason}}."
            ),
            wrapTemplate(
                key = "WITHDRAWAL_REQUESTED",
                name = "Withdrawal Pending Review",
                subject = "ApexTrade: Withdrawal Request Pending ({{amount}})",
                headline = "Withdrawal Pending Review",
                body = "Hello {{name}}, your withdrawal request for {{amount}} via {{method}} is Pending. System will review and approve within 24 hours."
            ),
            wrapTemplate(
                key = "WITHDRAWAL_PAID",
                name = "Manual Withdrawal Payout Completed",
                subject = "ApexTrade: Withdrawal Paid ({{amount}})",
                headline = "Withdrawal Settled",
                body = "Hello {{name}}, your withdrawal of {{amount}} via {{method}} has been paid manually. Payout Reference: {{payoutRef}}."
            ),
            wrapTemplate(
                key = "WITHDRAWAL_REJECTED",
                name = "Withdrawal Rejected & Refunded",
                subject = "ApexTrade: Withdrawal Refunded to Wallet ({{amount}})",
                headline = "Withdrawal Refunded",
                body = "Hello {{name}}, your withdrawal request of {{amount}} was declined and returned to your available cash balance. Note: {{reason}}."
            ),
            wrapTemplate(
                key = "TRADE_RECEIPT",
                name = "Trade Execution Receipt",
                subject = "ApexTrade Execution: {{side}} {{symbol}} ({{status}})",
                headline = "Trade Confirmation",
                body = "Order {{orderType}} {{side}} {{qty}} {{symbol}} @ {{price}} | Gross: {{gross}} | Commission: {{fee}} | Net: {{net}}."
            ),
            wrapTemplate(
                key = "SUPPORT_REPLY",
                name = "Support Ticket Reply",
                subject = "ApexTrade Support Update: {{subject}}",
                headline = "Support Team Response",
                body = "Hello {{name}}, our team replied to ticket \"{{subject}}\": {{reply}}"
            ),
            wrapTemplate(
                key = "ANNOUNCEMENT",
                name = "Platform Announcement",
                subject = "ApexTrade Notice: {{title}}",
                headline = "{{title}}",
                body = "{{message}}"
            )
        )

        private fun wrapTemplate(
            key: String,
            name: String,
            subject: String,
            headline: String,
            body: String
        ): EmailTemplate {
            val html = """
                <!DOCTYPE html>
                <html>
                <body style="background:#0B1220;color:#E5E7EB;font-family:Arial,sans-serif;padding:24px;">
                  <div style="max-width:560px;margin:0 auto;background:#111A2E;border:1px solid #243047;border-radius:16px;padding:24px;">
                    <div style="color:#10B981;font-weight:700;font-size:13px;letter-spacing:1px;">APEXTRADE GLOBAL BROKERAGE</div>
                    <h2 style="color:#F9FAFB;margin:12px 0;">$headline</h2>
                    <p style="color:#CBD5E1;line-height:1.6;font-size:14px;">$body</p>
                    <hr style="border:none;border-top:1px solid #243047;margin:20px 0;" />
                    <p style="color:#64748B;font-size:12px;">Automated compliance notice from ApexTrade Custody & Execution Systems.</p>
                  </div>
                </body>
                </html>
            """.trimIndent()
            return EmailTemplate(
                id = key,
                templateKey = key,
                name = name,
                subject = subject,
                htmlBody = html,
                textBody = "APEXTRADE GLOBAL BROKERAGE\n$headline\n\n$body"
            )
        }
    }
}
