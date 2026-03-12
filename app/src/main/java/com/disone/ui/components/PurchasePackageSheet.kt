package com.disone.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.disone.core.models.SubscriptionPackage

private fun formatSolAmount(amount: String): String =
    amount.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: amount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasePackageSheet(
    packages: List<SubscriptionPackage>,
    packagesLoading: Boolean,
    recipientAddress: String?,
    pendingVerification: Pair<String, String>?,
    purchaseInProgress: Boolean,
    /** When non-null, tx is ready – show confirm payment dialog for this package. */
    preparedPackage: SubscriptionPackage?,
    /** Package we're currently fetching tx for (show spinner on this card). */
    preparingPackage: SubscriptionPackage? = null,
    onPrepare: (SubscriptionPackage) -> Unit,
    onPay: () -> Unit,
    onClearPrepared: () -> Unit,
    onRetryVerification: () -> Unit,
    onClearPendingVerification: () -> Unit,
    onDismiss: () -> Unit,
    canPurchase: Boolean,
) {
    // Cooldown: Pay button only becomes clickable 400ms after prepare completes.
    // Prevents the same tap that triggered prepare from accidentally firing Pay
    // when prepare is fast and the button appears under the user's finger.
    var payButtonReady by remember { mutableStateOf(false) }
    LaunchedEffect(preparedPackage) {
        if (preparedPackage != null) {
            payButtonReady = false
            delay(400)
            payButtonReady = true
        } else {
            payButtonReady = false
        }
    }

    if (preparedPackage != null) {
        Dialog(
            onDismissRequest = { if (!purchaseInProgress) onClearPrepared() }
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Confirm payment", style = MaterialTheme.typography.titleLarge)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                    Text(preparedPackage.name, style = MaterialTheme.typography.titleMedium)
                    Text("${formatSolAmount(preparedPackage.amountSol)} SOL", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
                    if (purchaseInProgress) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                            Text("Verifying payment…", style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        Button(
                            onClick = onPay,
                            enabled = canPurchase && payButtonReady,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Confirm Payment") }
                    }
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = onClearPrepared,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Choose different plan") }
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = {
        onClearPrepared()
        onDismiss()
    }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("Subscribe to watch", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            if (canPurchase && packages.isNotEmpty()) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Tap \"Select\" on a plan. A confirmation dialog will appear, then tap \"Confirm Payment\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            when {
                packagesLoading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                packages.isEmpty() -> {
                    Text(
                        "Unable to load plans. Check your connection and try again.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    if (recipientAddress.isNullOrBlank()) {
                        Text(
                            "Payment setup in progress. Plans will be available soon.",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.error
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (pendingVerification != null && !purchaseInProgress) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Payment sent! Verification is delayed. Tap Retry in a few seconds.",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                                )
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = onClearPendingVerification) { Text("Dismiss") }
                                    Button(onClick = onRetryVerification) { Text("Retry verification") }
                                }
                            }
                        }
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                    }
                    packages.forEach { pkg ->
                        val isPrepared = preparedPackage?.packageKey == pkg.packageKey
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pkg.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                        pkg.description?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                                        Text(
                                            "${formatSolAmount(pkg.amountSol)} SOL · ${pkg.durationMonths} month${if (pkg.durationMonths > 1) "s" else ""}",
                                            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    when {
                                        purchaseInProgress && isPrepared -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        preparingPackage?.packageKey == pkg.packageKey -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        isPrepared -> Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        else -> Button(
                                            onClick = { onPrepare(pkg) },
                                            enabled = canPurchase && !purchaseInProgress
                                        ) { Text(if (purchaseInProgress) "Preparing…" else "Select") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (packages.isNotEmpty() && !packagesLoading) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = {
                    onClearPrepared()
                    onDismiss()
                }) { Text("Cancel") }
            }
        }
    }
}
