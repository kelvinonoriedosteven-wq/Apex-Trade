package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UserAccount
import com.example.ui.components.formatUsdPrice
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldDim
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.CrimsonLossDim
import com.example.ui.theme.EmeraldAlpha
import com.example.ui.theme.EmeraldAlphaDim
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.BrokerUiState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun AdminOverviewTab(
    uiState: BrokerUiState,
    onAssignStaffRole: (UserAccount, String) -> Unit
) {
    val totalCashCustody = uiState.allUsersForAdmin.sumOf { it.cashBalanceUsd }
    val pendingPayoutUsd = uiState.allUsersForAdmin.sumOf { it.pendingWithdrawalUsd }
    val totalTradeVol = uiState.allTradesForAdmin.sumOf { it.grossAmountUsd }
    val totalCommissions = uiState.allTradesForAdmin.sumOf { it.commissionUsd }
    val recon = uiState.ledgerReconciliationReport
    val openAml = uiState.allAmlAlertsForAdmin.count { it.status == "open" || it.status == "escalated" }
    val openMc = uiState.pendingApprovalsForAdmin.count { it.status == "pending_checker" }
    val openTickets = uiState.allTicketsForAdmin.count { it.status == "open" || it.status == "in_progress" }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Client Cash Custody", formatUsdPrice(totalCashCustody), EmeraldAlpha, Modifier.weight(1f))
                KpiCard("Pending Payouts", formatUsdPrice(pendingPayoutUsd), ChampagneGold, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Platform Volume", formatUsdPrice(totalTradeVol), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                KpiCard("Fee Revenue", formatUsdPrice(totalCommissions), ChampagneGold, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard("Maker-Checker Queue", "$openMc Pending", ChampagneGold, Modifier.weight(1f))
                KpiCard("AML Risk Alerts", "$openAml Open", if (openAml > 0) CrimsonLoss else EmeraldAlpha, Modifier.weight(1f))
                KpiCard("Support Desk", "$openTickets Open", EmeraldAlpha, Modifier.weight(1f))
            }
        }

        // Double-Entry Ledger Reconciliation Health Card
        item {
            val allMatch = recon.mismatchedUsers.isEmpty()
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, if (allMatch) EmeraldAlpha.copy(alpha = 0.5f) else CrimsonLoss),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (allMatch) "Double-Entry Ledger Reconciliation: HEALTHY" else "Ledger Reconciliation Mismatch (${recon.mismatchedUsers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (allMatch) EmeraldAlpha else CrimsonLoss
                    )
                    Text(
                        text = "Audited ${recon.totalUsersChecked} accounts • ${recon.matchedUsers} matched exact Sum(CREDIT) - Sum(DEBIT) invariant • ${uiState.allLedgerForAdmin.size} immutable ledger entries.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    recon.mismatchedUsers.take(3).forEach { m ->
                        Text(
                            text = "${m.email}: User=${formatUsdPrice(m.reportedCashUsd)} vs Ledger=${formatUsdPrice(m.ledgerComputedCashUsd)} (Δ ${formatUsdPrice(m.deltaUsd)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonLoss
                        )
                    }
                }
            }
        }

        // Staff RBAC Overview & Quick Role Assignment
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Staff Access & RBAC (${uiState.staffRolesForAdmin.size})", style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                    uiState.staffRolesForAdmin.forEach { sr ->
                        Text("${sr.displayName} (${sr.email}) — ${sr.role}", style = MaterialTheme.typography.bodySmall)
                    }
                    if (uiState.allUsersForAdmin.isNotEmpty()) {
                        Text("Assign Staff Role to Trader:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        uiState.allUsersForAdmin.take(4).forEach { u ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(u.email, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = { onAssignStaffRole(u, "COMPLIANCE_OFFICER") }) {
                                        Text("Compliance", style = MaterialTheme.typography.labelSmall)
                                    }
                                    OutlinedButton(onClick = { onAssignStaffRole(u, "FINANCE_CHECKER") }) {
                                        Text("Checker", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminMakerCheckerTab(
    uiState: BrokerUiState,
    onDecideApproval: (approvalId: String, approve: Boolean, reason: String?) -> Unit
) {
    val currentUid = uiState.userAccount?.userId.orEmpty()
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.pendingApprovalsForAdmin.isEmpty()) {
            item {
                AdminInfoCard("No dual-control Maker-Checker items yet. Payouts >= ${formatUsdPrice(uiState.brokerConfig.makerCheckerThresholdUsd)} require second Admin sign-off.")
            }
        } else {
            items(uiState.pendingApprovalsForAdmin, key = { it.id.ifBlank { it.targetReferenceId } }) { mc ->
                val isPending = mc.status == "pending_checker"
                val isSameAdmin = mc.makerUid == currentUid
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, if (isPending) ChampagneGold.copy(alpha = 0.6f) else ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(mc.actionType.replace("_", " "), style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (mc.status == "approved") EmeraldAlphaDim else if (isPending) ChampagneGoldDim else CrimsonLossDim
                            ) {
                                Text(
                                    mc.status.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (mc.status == "approved") EmeraldAlpha else if (isPending) ChampagneGold else CrimsonLoss,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(mc.payloadSummary, style = MaterialTheme.typography.bodySmall)
                        Text("Maker: ${mc.makerEmail} • Amount: ${formatUsdPrice(mc.amountUsd)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isPending) {
                            if (isSameAdmin) {
                                Text(
                                    "Dual-Control Lock: A different Admin must sign off as Checker.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrimsonLoss
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onDecideApproval(mc.id, true, null) },
                                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Checker Approve", fontWeight = FontWeight.Bold) }
                                    OutlinedButton(
                                        onClick = { onDecideApproval(mc.id, false, "Declined by Checker") },
                                        border = BorderStroke(1.dp, CrimsonLoss),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Reject", color = CrimsonLoss) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAmlRiskTab(
    uiState: BrokerUiState,
    onResolveAml: (alertId: String, status: String, note: String) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (uiState.allAmlAlertsForAdmin.isEmpty()) {
            item { AdminInfoCard("No AML / High-Value compliance alerts triggered.") }
        } else {
            items(uiState.allAmlAlertsForAdmin, key = { it.id.ifBlank { "${it.referenceId}_${it.userId}" } }) { alert ->
                val isOpen = alert.status == "open" || alert.status == "escalated"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, if (isOpen) CrimsonLoss.copy(alpha = 0.65f) else ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${alert.severity} • ${alert.alertType}", style = MaterialTheme.typography.titleSmall, color = if (isOpen) CrimsonLoss else EmeraldAlpha)
                            Text(alert.status.uppercase(), style = MaterialTheme.typography.labelSmall, color = ChampagneGold)
                        }
                        Text("${alert.userEmail} • ${formatUsdPrice(alert.amountUsd)}", style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold))
                        Text(alert.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isOpen) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onResolveAml(alert.id, "cleared", "Verified source of funds") },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Mark Cleared", fontWeight = FontWeight.Bold) }
                                OutlinedButton(
                                    onClick = { onResolveAml(alert.id, "escalated", "Escalated to Compliance Officer") },
                                    border = BorderStroke(1.dp, ChampagneGold),
                                    modifier = Modifier.weight(1f)
                                ) { Text("Escalate", color = ChampagneGold) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminAuditLogsTab(uiState: BrokerUiState) {
    val fmt = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.auditLogsForAdmin.isEmpty()) {
            item { AdminInfoCard("Immutable admin audit log entries will appear here.") }
        } else {
            items(uiState.auditLogsForAdmin, key = { it.id.ifBlank { "${it.action}_${it.targetId}_${it.createdAt?.nanoseconds}" } }) { log ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(log.action, style = MaterialTheme.typography.titleSmall, color = ChampagneGold)
                            Text(log.createdAt?.toDate()?.let { fmt.format(it) } ?: "Live", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(log.details, style = MaterialTheme.typography.bodySmall)
                        Text("Actor: ${log.actorEmail} • Target: ${log.targetType}/${log.targetId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(3.dp))
            Text(value, style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold), color = color)
        }
    }
}

@Composable
private fun AdminInfoCard(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp))
    }
}
