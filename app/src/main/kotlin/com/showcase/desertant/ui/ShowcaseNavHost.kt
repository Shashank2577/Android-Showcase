package com.showcase.desertant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.showcase.desertant.core.ModelHub
import com.showcase.desertant.core.ModelKind
import com.showcase.desertant.core.Pipeline
import com.showcase.desertant.ui.components.LocalModelHub
import com.showcase.desertant.ui.home.HomeScreen
import com.showcase.desertant.ui.models.ClearScreen
import com.showcase.desertant.ui.models.EarScreen
import com.showcase.desertant.ui.models.EmoScreen
import com.showcase.desertant.ui.models.EyeScreen
import com.showcase.desertant.ui.models.GistScreen
import com.showcase.desertant.ui.models.ModeratorScreen
import com.showcase.desertant.ui.models.RedactScreen
import com.showcase.desertant.ui.models.ShapesScreen
import com.showcase.desertant.ui.models.TongueScreen
import com.showcase.desertant.ui.models.VozScreen
import com.showcase.desertant.ui.models.WhoScreen
import com.showcase.desertant.ui.pipeline.MeetingPipelineScreen
import com.showcase.desertant.ui.pipeline.MessagePipelineScreen
import com.showcase.desertant.ui.settings.SettingsScreen

@Composable
fun ShowcaseNavHost(hub: ModelHub) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    CompositionLocalProvider(LocalModelHub provides hub) {
        NavHost(nav, startDestination = "home") {
            composable("home") { HomeScreen(onOpen = { nav.navigate(it) }) }
            composable("settings") { SettingsScreen(back) }
            composable(Pipeline.MEETING.route) { MeetingPipelineScreen(back) }
            composable(Pipeline.MESSAGE.route) { MessagePipelineScreen(back) }
            ModelKind.entries.forEach { kind ->
                composable(kind.route) {
                    when (kind) {
                        ModelKind.VOZ -> VozScreen(back)
                        ModelKind.EAR -> EarScreen(back)
                        ModelKind.WHO -> WhoScreen(back)
                        ModelKind.CLEAR -> ClearScreen(back)
                        ModelKind.EYE -> EyeScreen(back)
                        ModelKind.MODERATOR -> ModeratorScreen(back)
                        ModelKind.REDACT -> RedactScreen(back)
                        ModelKind.GIST -> GistScreen(back)
                        ModelKind.EMO -> EmoScreen(back)
                        ModelKind.TONGUE -> TongueScreen(back)
                        ModelKind.SHAPES -> ShapesScreen(back)
                    }
                }
            }
        }
    }
}
