package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BrokerConfig
import com.example.data.model.DepositRequest
import com.example.data.model.FundingMethod
import com.example.data.model.KycSubmission
import com.example.data.model.WithdrawalRequest
import com.example.ui.components.decodeBase64ToImageBitmap
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

@Composable
fun AdminKycTab(
    requests: List<KycSubmission>,
    onReviewKyc: (targetUserId: String, approve: Boolean, reason: String?) -> Unit
) {
    var rejectingKyc by remember { mutableStateOf<KycSubmission?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (requests.isEmpty()) {
            item {
                AdminEmptyStateCard("No KYC submissions yet.")
            }
        } else {
            items(requests, key = { it.userId }) { kyc ->
                val isPending = kyc.status == "pending"
                val isApproved = kyc.status == "approved"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, if (isPending) ChampagneGold.copy(alpha = 0.55f) else ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(kyc.fullLegalName, style = MaterialTheme.typography.titleMedium)
                                Text(kyc.userEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AdminStatusBadge(
                                status = kyc.status,
                                isPositive = isApproved,
                                isPending = isPending
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${kyc.idType} (${kyc.idNumber}) • ${kyc.country} • DOB: ${kyc.dateOfBirth}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val docBitmap = remember(kyc.docImageBase64) { decodeBase64ToImageBitmap(kyc.docImageBase64) }
                        if (docBitmap != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                bitmap = docBitmap,
                                contentDescription = "User ID Document",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }

                        if (isPending) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onReviewKyc(kyc.userId, true, null) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("admin_approve_kyc_${kyc.userId}")
                                ) {
                                    Text("Approve KYC", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        rejectingKyc = kyc
                                        rejectReason = ""
                                    },
                                    border = BorderStroke(1.dp, CrimsonLoss),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reject", color = CrimsonLoss)
                                }
                            }
                        } else if (!kyc.rejectionReason.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Rejection Reason: ${kyc.rejectionReason}", style = MaterialTheme.typography.labelSmall, color = CrimsonLoss)
                        }
                    }
                }
            }
        }
    }

    rejectingKyc?.let { target ->
        AlertDialog(
            onDismissRequest = { rejectingKyc = null },
            containerColor = ObsidianSurface,
            title = { Text("Reject KYC Submission") },
            text = {
                OutlinedTextField(
                    value = rejectReason,
                    onValueChange = { rejectReason = it },
                    label = { Text("Rejection Reason") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onReviewKyc(target.userId, false, rejectReason)
                        rejectingKyc = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonLoss, contentColor = Color.White)
                ) { Text("Confirm Reject") }
            },
            dismissButton = {
                TextButton(onClick = { rejectingKyc = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AdminDepositsTab(
    deposits: List<DepositRequest>,
    onReviewDeposit: (depositId: String, approve: Boolean, note: String?) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (deposits.isEmpty()) {
            item {
                AdminEmptyStateCard("No deposit proof submissions yet.")
            }
        } else {
            items(deposits, key = { it.id.ifBlank { "${it.userId}_${it.txHash}" } }) { dep ->
                val isPending = dep.status == "pending"
                val isApproved = dep.status == "approved"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, if (isPending) ChampagneGold.copy(alpha = 0.55f) else ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${formatUsdPrice(dep.amountUsd)} • ${FundingMethod.fromWire(dep.method).displayName}",
                                    style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldAlpha
                                )
                                Text("${dep.userDisplayName} (${dep.userEmail})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AdminStatusBadge(dep.status, isPositive = isApproved, isPending = isPending)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("TxHash / Ref: ${dep.txHash}", style = MaterialTheme.typography.bodySmall)
                        Text("Wallet Used: ${dep.walletAddressUsed}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        val proofBitmap = remember(dep.proofImageBase64) { decodeBase64ToImageBitmap(dep.proofImageBase64) }
                        if (proofBitmap != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Image(
                                bitmap = proofBitmap,
                                contentDescription = "Deposit Proof Receipt",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }

                        if (isPending) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onReviewDeposit(dep.id, true, "Proof verified & credited") },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("admin_approve_deposit_${dep.id}")
                                ) {
                                    Text("Approve & Credit", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onReviewDeposit(dep.id, false, "Proof could not be verified on-chain") },
                                    border = BorderStroke(1.dp, CrimsonLoss),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reject", color = CrimsonLoss)
                                }
                            }
                        } else if (!dep.adminNote.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Admin Note: ${dep.adminNote}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdminWithdrawalsTab(
    withdrawals: List<WithdrawalRequest>,
    onProcessWithdrawal: (withdrawalId: String, markPaid: Boolean, payoutReference: String, note: String?) -> Unit
) {
    var payingWd by remember { mutableStateOf<WithdrawalRequest?>(null) }
    var payoutRef by remember { mutableStateOf("") }
    var payoutNote by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (withdrawals.isEmpty()) {
            item {
                AdminEmptyStateCard("No withdrawal requests yet.")
            }
        } else {
            items(withdrawals, key = { it.id.ifBlank { "${it.userId}_${it.destinationDetails}" } }) { wd ->
                val isPending = wd.status == "pending"
                val isPaid = wd.status == "paid"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, if (isPending) ChampagneGold.copy(alpha = 0.55f) else ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${formatUsdPrice(wd.amountUsd)} • ${FundingMethod.fromWire(wd.method).displayName}",
                                    style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                                    color = ChampagneGold
                                )
                                Text("${wd.userDisplayName} (${wd.userEmail})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            AdminStatusBadge(wd.status, isPositive = isPaid, isPending = isPending)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Beneficiary: ${wd.accountHolderName}", style = MaterialTheme.typography.bodySmall)
                        Text("Destination: ${wd.destinationDetails}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (isPending) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        payingWd = wd
                                        payoutRef = ""
                                        payoutNote = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).testTag("admin_pay_withdrawal_${wd.id}")
                                ) {
                                    Text("Pay Manually", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onProcessWithdrawal(wd.id, false, "", "Rejected & refunded to user cash") },
                                    border = BorderStroke(1.dp, CrimsonLoss),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Reject & Refund", color = CrimsonLoss)
                                }
                            }
                        } else if (!wd.payoutReference.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Payout Ref: ${wd.payoutReference}", style = MaterialTheme.typography.labelSmall, color = EmeraldAlpha)
                        }
                    }
                }
            }
        }
    }

    payingWd?.let { target ->
        AlertDialog(
            onDismissRequest = { payingWd = null },
            containerColor = ObsidianSurface,
            title = { Text("Record Manual Payout") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Send ${formatUsdPrice(target.amountUsd)} via ${FundingMethod.fromWire(target.method).displayName} to:\n${target.destinationDetails}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = payoutRef,
                        onValueChange = { payoutRef = it },
                        label = { Text("Payout TxHash / Bank Reference") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payoutNote,
                        onValueChange = { payoutNote = it },
                        label = { Text("Admin Note (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanRef = payoutRef.trim().ifBlank { "MANUAL_PAYOUT_${System.currentTimeMillis()}" }
                        onProcessWithdrawal(target.id, true, cleanRef, payoutNote.ifBlank { "Paid manually by Admin" })
                        payingWd = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg)
                ) { Text("Confirm Paid", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { payingWd = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun AdminWalletsTab(
    brokerConfig: BrokerConfig,
    onSaveConfig: (BrokerConfig) -> Unit
) {
    var trc20 by remember(brokerConfig.usdtTrc20Address) { mutableStateOf(brokerConfig.usdtTrc20Address) }
    var erc20 by remember(brokerConfig.usdtErc20Address) { mutableStateOf(brokerConfig.usdtErc20Address) }
    var btc by remember(brokerConfig.btcWalletAddress) { mutableStateOf(brokerConfig.btcWalletAddress) }
    var eth by remember(brokerConfig.ethWalletAddress) { mutableStateOf(brokerConfig.ethWalletAddress) }
    var bankWire by remember(brokerConfig.bankWireDetails) { mutableStateOf(brokerConfig.bankWireDetails) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Official Admin Deposit Wallets",
            style = MaterialTheme.typography.titleMedium,
            color = ChampagneGold
        )
        Text(
            text = "Users see these addresses in real time on their Deposit screen and upload transfer proof against them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = trc20,
            onValueChange = { trc20 = it },
            label = { Text("USDT (TRC-20) Deposit Address") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ChampagneGold, unfocusedBorderColor = ObsidianOutline),
            modifier = Modifier.fillMaxWidth().testTag("admin_trc20_input")
        )
        OutlinedTextField(
            value = erc20,
            onValueChange = { erc20 = it },
            label = { Text("USDT (ERC-20) Deposit Address") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ChampagneGold, unfocusedBorderColor = ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = btc,
            onValueChange = { btc = it },
            label = { Text("Bitcoin (BTC) Deposit Address") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ChampagneGold, unfocusedBorderColor = ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = eth,
            onValueChange = { eth = it },
            label = { Text("Ethereum (ETH) Deposit Address") },
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ChampagneGold, unfocusedBorderColor = ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = bankWire,
            onValueChange = { bankWire = it },
            label = { Text("Institutional Bank Wire / IBAN / SWIFT") },
            minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ChampagneGold, unfocusedBorderColor = ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                onSaveConfig(
                    brokerConfig.copy(
                        usdtTrc20Address = trc20,
                        usdtErc20Address = erc20,
                        btcWalletAddress = btc,
                        ethWalletAddress = eth,
                        bankWireDetails = bankWire
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBg),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("admin_save_wallets_button")
        ) {
            Text("Publish Deposit Wallet Addresses", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AdminStatusBadge(
    status: String,
    isPositive: Boolean,
    isPending: Boolean
) {
    val fg = when {
        isPositive -> EmeraldAlpha
        isPending -> ChampagneGold
        else -> CrimsonLoss
    }
    val bg = when {
        isPositive -> EmeraldAlphaDim
        isPending -> ChampagneGoldDim
        else -> CrimsonLossDim
    }
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun AdminEmptyStateCard(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp)
        )
    }
}
