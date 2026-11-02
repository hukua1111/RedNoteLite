package com.rednote.lite.ui.nav

import android.net.Uri
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
import com.rednote.lite.ui.user.UserProfileScreen

object Routes {
    const val MAIN = "main"
    const val LOGIN = "login"
    const val DETAIL = "detail"
    const val LOGS = "logs"
    const val USER = "user"
}

@Composable
fun AppNavHost(navController: NavHostController) {
    // xsec_token / 头像地址都可能含 + / = & ?，一律编码后再拼进路由
    fun openNote(noteId: String, xsecToken: String) {
        navController.navigate("${Routes.DETAIL}/${Uri.encode(noteId)}?token=${Uri.encode(xsecToken)}")
    }

    fun openUser(userId: String, nickname: String, avatar: String) {
        navController.navigate(
            "${Routes.USER}/${Uri.encode(userId)}" +
                "?nickname=${Uri.encode(nickname)}&avatar=${Uri.encode(avatar)}",
        )
    }

    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                onOpenNote = { noteId, xsecToken -> openNote(noteId, xsecToken) },
                onOpenUser = { userId, nickname, avatar -> openUser(userId, nickname, avatar) },
                onLogin = { navController.navigate(Routes.LOGIN) },
                onOpenLogs = { navController.navigate(Routes.LOGS) },
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(onClose = { navController.popBackStack() })
        }
        composable(Routes.LOGS) {
            LogScreen(onBack = { navController.popBackStack() })
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
                onOpenUser = { userId, nickname, avatar -> openUser(userId, nickname, avatar) },
            )
        }
        composable(
            route = "${Routes.USER}/{userId}?nickname={nickname}&avatar={avatar}",
            arguments = listOf(
                navArgument("userId") { type = NavType.StringType },
                navArgument("nickname") { type = NavType.StringType; defaultValue = "" },
                navArgument("avatar") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            UserProfileScreen(
                userId = entry.arguments?.getString("userId").orEmpty(),
                nickname = entry.arguments?.getString("nickname").orEmpty(),
                avatar = entry.arguments?.getString("avatar").orEmpty(),
                onBack = { navController.popBackStack() },
                onOpenNote = { noteId, token -> openNote(noteId, token) },
            )
        }
    }
}
