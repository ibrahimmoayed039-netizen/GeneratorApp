package com.example.generatorapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.generatorapp.ui.screens.AboutScreen
import com.example.generatorapp.ui.screens.BillingScreen
import com.example.generatorapp.ui.screens.ExpensesScreen
import com.example.generatorapp.ui.screens.GeneratorDetailScreen
import com.example.generatorapp.ui.screens.GeneratorsScreen
import com.example.generatorapp.ui.screens.HomeScreen
import com.example.generatorapp.ui.screens.LatePaymentsScreen
import com.example.generatorapp.ui.screens.MaintenanceAlertsScreen
import com.example.generatorapp.ui.screens.PaymentStatusScreen
import com.example.generatorapp.ui.screens.ProfitReportScreen
import com.example.generatorapp.ui.screens.RemindersScreen
import com.example.generatorapp.ui.screens.SettingsScreen
import com.example.generatorapp.ui.screens.StatementScreen
import com.example.generatorapp.ui.screens.SubscribersScreen

object Routes {
    const val HOME = "home"
    const val SUBSCRIBERS = "subscribers"
    const val GENERATORS = "generators"
    const val GENERATOR_DETAIL = "generator_detail/{generatorId}"
    const val BILLING = "billing"
    const val SETTINGS = "settings"
    const val STATEMENT = "statement"
    const val LATE_PAYMENTS = "late_payments"
    const val EXPENSES = "expenses"
    const val PROFIT_REPORT = "profit_report"
    const val PAYMENT_STATUS = "payment_status"
    const val MAINTENANCE_ALERTS = "maintenance_alerts"
    const val REMINDERS = "reminders"
    const val ABOUT = "about"

    fun generatorDetail(generatorId: Long) = "generator_detail/$generatorId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSubscribers = { navController.navigate(Routes.SUBSCRIBERS) },
                onOpenGenerators = { navController.navigate(Routes.GENERATORS) },
                onOpenBilling = { navController.navigate(Routes.BILLING) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenStatement = { navController.navigate(Routes.STATEMENT) },
                onOpenLatePayments = { navController.navigate(Routes.LATE_PAYMENTS) },
                onOpenExpenses = { navController.navigate(Routes.EXPENSES) },
                onOpenProfitReport = { navController.navigate(Routes.PROFIT_REPORT) },
                onOpenPaymentStatus = { navController.navigate(Routes.PAYMENT_STATUS) },
                onOpenMaintenanceAlerts = { navController.navigate(Routes.MAINTENANCE_ALERTS) },
                onOpenReminders = { navController.navigate(Routes.REMINDERS) }
            )
        }
        composable(Routes.SUBSCRIBERS) {
            SubscribersScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.GENERATORS) {
            GeneratorsScreen(
                onBack = { navController.popBackStack() },
                onOpenGenerator = { id -> navController.navigate(Routes.generatorDetail(id)) }
            )
        }
        composable(
            Routes.GENERATOR_DETAIL,
            arguments = listOf(navArgument("generatorId") { type = NavType.LongType })
        ) { backStackEntry ->
            val generatorId = backStackEntry.arguments?.getLong("generatorId") ?: 0L
            GeneratorDetailScreen(generatorId = generatorId, onBack = { navController.popBackStack() })
        }
        composable(Routes.BILLING) {
            BillingScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.STATEMENT) {
            StatementScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.LATE_PAYMENTS) {
            LatePaymentsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.EXPENSES) {
            ExpensesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PROFIT_REPORT) {
            ProfitReportScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.PAYMENT_STATUS) {
            PaymentStatusScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.MAINTENANCE_ALERTS) {
            MaintenanceAlertsScreen(
                onBack = { navController.popBackStack() },
                onOpenGenerator = { id -> navController.navigate(Routes.generatorDetail(id)) }
            )
        }
        composable(Routes.REMINDERS) {
            RemindersScreen(onBack = { navController.popBackStack() })
        }
    }
}
