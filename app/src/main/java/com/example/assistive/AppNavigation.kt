package com.example.assistive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch // Added missing import to fix the compilation errors

// Screen destination identifiers
enum class ScreenDestination(val title: String) {
    DASHBOARD("Assistive Touch"),
    SETTINGS("Settings"),
    ABOUT("About")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(ScreenDestination.DASHBOARD) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp)
            ) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Assistive Menu",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                HorizontalDivider()

                // Sidebar items configuration
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                    label = { Text("Layout Setup") },
                    selected = currentScreen == ScreenDestination.DASHBOARD,
                    onClick = {
                        currentScreen = ScreenDestination.DASHBOARD
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                    selected = currentScreen == ScreenDestination.SETTINGS,
                    onClick = {
                        currentScreen = ScreenDestination.SETTINGS
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    label = { Text("About") },
                    selected = currentScreen == ScreenDestination.ABOUT,
                    onClick = {
                        currentScreen = ScreenDestination.ABOUT
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(currentScreen.title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Sidebar Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Determine active container context cleanly
                when (currentScreen) {
                    ScreenDestination.DASHBOARD -> MainScreen(
                        onStartClick = { onStartClick() },
                        onStopClick = { onStopClick() }
                    )
                    ScreenDestination.SETTINGS -> PlaceholderScreen(title = "Settings Configuration Panel")
                    ScreenDestination.ABOUT -> PlaceholderScreen(title = "About Development Context")
                }
            }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
    }
}