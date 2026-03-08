package com.disone

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.disone.core.playback.PendingPlayHolder
import com.disone.ui.LocalActivityResultSender
import com.disone.ui.navigation.NavGraph
import com.disone.ui.navigation.Screen
import com.disone.ui.theme.DisoneTheme
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var pendingPlayHolder: PendingPlayHolder

    private val deepLinkRouteState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkRouteState.value = parseDeepLinkToRoute(intent?.data)
        // ActivityResultSender must register before activity reaches STARTED - create in onCreate
        val activityResultSender = ActivityResultSender(this)
        setContent {
            DisoneTheme {
                CompositionLocalProvider(LocalActivityResultSender provides activityResultSender) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()
                        NavGraph(
                            navController = navController,
                            pendingPlayHolder = pendingPlayHolder,
                            initialDeepLinkRoute = deepLinkRouteState.value,
                            onDeepLinkConsumed = { deepLinkRouteState.value = null }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkRouteState.value = parseDeepLinkToRoute(intent?.data)
    }

    private fun parseDeepLinkToRoute(uri: Uri?): String? {
        if (uri == null) return null
        val path = uri.path ?: return null
        val segments = path.trimStart('/').split('/').filter { it.isNotEmpty() }
        return when {
            segments.isEmpty() -> Screen.Featured.route
            segments.size >= 2 && (segments[0] == "movie" || segments[0] == "series") -> {
                val type = segments[0]
                val id = segments[1]
                Screen.TitleDetail.createRoute(type, id)
            }
            else -> Screen.Featured.route
        }
    }
}
