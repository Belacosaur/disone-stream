package com.disone.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.disone.R
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
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.height(28.dp)
                        )
                        Text("Disone", style = MaterialTheme.typography.titleLarge)
                    }
                },
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
            Image(
                painter = painterResource(R.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(160.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
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
                    if (s.plan == "P2P") {
                        Spacer(modifier = Modifier.height(16.dp))
                        val access by viewModel.accessStatus.collectAsState()
                        Text(
                            if (access?.hasSeekerGenesisToken == true)
                                "You have 1 day of free viewing. After that, you'll need a subscription to continue."
                            else
                                "Subscribe to watch. Seeker device holders get 1 day free.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
                is WalletConnectState.Initializing,
                is WalletConnectState.Loading -> {
                    CircularProgressIndicator()
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
