package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BrokerConfig
import com.example.data.model.FundingMethod
import com.example.ui.components.decodeBase64ToImageBitmap
import com.example.ui.components.encodeUriToCompactBase64
import com.example.ui.components.formatUsdPrice
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldDim
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.EmeraldAlpha
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import kotlinx.coroutines.launch

@Composable
fun KycSubmissionDialog(
    initialFullName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitKyc: (
        fullLegalName: String,
        country: String,
        dateOfBirth: String,
        idType: String,
        idNumber: String,
        docImageBase64: String,
        onComplete: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fullLegalName by remember { mutableStateOf(initialFullName) }
    var country by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var idType by remember { mutableStateOf("Passport") }
    var idNumber by remember { mutableStateOf("") }
    var docImageBase64 by remember { mutableStateOf("") }
    var encodingImage by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                encodingImage = true
                localError = null
                encodeUriToCompactBase64(context, uri)
                    .onSuccess { docImageBase64 = it }
                    .onFailure { localError = it.localizedMessage ?: "Could not load ID photo" }
                encodingImage = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = ObsidianSurface,
        icon = { Icon(Icons.Default.Badge, contentDescription = null, tint = ChampagneGold) },
        title = {
            Text("Identity Verification (KYC)", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ChampagneGoldDim,
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All KYC submissions enter Pending status. System will review and approve within 24 hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChampagneGold
                        )
                    }
                }

                OutlinedTextField(
                    value = fullLegalName,
                    onValueChange = { fullLegalName = it; localError = null },
                    label = { Text("Full Legal Name (as on ID)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("kyc_name_input")
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it; localError = null },
                    label = { Text("Country of Residence") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("kyc_country_input")
                )
                OutlinedTextField(
                    value = dateOfBirth,
                    onValueChange = { dateOfBirth = it; localError = null },
                    label = { Text("Date of Birth (YYYY-MM-DD)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("kyc_dob_input")
                )

                Text("Government ID Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Passport", "National ID", "Driver's License").forEach { type ->
                        val selected = idType == type
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) EmeraldAlpha.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (selected) EmeraldAlpha else ObsidianOutline),
                            modifier = Modifier.clickable { idType = type }
                        ) {
                            Text(
                                text = type,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) EmeraldAlpha else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = idNumber,
                    onValueChange = { idNumber = it; localError = null },
                    label = { Text("ID / Passport Number") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("kyc_id_number_input")
                )

                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    border = BorderStroke(1.dp, if (docImageBase64.isNotBlank()) EmeraldAlpha else ObsidianOutline),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("kyc_upload_doc_button")
                ) {
                    if (encodingImage) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EmeraldAlpha)
                    } else {
                        Icon(
                            imageVector = if (docImageBase64.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = if (docImageBase64.isNotBlank()) EmeraldAlpha else ChampagneGold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (docImageBase64.isNotBlank()) "ID Photo Attached (Tap to Change)" else "Upload ID Document Photo",
                        color = if (docImageBase64.isNotBlank()) EmeraldAlpha else MaterialTheme.colorScheme.onSurface
                    )
                }

                val previewBitmap = remember(docImageBase64) { decodeBase64ToImageBitmap(docImageBase64) }
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "KYC Document Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                if (localError != null) {
                    Text(localError ?: "", style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && !encodingImage,
                onClick = {
                    when {
                        fullLegalName.trim().length < 2 -> localError = "Enter your full legal name."
                        country.trim().length < 2 -> localError = "Enter your country of residence."
                        dateOfBirth.trim().length < 4 -> localError = "Enter your date of birth."
                        idNumber.trim().length < 3 -> localError = "Enter a valid ID number."
                        docImageBase64.length < 10 -> localError = "Please upload your government ID photo."
                        else -> onSubmitKyc(fullLegalName, country, dateOfBirth, idType, idNumber, docImageBase64, onDismiss)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg),
                modifier = Modifier.testTag("kyc_submit_button")
            ) {
                Text(if (isSubmitting) "Submitting..." else "Submit KYC", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DepositProofDialog(
    brokerConfig: BrokerConfig,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitDeposit: (
        amountUsd: Double,
        method: FundingMethod,
        walletAddressUsed: String,
        txHash: String,
        proofImageBase64: String,
        onComplete: () -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMethod by remember { mutableStateOf(FundingMethod.USDT_TRC20) }
    var amountText by remember { mutableStateOf("") }
    var txHash by remember { mutableStateOf("") }
    var proofBase64 by remember { mutableStateOf("") }
    var encodingImage by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val walletAddress = brokerConfig.walletFor(selectedMethod)

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                encodingImage = true
                localError = null
                encodeUriToCompactBase64(context, uri)
                    .onSuccess { proofBase64 = it }
                    .onFailure { localError = it.localizedMessage ?: "Failed to read proof image" }
                encodingImage = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = ObsidianSurface,
        title = { Text("Deposit to Admin Wallet", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ChampagneGoldDim,
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Send funds to the official wallet below & upload proof. All deposits are Pending — system will review and approve within 24 hours.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChampagneGold
                        )
                    }
                }

                Text("Select Payment Network", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FundingMethod.entries.forEach { method ->
                        val active = selectedMethod == method
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) EmeraldAlpha.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (active) EmeraldAlpha else ObsidianOutline),
                            modifier = Modifier.clickable { selectedMethod = method }
                        ) {
                            Text(
                                text = method.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) EmeraldAlpha else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // Official Admin-Provided Wallet Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, EmeraldAlpha.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "OFFICIAL DEPOSIT WALLET (${selectedMethod.displayName})",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAlpha
                        )
                        Text(
                            text = selectedMethod.networkHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = walletAddress,
                                style = MonoPriceStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("ApexTrade Wallet", walletAddress))
                                    Toast.makeText(context, "Wallet address copied", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy wallet address", tint = ChampagneGold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; localError = null },
                    label = { Text("Deposit Amount (USD, min $10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("deposit_amount_input")
                )

                OutlinedTextField(
                    value = txHash,
                    onValueChange = { txHash = it; localError = null },
                    label = { Text("Transaction Hash / Wire Reference ID") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("deposit_txhash_input")
                )

                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    border = BorderStroke(1.dp, if (proofBase64.isNotBlank()) EmeraldAlpha else ObsidianOutline),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("upload_deposit_proof_button")
                ) {
                    if (encodingImage) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EmeraldAlpha)
                    } else {
                        Icon(
                            imageVector = if (proofBase64.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.CloudUpload,
                            contentDescription = null,
                            tint = if (proofBase64.isNotBlank()) EmeraldAlpha else ChampagneGold
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (proofBase64.isNotBlank()) "Proof Screenshot Attached (Change)" else "Upload Proof of Deposit Screenshot",
                        color = if (proofBase64.isNotBlank()) EmeraldAlpha else MaterialTheme.colorScheme.onSurface
                    )
                }

                val previewBitmap = remember(proofBase64) { decodeBase64ToImageBitmap(proofBase64) }
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap,
                        contentDescription = "Deposit Proof Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                if (localError != null) {
                    Text(localError ?: "", style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting && !encodingImage,
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    when {
                        amt == null || amt < 10.0 -> localError = "Enter a deposit amount of at least $10.00."
                        txHash.trim().length < 4 -> localError = "Enter your transaction hash or payment reference."
                        proofBase64.length < 10 -> localError = "Please upload a proof of deposit screenshot."
                        else -> onSubmitDeposit(amt, selectedMethod, walletAddress, txHash, proofBase64, onDismiss)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldAlpha, contentColor = ObsidianBg),
                modifier = Modifier.testTag("submit_deposit_proof_button")
            ) {
                Text(if (isSubmitting) "Submitting..." else "Submit Deposit", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun WithdrawalRequestDialog(
    availableCashUsd: Double,
    defaultHolderName: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmitWithdrawal: (
        amountUsd: Double,
        method: FundingMethod,
        destinationDetails: String,
        accountHolderName: String,
        onComplete: () -> Unit
    ) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(FundingMethod.USDT_TRC20) }
    var amountText by remember { mutableStateOf("") }
    var holderName by remember { mutableStateOf(defaultHolderName) }
    var destinationDetails by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        containerColor = ObsidianSurface,
        title = { Text("Request Withdrawal", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ChampagneGoldDim,
                    border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f))
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All withdrawals start in Pending status. System will review and approve within 24 hours, and payouts are processed by Admin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ChampagneGold
                        )
                    }
                }

                Text(
                    text = "Available to Withdraw: ${formatUsdPrice(availableCashUsd)}",
                    style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                    color = EmeraldAlpha
                )

                Text("Payout Method", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FundingMethod.entries.forEach { method ->
                        val active = selectedMethod == method
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (active) EmeraldAlpha.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (active) EmeraldAlpha else ObsidianOutline),
                            modifier = Modifier.clickable { selectedMethod = method }
                        ) {
                            Text(
                                text = method.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) EmeraldAlpha else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; localError = null },
                    label = { Text("Withdrawal Amount (USD, min $10)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("withdraw_amount_input")
                )

                OutlinedTextField(
                    value = holderName,
                    onValueChange = { holderName = it; localError = null },
                    label = { Text("Beneficiary / Account Holder Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("withdraw_holder_input")
                )

                OutlinedTextField(
                    value = destinationDetails,
                    onValueChange = { destinationDetails = it; localError = null },
                    label = {
                        Text(
                            if (selectedMethod == FundingMethod.BANK_WIRE) {
                                "Bank Name, IBAN / Account # & SWIFT Code"
                            } else {
                                "Receiving ${selectedMethod.displayName} Wallet Address"
                            }
                        )
                    },
                    minLines = 2,
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = EmeraldAlpha, unfocusedBorderColor = ObsidianOutline),
                    modifier = Modifier.fillMaxWidth().testTag("withdraw_destination_input")
                )

                if (localError != null) {
                    Text(localError ?: "", style = MaterialTheme.typography.bodySmall, color = CrimsonLoss)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSubmitting,
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    when {
                        amt == null || amt < 10.0 -> localError = "Minimum withdrawal is $10.00."
                        amt > availableCashUsd + 1e-6 -> localError = "Insufficient available cash balance."
                        holderName.trim().length < 2 -> localError = "Enter beneficiary / account holder name."
                        destinationDetails.trim().length < 5 -> localError = "Enter valid payout wallet or bank details."
                        else -> onSubmitWithdrawal(amt, selectedMethod, destinationDetails, holderName, onDismiss)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBg),
                modifier = Modifier.testTag("submit_withdraw_button")
            ) {
                Text(if (isSubmitting) "Submitting..." else "Submit Withdrawal", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isSubmitting, onClick = onDismiss) { Text("Cancel") }
        }
    )
}
