package com.showcase.desertant.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.showcase.desertant.BuildConfig
import com.showcase.desertant.app
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.theme.burrow

/** Burrow's step-by-step log, to copy and paste when something goes wrong. */
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val log = context.app.log
    val lines by log.lines.collectAsState()

    HandPage(onBack = onBack) {
        PageIntro("logs", "Each step Burrow took with its models, and anything that failed. Copy it and paste it when you report a problem.", doodle = Doodle.PEN)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton("copy all", { copy(context) }, leading = Doodle.SHARE)
            HandLink("clear", { log.clear() })
        }
        Spacer(Modifier.height(10.dp))
        Meta("${lines.size} line${if (lines.size == 1) "" else "s"}, newest last. Kept on this phone only.")
        HandDivider()
        if (lines.isEmpty()) Meta("Nothing yet.")
        SelectionContainer {
            Text(
                lines.joinToString("\n"),
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
                color = burrow.pencil,
            )
        }
    }
}

private fun copy(context: Context) {
    val app = context.app
    val header = "Burrow ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) on ${Build.MANUFACTURER} ${Build.MODEL}, " +
        "Android ${Build.VERSION.RELEASE}, models in ${app.models.root.path}"
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Burrow logs", app.log.text(header)))
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
