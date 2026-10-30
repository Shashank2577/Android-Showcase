package com.showcase.desertant.ui.mask

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.showcase.desertant.app
import com.showcase.desertant.core.languageName
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.DoodleIcon
import com.showcase.desertant.ui.hand.HandField
import com.showcase.desertant.ui.hand.HandLink
import com.showcase.desertant.ui.hand.HandPage
import com.showcase.desertant.ui.hand.InkButton
import com.showcase.desertant.ui.hand.Meta
import com.showcase.desertant.ui.hand.OnDeviceStamp
import com.showcase.desertant.ui.hand.PageIntro
import com.showcase.desertant.ui.hand.PillButton
import com.showcase.desertant.ui.hand.StepHead
import com.showcase.desertant.ui.privacy.SecureScreen
import com.showcase.desertant.ui.theme.Hand
import com.showcase.desertant.ui.theme.bodyStyle
import com.showcase.desertant.ui.theme.burrow
import kotlinx.coroutines.launch

private val PLACEHOLDER = Regex("""\[[A-Z][A-Z_]*_\d+]""")

/**
 * Mask: names, numbers, addresses and card numbers become [NAME_1]-style
 * placeholders before the text goes to an AI chat or a form. Paste the reply
 * back and the real details return — all on the phone, nothing stored.
 *
 * [onReplace] is set when Burrow was opened from another app's text-selection
 * menu: the masked text can go straight back into that app.
 */
@Composable
fun MaskScreen(onBack: (() -> Unit)?, initial: String = "", onReplace: ((String) -> Unit)? = null) {
    SecureScreen()
    val context = LocalContext.current
    val session = context.app.mask
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf(initial) }
    var reply by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val masked by session.current.collectAsState()
    val ink = burrow.ink
    val marker = burrow.marker
    val markerInk = burrow.markerInk

    fun maskNow() = scope.launch {
        busy = true
        error = null
        runCatching { session.mask(text) }.onFailure { error = it.message ?: "Couldn't mask this" }
        busy = false
    }

    val current = masked?.takeIf { it.original == text }
    HandPage(onBack = onBack, topEnd = { OnDeviceStamp(Modifier.padding(end = 12.dp)) }) {
        PageIntro(
            "mask a message.",
            "Before text goes into ChatGPT, an email or a form, Burrow swaps names, numbers and addresses for placeholders. Paste the reply back and the real details return.",
            mark = "mask",
            doodle = Doodle.MASK,
        )
        StepHead(1, "paste your text", done = current != null)
        HandField(text, { text = it }, placeholder = "Hi, I'm Sam Carter, card 4111 1111 1111 1111 — call me on +44 7700 900123…", minHeight = 120.dp)
        Spacer(Modifier.height(12.dp))
        if (current == null) {
            InkButton("mask it", ::maskNow, Modifier.fillMaxWidth(), enabled = text.isNotBlank(), busy = busy)
            error?.let { Meta(it, Modifier.padding(top = 8.dp), color = burrow.redPen) }
        }

        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DoodleIcon(Doodle.TICK, null, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (current.secrets == 0) "nothing personal found" else if (current.secrets == 1) "1 secret found" else "${current.secrets} secrets found",
                    style = Hand.label, color = ink,
                )
                current.language?.let { Meta("  ·  ${languageName(it).lowercase()}") }
            }
            Spacer(Modifier.height(10.dp))
            StepHead(2, "copy the safe version")
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.8.dp, ink, RoundedCornerShape(16.dp))
                    .padding(18.dp),
            ) {
                SelectionContainer { Text(highlight(current.text, PLACEHOLDER, marker, markerInk), style = bodyStyle(16f, 25f), color = ink) }
                Spacer(Modifier.height(10.dp))
                Meta("reversible on this phone only")
            }
            Spacer(Modifier.height(14.dp))
            InkButton(
                if (onReplace != null) "put the masked text back" else "copy masked text",
                {
                    if (onReplace != null) onReplace(current.text)
                    else {
                        copy(context, current.text, sensitive = false)
                        Toast.makeText(context, "Copied. Paste it into the AI, then bring its reply back here.", Toast.LENGTH_LONG).show()
                    }
                },
                Modifier.fillMaxWidth(), trailing = Doodle.ARROW,
            )
            if (onReplace != null) HandLink("copy it instead", { copy(context, current.text, sensitive = false) }, Modifier.align(Alignment.CenterHorizontally))

            Spacer(Modifier.height(24.dp))
            StepHead(3, "bring the reply back", done = reply.isNotBlank())
            Meta("Paste what the AI wrote. Burrow puts the real details back in, here on the phone.")
            Spacer(Modifier.height(10.dp))
            HandField(reply, { reply = it }, placeholder = "Dear [NAME_1], your order to [ADDRESS_1]…", minHeight = 96.dp)
            PillButton("paste from clipboard", { paste(context)?.let { reply = it } }, Modifier.padding(top = 8.dp))
            if (reply.isNotBlank()) {
                val restored = session.restore(reply)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().border(1.4.dp, burrow.rule, RoundedCornerShape(16.dp)).padding(18.dp)) {
                    SelectionContainer {
                        Text(restoredText(restored, current.redaction.items.map { it.original }, ink), style = bodyStyle(16f, 25f), color = ink)
                    }
                }
                Spacer(Modifier.height(10.dp))
                InkButton("copy the restored reply", {
                    copy(context, restored, sensitive = true)
                    Toast.makeText(context, "Copied, marked as sensitive.", Toast.LENGTH_SHORT).show()
                }, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            PillButton("start over with new text", { session.clear(); text = ""; reply = "" }, leading = Doodle.PLUS)
        }
        Spacer(Modifier.height(20.dp))
        Meta("Found on this phone by Redact. Nothing is stored; Burrow forgets this in 30 minutes or when you close it.")
    }
}

private fun highlight(text: String, pattern: Regex, bg: Color, fg: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    pattern.findAll(text).forEach { m ->
        addStyle(SpanStyle(background = bg, color = fg, fontWeight = FontWeight.SemiBold), m.range.first, m.range.last + 1)
    }
}

/** The restored reply, with the details that came back underlined so they're easy to check. */
private fun restoredText(text: String, originals: List<String>, ink: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    originals.filter { it.isNotBlank() }.distinct().forEach { o ->
        var at = text.indexOf(o)
        while (at >= 0) {
            addStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = ink, fontWeight = FontWeight.SemiBold), at, at + o.length)
            at = text.indexOf(o, at + o.length)
        }
    }
}

private fun copy(context: Context, text: String, sensitive: Boolean) {
    val clip = ClipData.newPlainText("Burrow", text)
    if (sensitive && Build.VERSION.SDK_INT >= 33) {
        // Keeps the restored details out of Android's clipboard preview.
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

private fun paste(context: Context): String? =
    context.getSystemService(ClipboardManager::class.java).primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
