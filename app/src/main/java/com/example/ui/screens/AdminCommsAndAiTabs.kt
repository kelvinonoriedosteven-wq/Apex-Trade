package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EmailJob
import com.example.data.model.EmailTemplate
import com.example.data.model.SupportTicket
import com.example.data.repository.AiUiState
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.EmeraldAlpha
import com.example.ui.theme.EmeraldAlphaDim
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.BrokerUiState

@Composable
fun AdminSupportDeskTab(
    uiState: BrokerUiState,
    onReplyTicket: (SupportTicket, status: String, reply: String) -> Unit
) {
    var replyingTicket by remember { mutableStateOf<SupportTicket?>(null) }
    var replyText by remember { mutableStateOf("") }
    var targetStatus by remember { mutableStateOf("resolved") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (uiState.allTicketsForAdmin.isEmpty()) {
            item { EmptyCommsCard("No client support tickets submitted yet.") }
        } else {
            items(uiState.allTicketsForAdmin, key = { it.id.ifBlank { "${it.userId}_${it.subject}" } }) { ticket ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, if (ticket.status == "open") ChampagneGold.copy(alpha = 0.6f) else ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ticket.subject, style = MaterialTheme.typography.titleSmall)
                            Text(ticket.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                        }
                        Text("${ticket.userDisplayName} (${ticket.userEmail}) • ${ticket.category}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ticket.message, style = MaterialTheme.typography.bodySmall)
                        if (!ticket.adminReply.isNullOrBlank()) {
                            Text("Reply: ${ticket.adminReply}", style = MaterialTheme.typography.labelSmall, color = EmeraldAlpha)
                        }
                        OutlinedButton(
                            onClick = {
                                replyingTicket = ticket
                                replyText = ticket.adminReply.orEmpty()
                                targetStatus = "resolved"
                            },
                            border = BorderStroke(1.dp, ChampagneGold)
                        ) { Text("Reply & Update Status", color = ChampagneGold) }
                    }
                }
            }
        }
    }

    replyingTicket?.let { t ->
        AlertDialog(
            onDismissRequest = { replyingTicket = null },
            containerColor = ObsidianSurface,
            title = { Text("Reply to Support Ticket") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("in_progress", "resolved", "closed").forEach { st ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (targetStatus == st) EmeraldAlphaDim else ObsidianBg,
                                border = BorderStroke(1.dp, if (targetStatus == st) EmeraldAlpha else ObsidianOutline),
                                modifier = Modifier.clickable { targetStatus = st }
                            ) {
                                Text(st.uppercase(), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                    OutlinedTextField(value = replyText, onValueChange = { replyText = it }, label = { Text("Admin Response") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = { onReplyTicket(t, targetStatus, replyText); replyingTicket = null },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg)
                ) { Text("Send Reply", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { replyingTicket = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AdminAnnouncementsTab(
    uiState: BrokerUiState,
    onPublish: (title: String, message: String, severity: String, audience: String) -> Unit,
    onDeactivate: (String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("INFO") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Publish Live Market Broadcast", style = MaterialTheme.typography.titleMedium, color = ChampagneGold)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Headline") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = message, onValueChange = { message = it }, label = { Text("Broadcast Message") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("INFO", "WARNING", "ALERT").forEach { sev ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (severity == sev) EmeraldAlphaDim else ObsidianBg,
                                border = BorderStroke(1.dp, if (severity == sev) EmeraldAlpha else ObsidianOutline),
                                modifier = Modifier.clickable { severity = sev }
                            ) {
                                Text(sev, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                            }
                        }
                    }
                    Button(
                        onClick = {
                            if (title.isNotBlank() && message.isNotBlank()) {
                                onPublish(title, message, severity, "ALL")
                                title = ""; message = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBg),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Broadcast to All Traders", fontWeight = FontWeight.Bold) }
                }
            }
        }
        items(uiState.activeAnnouncements, key = { it.id }) { ann ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${ann.severity} • ${ann.title}", style = MaterialTheme.typography.titleSmall)
                        Text(ann.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onDeactivate(ann.id) }) {
                        Text("Archive", color = CrimsonLoss)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminEmailCenterTab(
    uiState: BrokerUiState,
    onSaveTemplate: (EmailTemplate) -> Unit,
    onRetryJob: (EmailJob) -> Unit,
    onSendTestEmail: (recipient: String, templateKey: String) -> Unit,
    onAddSuppression: (email: String, reason: String) -> Unit,
    onRemoveSuppression: (id: String) -> Unit
) {
    val provider = uiState.emailProviderStatus
    var testRecipient by remember(uiState.userAccount?.email) { mutableStateOf(uiState.userAccount?.email ?: "") }
    var editingTemplate by remember { mutableStateOf<EmailTemplate?>(null) }
    var suppressEmailInput by remember { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, if (provider.isConfigured) EmeraldAlpha else ChampagneGold),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = if (provider.isConfigured) EmeraldAlpha else ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${provider.providerName} • ${if (provider.isConfigured) "LIVE" else "AWAITING SECRET KEY"}", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(provider.statusDetail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = testRecipient, onValueChange = { testRecipient = it }, label = { Text("Test Recipient") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(
                            onClick = { if (testRecipient.isNotBlank()) onSendTestEmail(testRecipient, "KYC_APPROVED") },
                            colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBg)
                        ) { Text("Test") }
                    }
                }
            }
        }

        item { Text("Transactional Templates (${uiState.emailTemplatesForAdmin.size})", style = MaterialTheme.typography.titleSmall, color = ChampagneGold) }
        items(uiState.emailTemplatesForAdmin.take(6), key = { it.templateKey }) { tpl ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier.fillMaxWidth().clickable { editingTemplate = tpl }
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tpl.templateKey, style = MaterialTheme.typography.labelLarge, color = EmeraldAlpha)
                        Text(tpl.subject, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (tpl.isEnabled) "ENABLED" else "PAUSED", style = MaterialTheme.typography.labelSmall, color = if (tpl.isEnabled) EmeraldAlpha else CrimsonLoss)
                }
            }
        }

        item { Text("Outbox Queue (${uiState.emailJobsForAdmin.size})", style = MaterialTheme.typography.titleSmall, color = ChampagneGold) }
        items(uiState.emailJobsForAdmin.take(15), key = { it.id.ifBlank { it.idempotencyKey } }) { job ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${job.templateKey} -> ${job.recipientEmail}", style = MaterialTheme.typography.labelLarge)
                        Text("Status: ${job.status.uppercase()} • Attempts: ${job.attempts}", style = MaterialTheme.typography.labelSmall, color = if (job.status == "sent") EmeraldAlpha else ChampagneGold)
                        if (!job.lastError.isNullOrBlank()) {
                            Text(job.lastError, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (job.status != "sent" && job.status != "suppressed") {
                        TextButton(onClick = { onRetryJob(job) }) { Text("Retry", color = ChampagneGold) }
                    }
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Bounce & Opt-Out Suppressions (${uiState.emailSuppressionsForAdmin.size})", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = suppressEmailInput, onValueChange = { suppressEmailInput = it }, label = { Text("Suppress Email") }, singleLine = true, modifier = Modifier.weight(1f))
                        Button(onClick = { if (suppressEmailInput.isNotBlank()) { onAddSuppression(suppressEmailInput, "ADMIN_SUPPRESSED"); suppressEmailInput = "" } }) { Text("Add") }
                    }
                    uiState.emailSuppressionsForAdmin.forEach { sup ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${sup.email} (${sup.reason})", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { onRemoveSuppression(sup.id) }) { Text("Remove", color = CrimsonLoss) }
                        }
                    }
                }
            }
        }
    }

    editingTemplate?.let { tpl ->
        var subject by remember(tpl.templateKey) { mutableStateOf(tpl.subject) }
        var textBody by remember(tpl.templateKey) { mutableStateOf(tpl.textBody) }
        var enabled by remember(tpl.templateKey) { mutableStateOf(tpl.isEnabled) }
        AlertDialog(
            onDismissRequest = { editingTemplate = null },
            containerColor = ObsidianSurface,
            title = { Text("Edit ${tpl.templateKey}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Template Active")
                        Switch(checked = enabled, onCheckedChange = { enabled = it }, colors = SwitchDefaults.colors(checkedTrackColor = EmeraldAlpha))
                    }
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = textBody, onValueChange = { textBody = it }, label = { Text("Plain Text Body") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveTemplate(tpl.copy(subject = subject, textBody = textBody, isEnabled = enabled))
                        editingTemplate = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBg)
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingTemplate = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun AdminAiCopilotTab(
    uiState: BrokerUiState,
    onAskAdminAi: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.55f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Risk & Operations AI Copilot (Read-Only)", style = MaterialTheme.typography.titleMedium, color = ChampagneGold)
                    }
                    Text(
                        "Summarize pending compliance queues, interpret AML risk spikes, verify reconciliation health, or draft client notices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            placeholder = { Text("Summarize open AML alerts & Maker-Checker queue...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ChampagneGold, unfocusedBorderColor = ObsidianOutline),
                            modifier = Modifier.weight(1f).testTag("admin_ai_input")
                        )
                        IconButton(onClick = { if (prompt.isNotBlank()) { onAskAdminAi(prompt); prompt = "" } }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Admin AI prompt", tint = ChampagneGold)
                        }
                    }
                    if (uiState.adminAiState is AiUiState.Loading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = ChampagneGold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Synthesizing operational brief...", style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                        }
                    }
                }
            }
        }
        items(uiState.adminAiMessages, key = { it.id }) { msg ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (msg.isUser) EmeraldAlphaDim else ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = (if (msg.isUser) "Admin: " else "Risk Copilot: ") + msg.text,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyCommsCard(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp))
    }
}
