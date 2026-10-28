@file:OptIn(ExperimentalMaterial3Api::class)

package com.showcase.desertant.ui.meetings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PhoneDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.showcase.desertant.ui.common.EmptyState
import com.showcase.desertant.ui.common.dateTime
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
        MeetingImporter.import(context, r.uri, if (r.isCall) MeetingSource.CALL else MeetingSource.IMPORTED, r.name, r.dateMillis)
        reload++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calls & recordings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    val fresh = items.orEmpty().filter { it.isCall && !it.imported }
                    if (fresh.isNotEmpty()) TextButton(onClick = { fresh.forEach { import(it) } }) { Text("Import ${fresh.size} calls") }
                },
            )
        },
    ) { padding ->
        when {
            !granted -> EmptyState(
                Icons.Outlined.Call, "Bring in your call recordings",
                "Samsung's Phone app can record calls (Phone → Settings → Record calls). Burrow reads those recordings — and your Voice Recorder files — to turn them into notes.",
                Modifier.padding(padding),
            ) { Button(onClick = { permission.launch(audioPermission) }) { Text("Allow access to recordings") } }
            items?.isEmpty() == true -> EmptyState(
                Icons.Outlined.PhoneDisabled, "No recordings found",
                "Nothing in Recordings/Call or Recordings/Voice Recorder yet. Turn on call recording in Samsung's Phone app, or use Import a file.",
                Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items.orEmpty(), key = { it.uri.toString() }) { r ->
                    ElevatedCard {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (r.isCall) Icons.Outlined.Call else Icons.Outlined.Mic, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text("${dateTime(context, r.dateMillis)} · ${formatClock(r.durationSec)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (r.imported) Icon(Icons.Outlined.CheckCircle, "Imported", tint = MaterialTheme.colorScheme.tertiary)
                            else FilledTonalButton(onClick = { import(r) }) { Text("Import") }
                        }
                    }
                }
            }
        }
    }
}
