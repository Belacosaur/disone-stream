package com.disone.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.disone.ui.screens.AddonManagerScreen
import com.disone.ui.screens.CatalogueScreen
import com.disone.ui.screens.DiscoveryScreen
import com.disone.ui.screens.LibraryScreen
import com.disone.ui.screens.MainScreen
import com.disone.ui.screens.PlayerScreen
import com.disone.ui.screens.SettingsScreen
import com.disone.ui.screens.StreamSelectorScreen
import com.disone.ui.screens.WalletConnectScreen
import java.net.URLDecoder
import java.net.URLEncoder

/** Bottom nav tab routes */
enum class BottomNav(
    val route: String,
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Discovery("discovery", "Discovery", Icons.Filled.Explore, Icons.Outlined.Explore),
    Catalogue("catalogue", "Catalogue", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
    Library("library", "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    Settings("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

sealed class Screen(val route: String) {
    object WalletConnect : Screen("wallet_connect")
    object Discovery : Screen("discovery")
    object Catalogue : Screen("catalogue")
    object Library : Screen("library")
    object Settings : Screen("settings")
    object StreamSelector : Screen("stream_selector/{itemId}") {
        fun createRoute(itemId: String) = "stream_selector/${URLEncoder.encode(itemId, "UTF-8")}"
    }
    object Player : Screen("player/{magnetEncoded}/{fileIndex}") {
        fun createRoute(magnet: String, fileIndex: Int) =
            "player/${URLEncoder.encode(magnet, "UTF-8")}/$fileIndex"
    }
    object PlayerStream : Screen("player_stream")
    object AddonManager : Screen("addon_manager")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    pendingPlayHolder: com.disone.core.playback.PendingPlayHolder
) {
    MainScreen(navController = navController) {
        NavHost(
            navController = navController,
            startDestination = Screen.WalletConnect.route
        ) {
            composable(Screen.WalletConnect.route) {
                WalletConnectScreen(
                    onConnected = {
                        navController.navigate(Screen.Discovery.route) {
                            popUpTo(Screen.WalletConnect.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Discovery.route) {
                DiscoveryScreen(
                    onItemClick = { item -> navController.navigate(Screen.StreamSelector.createRoute(item.id)) }
                )
            }

            composable(Screen.Catalogue.route) {
                CatalogueScreen()
            }

            composable(Screen.Library.route) {
                LibraryScreen()
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onAddonManagerClick = { navController.navigate(Screen.AddonManager.route) }
                )
            }

            composable(
                Screen.StreamSelector.route,
                arguments = listOf(
                    navArgument("itemId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val itemIdRaw = backStackEntry.arguments?.getString("itemId") ?: ""
                val itemId = try {
                    URLDecoder.decode(itemIdRaw, "UTF-8")
                } catch (_: Exception) {
                    itemIdRaw
                }
                StreamSelectorScreen(
                    itemId = itemId,
                    onPlayStream = { stream ->
                        pendingPlayHolder.setPending(stream)
                        navController.navigate(Screen.PlayerStream.route)
                    },
                    onBack = { navController.popBackStack() },
                    onDiscover = {
                        navController.navigate(Screen.Discovery.route) {
                            popUpTo(Screen.Discovery.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.PlayerStream.route) {
                PlayerScreen(
                    magnet = null,
                    fileIndex = 0,
                    usePendingStream = true,
                    onBack = { navController.popBackStack() },
                    onDiscover = {
                        navController.navigate(Screen.Discovery.route) {
                            popUpTo(Screen.Discovery.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                Screen.Player.route,
                arguments = listOf(
                    navArgument("magnetEncoded") { type = NavType.StringType },
                    navArgument("fileIndex") { type = NavType.IntType; defaultValue = 0 }
                )
            ) { backStackEntry ->
                val magnetEncoded = backStackEntry.arguments?.getString("magnetEncoded") ?: ""
                val magnet = URLDecoder.decode(magnetEncoded, "UTF-8")
                val fileIndex = backStackEntry.arguments?.getInt("fileIndex") ?: 0
                PlayerScreen(
                    magnet = magnet,
                    fileIndex = fileIndex,
                    usePendingStream = false,
                    onBack = { navController.popBackStack() },
                    onDiscover = {
                        navController.navigate(Screen.Discovery.route) {
                            popUpTo(Screen.Discovery.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.AddonManager.route) {
                AddonManagerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
