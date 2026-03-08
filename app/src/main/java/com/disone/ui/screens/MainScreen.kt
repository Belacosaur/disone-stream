package com.disone.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.disone.ui.components.DisoneHeader
import com.disone.ui.navigation.BottomNav
import com.disone.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    hideHeaderByFullscreen: Boolean = false,
    onSearchClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in listOf(
        BottomNav.Featured.route,
        BottomNav.Discovery.route,
        BottomNav.Library.route,
        BottomNav.Account.route
    )
    val isPlayerScreen = currentDestination?.route == Screen.PlayerStream.route ||
        currentDestination?.route?.startsWith("player/") == true
    val showHeader = currentDestination?.route != Screen.WalletConnect.route &&
        !isPlayerScreen &&
        !hideHeaderByFullscreen
    val showBackButton = currentDestination?.route !in listOf(
        BottomNav.Featured.route,
        BottomNav.Discovery.route,
        BottomNav.Library.route,
        BottomNav.Account.route
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (showHeader) {
                DisoneHeader(
                    onSearchClick = onSearchClick,
                    onBackClick = if (showBackButton) {{ navController.popBackStack() }} else null
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomNav.entries.forEach { screen ->
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (currentDestination?.route == screen.route) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .consumeWindowInsets(WindowInsets.systemBars)
        ) {
            content()
        }
    }
}
