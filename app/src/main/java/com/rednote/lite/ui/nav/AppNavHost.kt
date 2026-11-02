package com.rednote.lite.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rednote.lite.ui.detail.NoteDetailScreen
import com.rednote.lite.ui.login.LoginScreen
import com.rednote.lite.ui.main.MainScreen

object Routes {
    const val MAIN = "main"
    const val LOGIN = "login"
    const val DETAIL = "detail"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenNote = { noteId, xsecToken ->
                    navController.navigate("${Routes.DETAIL}/$noteId?token=$xsecToken")
                },
                onLogin = { navController.navigate(Routes.LOGIN) },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(onClose = { navController.popBackStack() })
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
