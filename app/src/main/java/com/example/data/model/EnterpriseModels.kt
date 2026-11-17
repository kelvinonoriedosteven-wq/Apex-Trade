package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class LedgerEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val entryType: String = "DEPOSIT_CREDIT",
    val direction: String = "CREDIT",
    val amountUsd: Double = 0.0,
    val balanceBeforeUsd: Double = 0.0,
    val balanceAfterUsd: Double = 0.0,
    val referenceType: String = "",
    val referenceId: String = "",
    val description: String = "",
    val actorUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AppNotification(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val message: String = "",
    val category: String = "INFO",
    val severity: String = "INFO",
    val isRead: Boolean = false,
    val isArchived: Boolean = false,
    val actionRoute: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val userEmail: String = "",
    val subject: String = "",
    val category: String = "ACCOUNT",
    val priority: String = "NORMAL",
    val status: String = "open",
    val message: String = "",
    val adminReply: String? = null,
    val repliedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PlatformAnnouncement(
    @DocumentId val id: String = "",
    val title: String = "",
    val message: String = "",
    val severity: String = "INFO",
    val targetAudience: String = "ALL",
    val isActive: Boolean = true,
    val createdByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AmlRiskAlert(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val alertType: String = "LARGE_DEPOSIT",
    val severity: String = "MEDIUM",
    val amountUsd: Double = 0.0,
    val referenceId: String = "",
    val summary: String = "",
    val status: String = "open",
    val resolutionNote: String? = null,
    val resolvedByUid: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class MakerCheckerApproval(
    @DocumentId val id: String = "",
    val actionType: String = "LARGE_WITHDRAWAL_PAYOUT",
    val targetUserId: String = "",
    val targetReferenceId: String = "",
    val amountUsd: Double = 0.0,
    val payloadSummary: String = "",
    val payoutReference: String? = null,
    val makerUid: String = "",
    val makerEmail: String = "",
    val checkerUid: String? = null,
    val checkerEmail: String? = null,
    val status: String = "pending_checker",
    val rejectionReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class AuditLogEntry(
    @DocumentId val id: String = "",
    val actorUid: String = "",
    val actorEmail: String = "",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val details: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StaffRoleAssignment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "SUPPORT_AGENT",
    val assignedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserSessionRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val appVersion: String = "1.0",
    val status: String = "active",
    @ServerTimestamp val lastActiveAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class NotificationPreferences(
    @DocumentId val id: String = "",
    val userId: String = "",
    val inAppEnabled: Boolean = true,
    val emailSecurityEnabled: Boolean = true,
    val emailFundingEnabled: Boolean = true,
    val emailTradeReceiptsEnabled: Boolean = true,
    val emailAnnouncementsEnabled: Boolean = true,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EmailTemplate(
    @DocumentId val id: String = "",
    val templateKey: String = "",
    val name: String = "",
    val subject: String = "",
    val htmlBody: String = "",
    val textBody: String = "",
    val isEnabled: Boolean = true,
    val updatedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EmailJob(
    @DocumentId val id: String = "",
    val idempotencyKey: String = "",
    val userId: String = "",
    val recipientEmail: String = "",
    val templateKey: String = "",
    val subject: String = "",
    val htmlBody: String = "",
    val textBody: String = "",
    val status: String = "queued",
    val provider: String = "resend",
    val providerMessageId: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EmailSuppression(
    @DocumentId val id: String = "",
    val email: String = "",
    val reason: String = "USER_OPT_OUT",
    val addedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
