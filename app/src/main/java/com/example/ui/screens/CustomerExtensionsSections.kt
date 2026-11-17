package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.NotificationPreferences
import com.example.data.repository.AiUiState
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.EmeraldAlpha
import com.example.ui.theme.EmeraldAlphaDim
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import com.example.ui.viewmodel.BrokerUiState

@Composable
fun CustomerSecurityAndPrefsSection(
    uiState: BrokerUiState,
    onEnablePin: (String) -> Unit,
    onDisablePin: () -> Unit,
    onRevokeSession: (String) -> Unit,
    onUpdateNotifPrefs: (NotificationPreferences) -> Unit,
    onApplyReferral: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var referralInput by remember { mutableStateOf("") }

    // 1. Security PIN & Active Sessions Card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Pin, contentDescription = null, tint = EmeraldAlpha, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Security PIN & Active Devices", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("2FA Device PIN Lock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (uiState.localSecurityState.pinEnabled) "Enabled on this device (4–8 digit PIN)"
                        else "Protect your portfolio with a quick local Security PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = uiState.localSecurityState.pinEnabled,
                    onCheckedChange = { enable ->
                        if (enable) { pinInput = ""; showPinSetupDialog = true } else onDisablePin()
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = EmeraldAlpha)
                )
            }

            if (uiState.userSessions.isNotEmpty()) {
                HorizontalDivider(color = ObsidianOutline)
                Text("Active & Recent Sessions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                uiState.userSessions.take(4).forEach { sess ->
                    val isCurrent = sess.id == uiState.currentDeviceSessionId
                    val isRevoked = sess.status == "revoked"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Devices, contentDescription = null, tint = if (isRevoked) CrimsonLoss else EmeraldAlpha, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(sess.deviceName.ifBlank { sess.platform }, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    if (isCurrent) "This Device • ${sess.status.uppercase()}" else sess.status.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isRevoked) CrimsonLoss else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!isCurrent && !isRevoked) {
                            TextButton(onClick = { onRevokeSession(sess.id) }) {
                                Text("Revoke", color = CrimsonLoss, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Notification & Email Preferences
    val prefs = uiState.notificationPreferences
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notification & Email Preferences", style = MaterialTheme.typography.titleMedium)
            }
            PrefSwitchRow("Trade Execution Receipts (Email)", prefs.emailTradeReceiptsEnabled) {
                onUpdateNotifPrefs(prefs.copy(emailTradeReceiptsEnabled = it))
            }
            PrefSwitchRow("KYC, Deposit & Payout Alerts (Email)", prefs.emailFundingEnabled) {
                onUpdateNotifPrefs(prefs.copy(emailFundingEnabled = it))
            }
            PrefSwitchRow("Security & Login Alerts (Email)", prefs.emailSecurityEnabled) {
                onUpdateNotifPrefs(prefs.copy(emailSecurityEnabled = it))
            }
        }
    }

    // 3. Referral Code Card
    val myCode = uiState.userAccount?.referralCode?.ifBlank { "APEX-TRADER" } ?: "APEX-TRADER"
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Client Referral Program", style = MaterialTheme.typography.titleMedium)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { clipboard.setText(AnnotatedString(myCode)) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Your Invite Code", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(myCode, style = MaterialTheme.typography.titleMedium, color = EmeraldAlpha, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy referral code", tint = EmeraldAlpha)
            }
            val linkedRef = uiState.userAccount?.referredBy
            if (!linkedRef.isNullOrBlank()) {
                Text("Referred by: $linkedRef", style = MaterialTheme.typography.labelMedium, color = ChampagneGold)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = referralInput,
                        onValueChange = { referralInput = it },
                        label = { Text("Enter Sponsor Code") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { if (referralInput.isNotBlank()) onApplyReferral(referralInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg)
                    ) { Text("Apply") }
                }
            }
        }
    }

    if (showPinSetupDialog) {
        AlertDialog(
            onDismissRequest = { showPinSetupDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Set 4–8 Digit Security PIN") },
            text = {
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pinInput = it },
                    label = { Text("Enter Security PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length in 4..8) {
                            onEnablePin(pinInput)
                            showPinSetupDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg)
                ) { Text("Activate PIN", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPinSetupDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun CustomerSupportAndAiSection(
    uiState: BrokerUiState,
    onCreateTicket: (subject: String, category: String, priority: String, message: String, onDone: () -> Unit) -> Unit,
    onAskCustomerAi: (String) -> Unit
) {
    var showTicketDialog by remember { mutableStateOf(false) }
    var ticketSubject by remember { mutableStateOf("") }
    var ticketCategory by remember { mutableStateOf("KYC_FUNDS") }
    var ticketMessage by remember { mutableStateOf("") }
    var aiPrompt by remember { mutableStateOf("") }

    // 1. Customer Support Desk Card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.SupportAgent, contentDescription = null, tint = EmeraldAlpha, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Brokerage Support Desk", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { showTicketDialog = true },
                    border = BorderStroke(1.dp, EmeraldAlpha),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("new_support_ticket_button")
                ) { Text("New Ticket", color = EmeraldAlpha, style = MaterialTheme.typography.labelMedium) }
            }

            if (uiState.userTickets.isEmpty()) {
                Text(
                    "Need assistance with KYC, deposit hashes, or payouts? Open a ticket for 24h specialist review.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                uiState.userTickets.take(3).forEach { t ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ObsidianBg,
                        border = BorderStroke(1.dp, ObsidianOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(t.subject, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    t.status.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (t.status == "resolved") EmeraldAlpha else ChampagneGold
                                )
                            }
                            Text(t.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!t.adminReply.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Admin Reply: ${t.adminReply}", style = MaterialTheme.typography.labelSmall, color = EmeraldAlpha)
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Read-Only Gemini AI Client Copilot
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, EmeraldAlpha.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = EmeraldAlpha, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("ApexTrade Client AI Copilot", style = MaterialTheme.typography.titleMedium)
                    Text("Read-only portfolio analytics & trading education", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            uiState.customerAiMessages.takeLast(4).forEach { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (msg.isUser) EmeraldAlphaDim else ObsidianBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = (if (msg.isUser) "You: " else "Copilot: ") + msg.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
            if (uiState.customerAiState is AiUiState.Loading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = EmeraldAlpha)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Analyzing live portfolio...", style = MaterialTheme.typography.labelSmall, color = EmeraldAlpha)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = aiPrompt,
                    onValueChange = { aiPrompt = it },
                    placeholder = { Text("Ask about your allocation or order types...") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.weight(1f).testTag("customer_ai_input")
                )
                IconButton(
                    onClick = {
                        if (aiPrompt.isNotBlank()) {
                            onAskCustomerAi(aiPrompt)
                            aiPrompt = ""
                        }
                    },
                    modifier = Modifier.testTag("customer_ai_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask Copilot", tint = EmeraldAlpha)
                }
            }
        }
    }

    if (showTicketDialog) {
        AlertDialog(
            onDismissRequest = { showTicketDialog = false },
            containerColor = ObsidianSurface,
            title = { Text("Submit Support Ticket") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = ticketSubject, onValueChange = { ticketSubject = it }, label = { Text("Subject") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("KYC_FUNDS", "TRADING", "SECURITY").forEach { cat ->
                            val active = ticketCategory == cat
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (active) EmeraldAlphaDim else ObsidianSurfaceElevated,
                                border = BorderStroke(1.dp, if (active) EmeraldAlpha else ObsidianOutline),
                                modifier = Modifier.clickable { ticketCategory = cat }
                            ) {
                                Text(cat, style = MaterialTheme.typography.labelSmall, color = if (active) EmeraldAlpha else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                        }
                    }
                    OutlinedTextField(value = ticketMessage, onValueChange = { ticketMessage = it }, label = { Text("Message / Tx Reference") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateTicket(ticketSubject, ticketCategory, "NORMAL", ticketMessage) {
                            ticketSubject = ""; ticketMessage = ""; showTicketDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg)
                ) { Text("Submit", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showTicketDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun PinLockOverlay(
    onVerifyPin: (String, (Boolean, String?) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, EmeraldAlpha.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = EmeraldAlpha, modifier = Modifier.size(36.dp))
                Text("ApexTrade Security Lock", style = MaterialTheme.typography.titleLarge)
                Text("Enter your Security PIN to unlock your brokerage portfolio.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) { pin = it; error = null } },
                    label = { Text("Security PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                }
                Button(
                    onClick = {
                        onVerifyPin(pin) { ok, err ->
                            if (!ok) {
                                error = err ?: "Incorrect PIN"
                                pin = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) { Text("Unlock", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun PrefSwitchRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = EmeraldAlpha)
        )
    }
}
