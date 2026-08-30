package com.sandeepraghav.passvault.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sandeepraghav.passvault.session.LockState
import com.sandeepraghav.passvault.ui.category.CategoryScreen
import com.sandeepraghav.passvault.ui.entry.AddEditEntryScreen
import com.sandeepraghav.passvault.ui.entry.EntryDetailScreen
import com.sandeepraghav.passvault.ui.home.HomeScreen
import com.sandeepraghav.passvault.ui.lock.LockScreen
import com.sandeepraghav.passvault.ui.recovery.RecoveryKeyRevealScreen
import com.sandeepraghav.passvault.ui.recovery.RecoveryKeyUnlockScreen
import com.sandeepraghav.passvault.ui.recovery.RecoveryScreen
import com.sandeepraghav.passvault.ui.rememberVaultApp
import com.sandeepraghav.passvault.ui.setup.SetupScreen
import com.sandeepraghav.passvault.ui.settings.SettingsScreen

@Composable
fun PassVaultNavGraph() {
    val app = rememberVaultApp()
    val navController = rememberNavController()
    val lockState by app.sessionManager.lockState.collectAsState()

    val startDestination = when (lockState) {
        LockState.NO_VAULT -> Routes.SETUP
        LockState.LOCKED -> Routes.LOCK
        LockState.UNLOCKED -> Routes.HOME
    }

    LaunchedEffect(lockState) {
        if (lockState == LockState.LOCKED && navController.currentDestination?.route != Routes.LOCK) {
            navController.navigate(Routes.LOCK) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {
            SetupScreen(onVaultCreated = {
                navController.navigate(Routes.recoveryKeyRevealAfterSetup()) { popUpTo(0) { inclusive = true } }
            })
        }

        composable(
            Routes.RECOVERY_KEY_REVEAL,
            arguments = listOf(navArgument("then") { type = NavType.StringType; defaultValue = "home" })
        ) { backStackEntry ->
            val then = backStackEntry.arguments?.getString("then") ?: "home"
            RecoveryKeyRevealScreen(onSaved = {
                if (then == "settings") {
                    navController.popBackStack()
                } else {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                }
            })
        }

        composable(Routes.LOCK) {
            LockScreen(
                onUnlocked = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                onForgotPassword = { navController.navigate(Routes.RECOVERY) },
                onUseRecoveryKey = { navController.navigate(Routes.RECOVERY_KEY_UNLOCK) }
            )
        }

        composable(Routes.RECOVERY) {
            RecoveryScreen(
                onRecovered = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.RECOVERY_KEY_UNLOCK) {
            RecoveryKeyUnlockScreen(
                onRecovered = { navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } } },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onOpenCategory = { id, name -> navController.navigate(Routes.category(id, name)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLocked = { },
                onRegenerateRecoveryKey = { navController.navigate(Routes.recoveryKeyRevealFromSettings()) }
            )
        }

        composable(
            Routes.CATEGORY,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.LongType },
                navArgument("categoryName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
            val categoryName = backStackEntry.arguments?.getString("categoryName") ?: ""
            CategoryScreen(
                categoryId = categoryId,
                categoryName = categoryName,
                onBack = { navController.popBackStack() },
                onOpenEntry = { entryId -> navController.navigate(Routes.entryDetail(entryId)) },
                onAddEntry = { navController.navigate(Routes.entryEditNew(categoryId)) }
            )
        }

        composable(
            Routes.ENTRY_DETAIL,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: 0L
            EntryDetailScreen(
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onEdit = { categoryId, id -> navController.navigate(Routes.entryEditExisting(categoryId, id)) }
            )
        }

        composable(
            Routes.ENTRY_EDIT,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.LongType },
                navArgument("entryId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) { backStackEntry ->
            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
            val entryId = backStackEntry.arguments?.getLong("entryId")?.takeIf { it >= 0 }
            AddEditEntryScreen(
                categoryId = categoryId,
                entryId = entryId,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}
