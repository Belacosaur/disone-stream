package com.disone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.disone.core.playback.PendingPlayHolder
import com.disone.ui.LocalActivityResultSender
import com.disone.ui.navigation.NavGraph
import com.disone.ui.theme.DisoneTheme
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pendingPlayHolder: PendingPlayHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // ActivityResultSender must register before activity reaches STARTED - create in onCreate
        val activityResultSender = ActivityResultSender(this)
        setContent {
            DisoneTheme {
                CompositionLocalProvider(LocalActivityResultSender provides activityResultSender) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        NavGraph(
                            navController = navController,
                            pendingPlayHolder = pendingPlayHolder
                        )
                    }
                }
            }
        }
    }
}
