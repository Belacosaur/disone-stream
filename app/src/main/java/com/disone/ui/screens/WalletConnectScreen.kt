package com.disone.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.disone.ui.LocalActivityResultSender
import com.disone.ui.components.PlanBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletConnectScreen(
    onConnected: () -> Unit,
    viewModel: WalletConnectViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val activityResultSender = LocalActivityResultSender.current

    LaunchedEffect(state) {
        if (state is WalletConnectState.Connected) {
            onConnected()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Disone") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "Solana Streaming",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Connect your wallet to continue",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            when (val s = state) {
                is WalletConnectState.Connected -> {
                    PlanBadge(plan = s.plan)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        s.wallet.take(8) + "..." + s.wallet.takeLast(8),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    Button(
                        onClick = {
                            when {
                                activityResultSender != null -> viewModel.connectWallet(activityResultSender)
                                else -> viewModel.setError("Unable to connect: activity not available")
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Connect Wallet")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { viewModel.useOfflineMode() }
                    ) {
                        Text("Continue without wallet (dev)")
                    }
                }
            }

            if (state is WalletConnectState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    (state as WalletConnectState.Error).message,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
