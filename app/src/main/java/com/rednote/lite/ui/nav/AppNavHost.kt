package com.rednote.lite.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rednote.lite.ui.detail.NoteDetailScreen
import com.rednote.lite.ui.login.LoginScreen
import com.rednote.lite.ui.log.LogScreen
import com.rednote.lite.ui.main.MainScreen
import com.rednote.lite.ui.settings.SettingsScreen
import android.net.Uri

object Routes {
    const val MAIN = "main"
    const val LOGIN = "login"
    const val DETAIL = "detail"
    const val LOGS = "logs"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenNote = { noteId, xsecToken ->
                    // xsec_token 是 base64，可能含 + / = 甚至 & ，不编码会把路由参数撑坏
                    val token = Uri.encode(xsecToken)
                    navController.navigate("${Routes.DETAIL}/${Uri.encode(noteId)}?token=$token")
                },
                onLogin = { navController.navigate(Routes.LOGIN) },
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(onClose = { navController.popBackStack() })
        }
        composable(Routes.LOGS) {
            LogScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenLogs = { navController.navigate(Routes.LOGS) },
            )
        }
        composable(
            route = "${Routes.DETAIL}/{noteId}?token={token}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.StringType },
                navArgument("token") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            NoteDetailScreen(
                noteId = entry.arguments?.getString("noteId").orEmpty(),
                xsecToken = entry.arguments?.getString("token").orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
}
