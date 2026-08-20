package com.school.attendance

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.school.attendance.data.AppPrefs
import com.school.attendance.ui.theme.SchoolAttendanceTheme
import com.school.attendance.ui.screens.AttendanceScreen
import com.school.attendance.ui.screens.BusesScreen
import com.school.attendance.ui.screens.DashboardScreen
import com.school.attendance.ui.screens.HolidaysScreen
import com.school.attendance.ui.screens.JoinScreen
import com.school.attendance.ui.screens.LicenseScreen
import com.school.attendance.ui.screens.LiveLocationScreen
import com.school.attendance.ui.screens.LoginScreen
import com.school.attendance.ui.screens.MastersScreen
import com.school.attendance.ui.screens.ParentDashboardScreen
import com.school.attendance.ui.screens.PayrollScreen
import com.school.attendance.ui.screens.ReportsScreen
import com.school.attendance.ui.screens.SelfAttendanceScreen
import com.school.attendance.ui.screens.SettingsScreen
import com.school.attendance.ui.screens.StudentsScreen
import com.school.attendance.ui.screens.SwitchToParentScreen
import com.school.attendance.ui.screens.TeacherAttendanceScreen
import com.school.attendance.ui.screens.TeachersScreen
import com.school.attendance.data.License
import androidx.navigation.NavType
import androidx.navigation.navArgument

/** Holds a `schoolapp://join?...` link's Uri from cold start until [com.school.attendance.ui.screens.JoinScreen]
 * consumes it — simpler than wiring Navigation-Compose's own deep-link argument passing for a
 * one-shot, app-wide value that's only ever read once right after launch. */
object PendingJoin {
    var uri: Uri? = null
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == android.content.Intent.ACTION_VIEW) PendingJoin.uri = intent?.data
        setContent {
            SchoolAttendanceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav()
                }
            }
        }
    }
}

object Routes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val MASTERS = "masters"
    const val TEACHERS = "teachers"
    const val STUDENTS = "students"
    const val ATTENDANCE = "attendance"
    const val TEACHER_ATTENDANCE = "teacherAttendance"
    const val HOLIDAYS = "holidays"
    const val REPORTS = "reports"
    const val PAYROLL = "payroll"
    const val SETTINGS = "settings"
    const val BUSES = "buses"
    const val LIVE_LOCATION = "liveLocation"
    const val SELF_ATTENDANCE = "selfAttendance"
    const val LICENSE = "license"
    const val JOIN = "join"
    const val PARENT_DASHBOARD = "parentDashboard"
    const val SWITCH_PARENT = "switchToParent"
    const val VIEW_AS_PARENT = "viewAsParent"
}

@Composable
fun AppNav() {
    val nav: NavHostController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    if (prefs.installDateMillis == 0L) prefs.installDateMillis = System.currentTimeMillis()
    // Trial/licensing only gates staff/admin use — a parent's own access never expires, and it's
    // checked at teacher sign-in, not cold start, so an expired trial never blocks a parent from
    // even reaching the Login screen to sign in.
    fun teacherLicenseDue() = License.dueMilestone(prefs.installDateMillis) > prefs.licensedMilestone
    val start = when {
        PendingJoin.uri != null -> Routes.JOIN
        prefs.isParentSession -> Routes.PARENT_DASHBOARD
        prefs.loggedInTeacherId > 0 && teacherLicenseDue() -> Routes.LICENSE
        prefs.loggedInTeacherId > 0 -> Routes.DASHBOARD
        else -> Routes.LOGIN
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.JOIN) {
            JoinScreen(
                uri = PendingJoin.uri,
                onReady = { PendingJoin.uri = null; nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } } }
            )
        }
        composable(Routes.LICENSE) {
            LicenseScreen(onActivated = { nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.LICENSE) { inclusive = true } } })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoggedIn = { nav.navigate(if (teacherLicenseDue()) Routes.LICENSE else Routes.DASHBOARD) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onParentLoggedIn = { nav.navigate(Routes.PARENT_DASHBOARD) { popUpTo(Routes.LOGIN) { inclusive = true } } }
            )
        }
        composable(Routes.PARENT_DASHBOARD) {
            ParentDashboardScreen(onLogout = {
                prefs.clearSession()
                nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpen = { route -> nav.navigate(route) },
                onLogout = {
                    prefs.clearSession()
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable(Routes.MASTERS) { MastersScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TEACHERS) { TeachersScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.STUDENTS) { StudentsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ATTENDANCE) { AttendanceScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.TEACHER_ATTENDANCE) { TeacherAttendanceScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.HOLIDAYS) { HolidaysScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.REPORTS) { ReportsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.PAYROLL) { PayrollScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.BUSES) { BusesScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.LIVE_LOCATION) { LiveLocationScreen(onBack = { nav.popBackStack() }, showHistory = true) }
        composable(Routes.SELF_ATTENDANCE) { SelfAttendanceScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SWITCH_PARENT) {
            SwitchToParentScreen(onBack = { nav.popBackStack() }, onPick = { studentId -> nav.navigate("${Routes.VIEW_AS_PARENT}/$studentId") })
        }
        composable(
            "${Routes.VIEW_AS_PARENT}/{studentId}",
            arguments = listOf(navArgument("studentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
            ParentDashboardScreen(onLogout = {}, studentIdOverride = studentId, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onSignedOut = {
                    prefs.clearSession()
                    nav.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
                }
            )
        }
    }
}
