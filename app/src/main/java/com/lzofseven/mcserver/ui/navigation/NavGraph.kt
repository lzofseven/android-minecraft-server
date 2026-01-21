package com.lzofseven.mcserver.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lzofseven.mcserver.ui.screens.config.ConfigScreen
import com.lzofseven.mcserver.ui.screens.console.ConsoleScreen
import com.lzofseven.mcserver.ui.screens.createserver.CreateServerScreen
import com.lzofseven.mcserver.ui.screens.dashboard.DashboardScreen
import com.lzofseven.mcserver.ui.screens.library.LibraryScreen
import com.lzofseven.mcserver.ui.screens.mods.ModsScreen
import com.lzofseven.mcserver.ui.screens.players.PlayersScreen
import com.lzofseven.mcserver.ui.screens.serverlist.ServerListScreen

sealed class Screen(val route: String) {
    object ServerList : Screen("server_list")
    object CreateServer : Screen("create_server")
    object Dashboard : Screen("dashboard/{serverId}") {
        fun createRoute(serverId: String) = "dashboard/$serverId"
    }
    object Config : Screen("config/{serverId}") {
        fun createRoute(serverId: String) = "config/$serverId"
    }
    object Mods : Screen("mods/{serverId}")
    object Library : Screen("library/{serverId}") {
        fun createRoute(serverId: String) = "library/$serverId"
    }
    object Console : Screen("console/{serverId}") {
        fun createRoute(serverId: String) = "console/$serverId"
    }
    object Players : Screen("players/{serverId}") {
        fun createRoute(serverId: String) = "players/$serverId"
    }
    object ServerManagement : Screen("server_management/{serverId}") {
        fun createRoute(serverId: String) = "server_management/$serverId"
    }
}

@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.ServerList.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.ServerList.route) {
            ServerListScreen(navController = navController)
        }
        
        composable(Screen.CreateServer.route) {
            CreateServerScreen(navController = navController)
        }

        composable(
            route = Screen.Dashboard.route,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) {
            DashboardScreen(navController = navController)
        }
        
        composable(
            route = Screen.Config.route,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) {
            ConfigScreen(navController = navController)
        }
        
        composable(Screen.Mods.route) {
            ModsScreen(navController = navController)
        }
        
        composable(
            route = Screen.Library.route,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) {
            LibraryScreen(navController = navController)
        }
        
        composable(
            route = Screen.Console.route,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) {
            ConsoleScreen(navController = navController)
        }
        
        composable(
            route = Screen.Players.route,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) {
            PlayersScreen(navController = navController)
        }

        composable(
            route = Screen.ServerManagement.route,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType })
        ) {
            com.lzofseven.mcserver.ui.screens.management.ServerManagementScreen(navController = navController)
        }
    }
}
