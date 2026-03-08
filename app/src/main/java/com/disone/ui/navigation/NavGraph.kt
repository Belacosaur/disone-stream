package com.disone.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.disone.ui.screens.AddonManagerScreen
import com.disone.ui.screens.DiscoveryScreen
import com.disone.ui.screens.FeaturedScreen
import com.disone.ui.screens.LibraryScreen
import com.disone.ui.screens.MainScreen
import com.disone.ui.screens.PlayerScreen
import com.disone.ui.screens.AccountScreen
import com.disone.ui.screens.SettingsScreen
import com.disone.ui.screens.EpisodesScreen
import com.disone.ui.screens.StreamSelectorScreen
import com.disone.ui.screens.TitleDetailScreen
import com.disone.ui.screens.WalletConnectScreen
import com.disone.core.utils.parseItemId
import java.net.URLDecoder
import java.net.URLEncoder

/** Bottom nav tab routes */
enum class BottomNav(
    val route: String,
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Featured("featured", "Featured", Icons.Filled.Star, Icons.Outlined.Star),
    Discovery("discovery", "Discovery", Icons.Filled.Explore, Icons.Outlined.Explore),
    Library("library", "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    Account("account", "Account", Icons.Filled.Person, Icons.Outlined.Person)
}

sealed class Screen(val route: String) {
    object WalletConnect : Screen("wallet_connect")
    object Featured : Screen("featured")
    object Discovery : Screen("discovery")
    object Library : Screen("library")
    object Account : Screen("account")
    object Settings : Screen("settings")
    object TitleDetail : Screen("title_detail/{type}/{id}") {
        fun createRoute(type: String, id: String) =
            "title_detail/${URLEncoder.encode(type, "UTF-8")}/${URLEncoder.encode(id, "UTF-8")}"
    }
    object StreamSelector : Screen("stream_selector/{itemId}") {
        fun createRoute(itemId: String) = "stream_selector/${URLEncoder.encode(itemId, "UTF-8")}"
    }
    object Episodes : Screen("episodes/{itemId}") {
        fun createRoute(itemId: String) = "episodes/${URLEncoder.encode(itemId, "UTF-8")}"
    }
    object Player : Screen("player/{magnetEncoded}/{fileIndex}") {
        fun createRoute(magnet: String, fileIndex: Int) =
            "player/${URLEncoder.encode(magnet, "UTF-8")}/$fileIndex"
    }
    object PlayerStream : Screen("player_stream")
    object AddonManager : Screen("addon_manager")
    object Search : Screen("search")
}

@Composable
fun NavGraph(
    navController: NavHostController,
    pendingPlayHolder: com.disone.core.playback.PendingPlayHolder,
    initialDeepLinkRoute: String? = null,
    onDeepLinkConsumed: () -> Unit = {}
) {
    var hideHeaderByFullscreen by remember { mutableStateOf(false) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(initialDeepLinkRoute) {
        if (initialDeepLinkRoute != null) {
            navController.navigate(initialDeepLinkRoute) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                launchSingleTop = true
            }
            onDeepLinkConsumed()
        }
    }

    LaunchedEffect(currentRoute) {
        if (currentRoute != Screen.PlayerStream.route && !currentRoute.orEmpty().startsWith("player/")) {
            hideHeaderByFullscreen = false
        }
    }

    MainScreen(
        navController = navController,
        hideHeaderByFullscreen = hideHeaderByFullscreen,
        onSearchClick = { navController.navigate(Screen.Search.route) }
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.WalletConnect.route
        ) {
            composable(Screen.WalletConnect.route) {
                WalletConnectScreen(
                    onConnected = {
                        navController.navigate(Screen.Featured.route) {
                            popUpTo(Screen.WalletConnect.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Featured.route) {
                FeaturedScreen(
                    onItemClick = { type, id ->
                        navController.navigate(Screen.TitleDetail.createRoute(type, id))
                    }
                )
            }

            composable(Screen.Discovery.route) {
                DiscoveryScreen(
                    onItemClick = { item ->
                        val (type, id) = parseItemId(item.id)
                        navController.navigate(Screen.TitleDetail.createRoute(type, id))
                    }
                )
            }

            composable(Screen.Library.route) {
                LibraryScreen(
                    onItemClick = { type, id ->
                        navController.navigate(Screen.TitleDetail.createRoute(type, id))
                    }
                )
            }

            composable(Screen.Account.route) {
                AccountScreen(
                    onSettingsClick = { navController.navigate(Screen.Settings.route) },
                    onSignOut = {
                        navController.navigate(Screen.WalletConnect.route) {
                            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                Screen.TitleDetail.route,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("id") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "movie"
                val id = backStackEntry.arguments?.getString("id") ?: ""
                val itemId = com.disone.core.utils.toItemId(type, id)
                TitleDetailScreen(
                    type = type,
                    id = id,
                    onWatch = {
                        if (type == "series") {
                            navController.navigate(Screen.Episodes.createRoute(itemId))
                        } else {
                            navController.navigate(Screen.StreamSelector.createRoute(itemId))
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
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
                    onPlayStream = { stream, resumeFromMs ->
                        pendingPlayHolder.setPending(stream, resumeFromMs)
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

            composable(
                Screen.Episodes.route,
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
                EpisodesScreen(
                    itemId = itemId,
                    onPlayStream = { stream, resumeFromMs ->
                        pendingPlayHolder.setPending(stream, resumeFromMs)
                        navController.navigate(Screen.PlayerStream.route)
                    },
                    onBack = { navController.popBackStack() }
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
                    },
                    onFullscreenChange = { hideHeaderByFullscreen = it }
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
                    },
                    onFullscreenChange = { hideHeaderByFullscreen = it }
                )
            }

            composable(Screen.AddonManager.route) {
                AddonManagerScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Search.route) {
                com.disone.ui.screens.SearchScreen(
                    onItemClick = { type, id ->
                        navController.navigate(Screen.TitleDetail.createRoute(type, id))
                    },
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
