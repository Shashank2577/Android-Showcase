package com.showcase.desertant.ui.meetings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.showcase.desertant.audio.formatClock
import com.showcase.desertant.data.MeetingSource
import com.showcase.desertant.meetings.ExternalRecording
import com.showcase.desertant.meetings.MeetingImporter
import com.showcase.desertant.ui.common.dateTime
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandDivider
import com.showcase.desertant.ui.hand.HandEmpty
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.Steps
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

/** Call recordings and voice memos other apps saved on the phone, ready to bring in. */
@Composable
fun DeviceRecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var items by remember { mutableStateOf<List<ExternalRecording>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(granted, reload) { if (granted) items = MeetingImporter.deviceRecordings(context) }

    fun import(r: ExternalRecording) = scope.launch {
        try {
            MeetingImporter.import(context, r.uri, if (r.isCall) MeetingSource.CALL else MeetingSource.IMPORTED, r.name, r.dateMillis)
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't import ${r.name}: ${e.message ?: "unreadable"}", Toast.LENGTH_LONG).show()
        }
        reload++
    }

    val fresh = items.orEmpty().filter { it.isCall && !it.imported }
    HandPage(
        onBack = onBack,
        bottom = if (fresh.size > 1) ({ InkButton("bring in all ${fresh.size} new calls", { fresh.forEach { import(it) } }, Modifier.fillMaxWidth()) }) else null,
    ) {
        PageIntro("calls & recordings", "Recordings other apps saved on this phone. Bring one in and Burrow writes its notes, like any talk.", doodle = Doodle.PHONE)
        when {
            !granted -> {
                Steps(
                    listOf(
                        "Turn on call recording in Samsung's Phone app (Phone → ⋮ → Settings → Record calls)",
                        "Let Burrow see your recordings",
                        "Tap “bring in” on a call to get its notes",
                    ),
                    current = 1,
                )
                Spacer(Modifier.height(18.dp))
                InkButton("let Burrow see recordings", { permission.launch(audioPermission) }, Modifier.fillMaxWidth())
            }
            items == null -> Meta("looking for recordings…")
            items?.isEmpty() == true -> HandEmpty(
                "no recordings yet.",
                "Nothing in Recordings/Call or Recordings/Voice Recorder. Turn on call recording in Samsung's Phone app, or import a file from the talks page.",
                doodle = Doodle.PHONE,
            )
            else -> items.orEmpty().forEachIndexed { n, r ->
                if (n > 0) HandDivider(seed = n)
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    DoodleIcon(if (r.isCall) Doodle.PHONE else Doodle.MIC, if (r.isCall) "Call" else "Recording", size = 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = Hand.label, color = burrow.ink, maxLines = 1)
                        Meta("${dateTime(context, r.dateMillis)} · ${formatClock(r.durationSec)}")
                    }
                    if (r.imported) Meta("brought in ✓")
                    else PillButton("bring in", { import(r) })
                }
            }
        }
    }
}
