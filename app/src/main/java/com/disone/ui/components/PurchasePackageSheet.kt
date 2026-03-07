package com.disone.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    onLoadPackages: () -> Unit,
    onPurchase: (SubscriptionPackage) -> Unit,
    onRetryVerification: () -> Unit,
    onClearPendingVerification: () -> Unit,
    onDismiss: () -> Unit,
    canPurchase: Boolean,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("Subscribe to watch", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable(enabled = canPurchase && !purchaseInProgress) {
                                    if (canPurchase && !purchaseInProgress) onPurchase(pkg)
                                },
                            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(pkg.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                                pkg.description?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                                Text(
                                    "${formatSolAmount(pkg.amountSol)} SOL · ${pkg.durationMonths} month${if (pkg.durationMonths > 1) "s" else ""}",
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            if (packages.isNotEmpty() && !packagesLoading) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    }
}
