package com.jw.autorecord.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jw.autorecord.ui.calendar.CalendarScreen
import com.jw.autorecord.ui.home.HomeScreen
import com.jw.autorecord.ui.recordings.RecordingsScreen
import com.jw.autorecord.ui.schedule.ScheduleScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Home : Screen("home", "홈", Icons.Default.Home)
    data object Schedule : Screen("schedule", "시간표", Icons.Default.TableChart)
    data object Calendar : Screen("calendar", "달력", Icons.Default.CalendarMonth)
    data object Recordings : Screen("recordings", "녹음", Icons.Default.LibraryMusic)
}

val screens = listOf(Screen.Home, Screen.Schedule, Screen.Calendar, Screen.Recordings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, fontSize = 11.sp) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(Screen.Home.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Schedule.route) { ScheduleScreen() }
            composable(Screen.Calendar.route) { CalendarScreen() }
            composable(Screen.Recordings.route) { RecordingsScreen() }
        }
    }
}
