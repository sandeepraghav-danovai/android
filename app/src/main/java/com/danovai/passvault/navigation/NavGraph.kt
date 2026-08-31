package com.danovai.passvault.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.danovai.passvault.session.LockState
import com.danovai.passvault.ui.category.CategoryScreen
import com.danovai.passvault.ui.chooser.ChooserScreen
import com.danovai.passvault.ui.notes.NoteEditorScreen
import com.danovai.passvault.ui.notes.NotesListScreen
import com.danovai.passvault.ui.entry.AddEditEntryScreen
import com.danovai.passvault.ui.entry.EntryDetailScreen
import com.danovai.passvault.ui.home.HomeScreen
import com.danovai.passvault.ui.lock.LockScreen
import com.danovai.passvault.ui.recovery.RecoveryKeyRevealScreen
import com.danovai.passvault.ui.recovery.RecoveryKeyUnlockScreen
import com.danovai.passvault.ui.recovery.RecoveryScreen
import com.danovai.passvault.ui.rememberVaultApp
import com.danovai.passvault.ui.setup.SetupScreen
import com.danovai.passvault.ui.settings.SettingsScreen

@Composable
fun PassVaultNavGraph() {
    val app = rememberVaultApp()
    val navController = rememberNavController()
    val lockState by app.sessionManager.lockState.collectAsState()

    val startDestination = when (lockState) {
        LockState.NO_VAULT -> Routes.SETUP
        LockState.LOCKED -> Routes.LOCK
        LockState.UNLOCKED -> Routes.CHOOSER
    }

    LaunchedEffect(lockState) {
        val target = when (lockState) {
            LockState.LOCKED -> Routes.LOCK
            LockState.NO_VAULT -> Routes.SETUP
            LockState.UNLOCKED -> null
        }
        if (target != null && navController.currentDestination?.route != target) {
            navController.navigate(target) {
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
                    navController.navigate(Routes.CHOOSER) { popUpTo(0) { inclusive = true } }
                }
            })
        }

        composable(Routes.LOCK) {
            LockScreen(
                onUnlocked = { navController.navigate(Routes.CHOOSER) { popUpTo(0) { inclusive = true } } },
                onForgotPassword = { navController.navigate(Routes.RECOVERY) },
                onUseRecoveryKey = { navController.navigate(Routes.RECOVERY_KEY_UNLOCK) }
            )
        }

        composable(Routes.RECOVERY) {
            RecoveryScreen(
                onRecovered = { navController.navigate(Routes.CHOOSER) { popUpTo(0) { inclusive = true } } },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.RECOVERY_KEY_UNLOCK) {
            RecoveryKeyUnlockScreen(
                onRecovered = { navController.navigate(Routes.CHOOSER) { popUpTo(0) { inclusive = true } } },
                onCancel = { navController.popBackStack() }
            )
        }

        composable(Routes.CHOOSER) {
            ChooserScreen(
                onOpenSecrets = { navController.navigate(Routes.HOME) },
                onOpenNotes = { navController.navigate(Routes.NOTES) },
                onLock = { }
            )
        }

        composable(Routes.NOTES) {
            NotesListScreen(
                onBack = { navController.popBackStack() },
                onAddNote = { navController.navigate(Routes.noteEditNew()) },
                onEditNote = { id -> navController.navigate(Routes.noteEditExisting(id)) }
            )
        }

        composable(
            Routes.NOTE_EDIT,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType; defaultValue = -1L })
        ) { backStackEntry ->
            val noteId = backStackEntry.arguments?.getLong("noteId")?.takeIf { it >= 0 }
            NoteEditorScreen(noteId = noteId, onDone = { navController.popBackStack() })
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
                onRegenerateRecoveryKey = { navController.navigate(Routes.recoveryKeyRevealFromSettings()) },
                // resetVault flips the session to NO_VAULT, which the LaunchedEffect above
                // routes to setup; this just closes the settings screen behind it.
                onVaultReset = { navController.navigate(Routes.SETUP) { popUpTo(0) { inclusive = true } } }
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
