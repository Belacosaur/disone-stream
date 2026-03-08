package com.disone.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.disone.R
import com.disone.core.auth.AuthState
import com.disone.core.models.SubscriptionPackage
import com.disone.ui.LocalActivityResultSender
import kotlinx.coroutines.launch
import java.math.BigDecimal

private fun formatSolAmount(amount: String): String =
    amount.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: amount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onSettingsClick: () -> Unit,
    onSignOut: () -> Unit = {},
    viewModel: AccountViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val packages by viewModel.packages.collectAsState()
    val recipientAddress by viewModel.recipientAddress.collectAsState()
    val packagesLoading by viewModel.packagesLoading.collectAsState()
    val purchaseInProgress by viewModel.purchaseInProgress.collectAsState()
    val pendingVerification by viewModel.pendingVerification.collectAsState()
    val purchaseConfirmation by viewModel.purchaseConfirmation.collectAsState()
    val activityResultSender = LocalActivityResultSender.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showPackagesSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showUsernameDialog by remember { mutableStateOf(false) }
    var usernameEdit by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes()
                }
                val filename = uri.lastPathSegment ?: "avatar.jpg"
                if (bytes != null && bytes.isNotEmpty()) {
                    viewModel.uploadAvatar(bytes, filename) { result ->
                        result.onSuccess {
                            scope.launch {
                                snackbarHostState.showSnackbar("Avatar updated")
                            }
                        }.onFailure { e ->
                            scope.launch {
                                snackbarHostState.showSnackbar(e.message ?: "Failed to upload avatar")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(e.message ?: "Failed to load image")
                }
            }
        }
    }

    LaunchedEffect(profile?.username) {
        usernameEdit = profile?.username ?: ""
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            when (authState) {
                is AuthState.Authenticated -> {
                    val auth = authState as AuthState.Authenticated

                    // Profile header card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Box(
                                    modifier = Modifier.size(100.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                            .clickable { imagePickerLauncher.launch("image/*") },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (profile?.avatarUrl != null) {
                                            AsyncImage(
                                                model = profile?.avatarUrl,
                                                contentDescription = "Profile photo",
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(R.drawable.defaultavatar),
                                                contentDescription = "Default avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    ) {
                                        Icon(
                                            Icons.Default.CameraAlt,
                                            contentDescription = "Change photo",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = profile?.username ?: (auth.wallet.take(6) + "..." + auth.wallet.takeLast(4)),
                                            style = MaterialTheme.typography.headlineSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        IconButton(
                                            onClick = {
                                                usernameEdit = profile?.username ?: ""
                                                showUsernameDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit username")
                                        }
                                    }
                                    if (profile?.username == null && auth.wallet.isNotEmpty()) {
                                        Text(
                                            text = "Tap edit to set a username",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = buildString {
                                            val pkg = profile?.packageName ?: (profile?.plan ?: auth.plan)
                                            append("Plan: $pkg")
                                            profile?.expiresAt?.let { exp ->
                                                try {
                                                    val expDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                        .parse(exp.take(10))
                                                    if (expDate != null) {
                                                        append(" · Renews ")
                                                        append(java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(expDate))
                                                    }
                                                } catch (_: Exception) { }
                                            }
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { showPackagesSheet = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                            ) {
                                Text("View subscription plans", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    ListItem(
                        headlineContent = { Text("Settings", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Addon Manager, Downloads, Cache") },
                        leadingContent = {
                            Icon(Icons.Default.Settings, contentDescription = null)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSettingsClick)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.signOut()
                            onSignOut()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text("Sign Out", color = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(48.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            painter = painterResource(R.drawable.logo),
                            contentDescription = null,
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Sign in to view your account",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(showPackagesSheet) {
        if (showPackagesSheet) viewModel.loadPackages()
    }

    if (showPackagesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPackagesSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text("Subscription Plans", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                when {
                    packagesLoading -> {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                    packages.isEmpty() -> {
                        Text(
                            "Unable to load plans. Check your connection and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        if (recipientAddress.isNullOrBlank()) {
                            Text(
                                "Payment setup in progress. Plans will be available soon.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        if (pendingVerification != null && !purchaseInProgress) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "Payment sent! Verification is delayed. Tap Retry in a few seconds.",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { viewModel.clearPendingVerification() }) {
                                            Text("Dismiss")
                                        }
                                        Button(onClick = {
                                            viewModel.retryPendingVerification { result ->
                                                result.onSuccess { showPackagesSheet = false }
                                                result.onFailure { e ->
                                                    scope.launch {
                                                        snackbarHostState.showSnackbar(e.message ?: "Retry failed")
                                                    }
                                                }
                                            }
                                        }) {
                                            Text("Retry verification")
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        val currentPackageKey = profile?.packageKey
                        val annualPackage = packages.find { it.packageKey == "annual" }
                        val hasMonthlySubscription = currentPackageKey == "monthly"
                        val canUpgradeToAnnual = hasMonthlySubscription && annualPackage != null

                        if (currentPackageKey != null && profile?.packageName != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Your plan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(profile?.packageName ?: "", style = MaterialTheme.typography.titleMedium)
                                    run {
                                        val formattedExpiry = profile?.expiresAt?.let { exp ->
                                            try {
                                                val expDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                                    .parse(exp.take(10))
                                                expDate?.let { java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(it) }
                                            } catch (_: Exception) { null }
                                        }
                                        if (formattedExpiry != null) {
                                            Text(
                                                "Renews $formattedExpiry",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            if (canUpgradeToAnnual) {
                                val upgradePkg = annualPackage!!
                                val canPurchase = !recipientAddress.isNullOrBlank() && activityResultSender != null
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(upgradePkg.name, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${formatSolAmount(upgradePkg.amountSol)} SOL for 12 months — same price as 10 months",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                if (activityResultSender != null && canPurchase && !purchaseInProgress) {
                                                    viewModel.purchasePackage(
                                                        activityResultSender,
                                                        upgradePkg
                                                    ) { result, canRetryVerification ->
                                                        result.onSuccess { showPackagesSheet = false }
                                                        result.onFailure { e ->
                                                            scope.launch {
                                                                if (canRetryVerification) {
                                                                    snackbarHostState.showSnackbar(
                                                                        "Payment sent but verification is delayed. Wait a few seconds and tap Retry.",
                                                                        withDismissAction = true
                                                                    )
                                                                } else {
                                                                    snackbarHostState.showSnackbar(e.message ?: "Purchase failed")
                                                                }
                                                            }
                                                        }
                                                    }
                                                } else if (activityResultSender == null) {
                                                    scope.launch { snackbarHostState.showSnackbar("Unable to connect wallet") }
                                                }
                                            },
                                            enabled = canPurchase && !purchaseInProgress
                                        ) {
                                            Text("Upgrade")
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        } else {
                            packages.forEach { pkg ->
                                val canPurchase = !recipientAddress.isNullOrBlank() && activityResultSender != null
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clickable(enabled = canPurchase && !purchaseInProgress) {
                                            if (activityResultSender != null) {
                                                viewModel.purchasePackage(
                                                    activityResultSender,
                                                    pkg
                                                ) { result, canRetryVerification ->
                                                    result.onSuccess {
                                                        showPackagesSheet = false
                                                    }.onFailure { e ->
                                                        scope.launch {
                                                            if (canRetryVerification) {
                                                                snackbarHostState.showSnackbar(
                                                                    "Payment sent but verification is delayed. Wait a few seconds and tap Retry.",
                                                                    withDismissAction = true
                                                                )
                                                            } else {
                                                                snackbarHostState.showSnackbar(e.message ?: "Purchase failed")
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                scope.launch { snackbarHostState.showSnackbar("Unable to connect wallet") }
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(pkg.name, style = MaterialTheme.typography.titleMedium)
                                        pkg.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                        Text(
                                            "${formatSolAmount(pkg.amountSol)} SOL · ${pkg.durationMonths} month${if (pkg.durationMonths > 1) "s" else ""}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (packages.isNotEmpty() && !packagesLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { showPackagesSheet = false }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    if (showUsernameDialog) {
        AlertDialog(
            onDismissRequest = { showUsernameDialog = false },
            title = { Text("Edit Username") },
            text = {
                Column {
                    OutlinedTextField(
                        value = usernameEdit,
                        onValueChange = { if (it.length <= 32) usernameEdit = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (usernameEdit.isNotBlank() && usernameEdit.length < 3) {
                        Text(
                            "At least 3 characters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = usernameEdit.trim()
                        if (trimmed.length >= 3) {
                            viewModel.updateUsername(trimmed) { result ->
                                result.onSuccess {
                                    scope.launch { snackbarHostState.showSnackbar("Username updated") }
                                    showUsernameDialog = false
                                }.onFailure { e ->
                                    scope.launch { snackbarHostState.showSnackbar(e.message ?: "Failed") }
                                }
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUsernameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    purchaseConfirmation?.let { conf ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPurchaseConfirmation() },
            title = { Text("Payment confirmed") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your upgrade to Disone Premium is complete.", style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Plan", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(conf.packageName, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (conf.amountSol.isNotBlank()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Amount", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${formatSolAmount(conf.amountSol)} SOL", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Transaction", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(conf.txSignature.take(8) + "…" + conf.txSignature.takeLast(8), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.dismissPurchaseConfirmation() }) {
                    Text("Done")
                }
            }
        )
    }
}
