package com.showcase.desertant.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.showcase.desertant.app
import com.showcase.desertant.core.ModelHub
import com.showcase.desertant.privacy.Biometrics
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandNav
import com.showcase.desertant.ui.hand.HandTab
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.ask.AskScreen
import com.showcase.desertant.ui.chats.ChatScreen
import com.showcase.desertant.ui.labGraph
import com.showcase.desertant.ui.mask.MaskScreen
import com.showcase.desertant.ui.meetings.DeviceRecordingsScreen
import com.showcase.desertant.ui.meetings.MeetingDetailScreen
import com.showcase.desertant.ui.meetings.MeetingsHome
import com.showcase.desertant.ui.meetings.RecordScreen
import com.showcase.desertant.ui.onboarding.AskFirstScreen
import com.showcase.desertant.ui.onboarding.WelcomeScreen
import com.showcase.desertant.ui.people.PeopleHome
import com.showcase.desertant.ui.people.PersonScreen
import com.showcase.desertant.ui.photos.PhotoReviewScreen
import com.showcase.desertant.ui.photos.PhotosHome
import com.showcase.desertant.ui.photos.ReviewKind
import com.showcase.desertant.ui.photos.SweepScreen
import com.showcase.desertant.ui.photos.VaultScreen
import com.showcase.desertant.ui.privacy.PrivacyScreen
import com.showcase.desertant.ui.settings.AppSettingsScreen
import com.showcase.desertant.ui.settings.SettingsScreen
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import com.showcase.desertant.ui.today.TodayScreen
import kotlinx.coroutines.launch

/** Whether the window is wide enough for side-by-side panes (a Fold's inner screen, tablets, landscape). */
val LocalWideLayout = staticCompositionLocalOf { false }

@Composable
fun BurrowApp(hub: ModelHub, deepLink: String?, onDeepLinkHandled: () -> Unit) {
    val app = LocalContext.current.app
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    val tab = HandTab.entries.firstOrNull { it.route == route }
    val onboarded = remember { app.privacy.onboarded.value }
    val locked by app.locked.collectAsState()
    val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
    val back: () -> Unit = { nav.popBackStack() }

    LaunchedEffect(deepLink) {
        if (deepLink != null && onboarded) {
            nav.navigate(deepLink)
            onDeepLinkHandled()
        }
    }

    CompositionLocalProvider(LocalModelHub provides hub, LocalWideLayout provides wide) {
        Column(Modifier.fillMaxSize().background(burrow.paper)) {
            NavHost(nav, startDestination = if (onboarded) HandTab.TODAY.route else "welcome", modifier = Modifier.weight(1f)) {
                composable("welcome") { WelcomeScreen(onStart = { go("ask-first") }) }
                composable("ask-first") {
                    AskFirstScreen(onBack = back, onDone = {
                        nav.navigate(HandTab.TODAY.route) { popUpTo("welcome") { inclusive = true } }
                    })
                }

                composable(HandTab.TODAY.route) { TodayScreen(open = go) }
                composable(HandTab.PHOTOS.route) { PhotosHome(open = go) }
                composable(HandTab.TALKS.route) { MeetingsHome(open = go) }
                composable(HandTab.PEOPLE.route) { PeopleHome(open = go) }

                composable("photos/review/{kind}", listOf(navArgument("kind") { type = NavType.StringType })) { e ->
                    PhotoReviewScreen(ReviewKind.valueOf(e.arguments?.getString("kind") ?: ReviewKind.SIMILAR.name), onBack = back)
                }
                composable("sweep") { SweepScreen(onBack = back, openVault = { go("vault") }) }
                composable("vault") { VaultScreen(onBack = back) }
                composable("record") { RecordScreen(onBack = back, onSaved = back) }
                composable("recordings") { DeviceRecordingsScreen(onBack = back) }
                composable("meeting/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    MeetingDetailScreen(e.arguments?.getLong("id") ?: 0, onBack = back, open = go)
                }
                composable("person/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    PersonScreen(e.arguments?.getLong("id") ?: 0, onBack = back, open = go)
                }
                composable("mask") { MaskScreen(onBack = back) }
                composable("ask") { AskScreen(onBack = back, open = go) }
                composable("chat/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    ChatScreen(e.arguments?.getLong("id") ?: 0, onBack = back, open = go)
                }
                composable("privacy") {
                    PrivacyScreen(onBack = back, openModels = { go("models") }, onWiped = {
                        nav.navigate("welcome") { popUpTo(0) { inclusive = true } }
                    })
                }
                composable("settings") { AppSettingsScreen(onBack = back, open = go) }
                composable("logs") { com.showcase.desertant.ui.settings.LogsScreen(onBack = back) }
                composable("models") { SettingsScreen(onBack = back, openLab = { go("lab") }) }
                labGraph(nav)
            }
            if (tab != null) HandNav(tab, onSelect = { nav.switchTab(it.route) })
        }
        if (locked) LockScreen { app.unlocked() }
    }
}

/** Shown over everything when "lock with fingerprint" is on and Burrow comes back. */
@Composable
private fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun ask() = scope.launch { if (Biometrics.confirm(context, "Open Burrow")) onUnlocked() }
    LaunchedEffect(Unit) { ask() }
    Box(Modifier.fillMaxSize().background(burrow.paper), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            DoodleIcon(Doodle.LOCK, null, size = 84.dp, stroke = 2.4.dp)
            Text("burrow is locked.", style = Hand.headline, color = burrow.ink)
            Meta("Your photos, talks and people stay hidden until you confirm it's you.")
            Spacer(Modifier.height(8.dp))
            InkButton("unlock", { ask() }, Modifier.fillMaxWidth())
        }
    }
}

private fun NavHostController.switchTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
