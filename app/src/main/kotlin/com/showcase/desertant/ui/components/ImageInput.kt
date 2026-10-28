package com.showcase.desertant.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.showcase.desertant.image.ImageLoader
import kotlinx.coroutines.launch

/** A button that opens the system photo picker and decodes the choice. */
@Composable
fun PickImageButton(
    label: String = "Pick a photo",
    enabled: Boolean = true,
    onError: (String) -> Unit = {},
    onImage: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            loading = true
            scope.launch {
                try {
                    onImage(ImageLoader.load(context, uri))
                } catch (e: Exception) {
                    onError(e.message ?: "This image could not be read.")
                } finally {
                    loading = false
                }
            }
        }
    }
    Button(
        enabled = enabled && !loading,
        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
    ) {
        Icon(Icons.Outlined.AddPhotoAlternate, null)
        Spacer(Modifier.width(6.dp))
        Text(if (loading) "Loading…" else label)
    }
}
