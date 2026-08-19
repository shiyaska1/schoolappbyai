package com.school.attendance

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
import com.school.attendance.ui.screens.DashboardScreen
import com.school.attendance.ui.screens.HolidaysScreen
import com.school.attendance.ui.screens.LoginScreen
import com.school.attendance.ui.screens.MastersScreen
import com.school.attendance.ui.screens.PayrollScreen
import com.school.attendance.ui.screens.ReportsScreen
import com.school.attendance.ui.screens.SettingsScreen
import com.school.attendance.ui.screens.StudentsScreen
import com.school.attendance.ui.screens.TeacherAttendanceScreen
import com.school.attendance.ui.screens.TeachersScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}

@Composable
fun AppNav() {
    val nav: NavHostController = rememberNavController()
    val context = LocalContext.current
    val prefs = remember(context) { AppPrefs(context) }
    val start = if (prefs.loggedInTeacherId > 0) Routes.DASHBOARD else Routes.LOGIN

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = { nav.navigate(Routes.DASHBOARD) { popUpTo(Routes.LOGIN) { inclusive = true } } })
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
        composable(Routes.SETTINGS) { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
