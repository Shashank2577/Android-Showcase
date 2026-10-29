package com.showcase.desertant.ui.mask

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.showcase.desertant.ui.theme.BurrowTheme

/**
 * Mask from anywhere: "Mask with Burrow" in any app's text-selection menu
 * (ACTION_PROCESS_TEXT), or Burrow in the share sheet for text. From the
 * selection menu of an editable field, the masked text replaces the selection.
 */
class MaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val processText = intent?.action == Intent.ACTION_PROCESS_TEXT
        val text = if (processText) intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else intent?.getStringExtra(Intent.EXTRA_TEXT)
        val editable = processText && !intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
        setContent {
            BurrowTheme {
                MaskScreen(
                    onBack = ::finish,
                    initial = text.orEmpty(),
                    onReplace = if (editable) { masked ->
                        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, masked))
                        finish()
                    } else null,
                )
            }
        }
    }
}
