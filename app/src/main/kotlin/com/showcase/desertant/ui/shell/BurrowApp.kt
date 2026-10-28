package com.showcase.desertant.ui.shell

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.showcase.desertant.core.ModelHub
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.labGraph
import com.showcase.desertant.ui.meetings.DeviceRecordingsScreen
import com.showcase.desertant.ui.meetings.MeetingDetailScreen
import com.showcase.desertant.ui.meetings.MeetingsHome
import com.showcase.desertant.ui.meetings.RecordScreen
import com.showcase.desertant.ui.people.PeopleHome
import com.showcase.desertant.ui.people.PersonScreen
import com.showcase.desertant.ui.photos.PhotoReviewScreen
import com.showcase.desertant.ui.photos.PhotosHome
import com.showcase.desertant.ui.photos.ReviewKind
import com.showcase.desertant.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    PHOTOS("photos", "Photos", Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    MEETINGS("meetings", "Meetings", Icons.Outlined.Mic, Icons.Filled.Mic),
    PEOPLE("people", "People", Icons.Outlined.Groups, Icons.Filled.Groups),
}

/** Whether the window is wide enough for side-by-side panes (a Fold's inner screen, tablets, landscape). */
val LocalWideLayout = staticCompositionLocalOf { false }

@Composable
fun BurrowApp(hub: ModelHub, deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val width = LocalConfiguration.current.screenWidthDp
    val wide = width >= 700
    val tab = Tab.entries.firstOrNull { it.route == route }
    val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
    val back: () -> Unit = { nav.popBackStack() }

    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            nav.navigate(deepLink)
            onDeepLinkHandled()
        }
    }

    CompositionLocalProvider(LocalModelHub provides hub, LocalWideLayout provides wide) {
        Scaffold(
            bottomBar = {
                if (!wide && tab != null) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(
                                selected = t == tab,
                                onClick = { nav.switchTab(t.route) },
                                icon = { Icon(if (t == tab) t.selectedIcon else t.icon, null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding)) {
                if (wide && tab != null) {
                    NavigationRail {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = t == tab,
                                onClick = { nav.switchTab(t.route) },
                                icon = { Icon(if (t == tab) t.selectedIcon else t.icon, null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                }
                NavHost(nav, startDestination = Tab.MEETINGS.route, modifier = Modifier.weight(1f)) {
                    composable(Tab.PHOTOS.route) { PhotosHome(open = go) }
                    composable("photos/review/{kind}", listOf(navArgument("kind") { type = NavType.StringType })) { e ->
                        PhotoReviewScreen(ReviewKind.valueOf(e.arguments?.getString("kind") ?: ReviewKind.SIMILAR.name), onBack = back)
                    }
                    composable(Tab.MEETINGS.route) { MeetingsHome(open = go) }
                    composable("record") { RecordScreen(onBack = back, onSaved = back) }
                    composable("recordings") { DeviceRecordingsScreen(onBack = back) }
                    composable("meeting/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                        MeetingDetailScreen(e.arguments?.getLong("id") ?: 0, onBack = back, open = go)
                    }
                    composable(Tab.PEOPLE.route) { PeopleHome(open = go) }
                    composable("person/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                        PersonScreen(e.arguments?.getLong("id") ?: 0, onBack = back, open = go)
                    }
                    composable("settings") { SettingsScreen(onBack = back, openLab = { go("lab") }) }
                    labGraph(nav)
                }
            }
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
