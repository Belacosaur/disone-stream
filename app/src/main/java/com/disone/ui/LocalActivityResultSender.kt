package com.disone.ui

import androidx.compose.runtime.compositionLocalOf
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

/**
 * CompositionLocal providing [ActivityResultSender] for MWA wallet connection.
 * Must be created in Activity.onCreate (before STARTED) and provided here.
 */
val LocalActivityResultSender = compositionLocalOf<ActivityResultSender?> { null }
