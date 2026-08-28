package io.github.kolossus03.sonometro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.kolossus03.sonometro.ui.history.HistoryScreen
import io.github.kolossus03.sonometro.ui.meter.MeterScreen
import io.github.kolossus03.sonometro.ui.permissions.RecordAudioGate
import io.github.kolossus03.sonometro.ui.settings.SettingsScreen
import io.github.kolossus03.sonometro.ui.theme.SonometroTheme

private enum class Destination(
    val route: String,
    val label: String,
    val title: String,
    val icon: ImageVector,
) {
    METER("medidor", "Medidor", "Sonómetro", Icons.Rounded.Speed),
    HISTORY("historico", "Histórico", "Histórico", Icons.Rounded.Insights),
    SETTINGS("ajustes", "Ajustes", "Ajustes", Icons.Rounded.Tune),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SonometroTheme {
                SonometroRoot()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SonometroRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val current = Destination.entries.firstOrNull { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    } ?: Destination.METER

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.title, style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = current == destination,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            RecordAudioGate {
                NavHost(navController, startDestination = Destination.METER.route) {
                    composable(Destination.METER.route) { MeterScreen() }
                    composable(Destination.HISTORY.route) { HistoryScreen() }
                    composable(Destination.SETTINGS.route) { SettingsScreen() }
                }
            }
        }
    }
}
