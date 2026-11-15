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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import com.showcase.desertant.ui.hand.Body
import com.showcase.desertant.ui.hand.Caption
import com.showcase.desertant.ui.hand.Doodle
import com.showcase.desertant.ui.hand.MarkedText
import com.showcase.desertant.ui.hand.Warning
import com.showcase.desertant.ui.theme.Mono
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
import com.showcase.desertant.ui.theme.Type
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
    var stage by rememberSaveable { mutableIntStateOf(1) }
    val ink = burrow.ink
    val chip = burrow.paper

    fun maskNow() = scope.launch {
        busy = true
        error = null
        runCatching { session.mask(text) }
            .onSuccess { stage = 2 }
            .onFailure { error = it.message ?: "Couldn't mask this" }
        busy = false
    }

    val current = masked?.takeIf { it.original == text }
    val restored = if (current != null && reply.isNotBlank()) session.restore(reply) else null
    val open = if (current == null) 1 else stage
    HandPage(
        onBack = onBack,
        topEnd = { OnDeviceStamp(Modifier.padding(end = 12.dp)) },
        bottom = {
            val wide = Modifier.widthIn(max = 400.dp).fillMaxWidth()
            when (open) {
                1 -> InkButton("mask it", ::maskNow, wide, enabled = text.isNotBlank(), busy = busy)
                2 -> InkButton(
                    if (onReplace != null) "put the masked text back" else "copy masked text",
                    {
                        val c = current ?: return@InkButton
                        if (onReplace != null) onReplace(c.text)
                        else {
                            copy(context, c.text, sensitive = false)
                            Toast.makeText(context, "Copied. Paste it into the AI, then bring its reply back here.", Toast.LENGTH_LONG).show()
                            stage = 3
                        }
                    },
                    wide, trailing = Doodle.ARROW,
                )
                else -> InkButton("copy the restored reply", {
                    restored?.let {
                        copy(context, it, sensitive = true)
                        Toast.makeText(context, "Copied, marked as sensitive.", Toast.LENGTH_SHORT).show()
                    }
                }, wide, enabled = restored != null)
            }
        },
    ) {
        PageIntro(
            "mask a message",
            "Before text goes into an AI chat, an email or a form, Burrow swaps names, numbers and addresses for placeholders, and puts them back in the reply.",
            doodle = Doodle.MASK,
        )
        StepHead(1, "paste your text", done = current != null, open = open == 1, onClick = if (current != null && open != 1) ({ stage = 1 }) else null)
        if (open == 1) {
            HandField(text, { text = it }, placeholder = "Hi, I'm Sam Carter, card 4111 1111 1111 1111. Call me on +44 7700 900123…", minHeight = 120.dp, textStyle = Type.reading)
            error?.let { Warning(it, Modifier.padding(top = 8.dp)) }
            if (current != null) HandLink("mask this again", { stage = 2 })
        }

        Spacer(Modifier.height(8.dp))
        StepHead(2, "copy the safe version", done = current != null && stage > 2, open = open == 2, onClick = if (current != null && open != 2) ({ stage = 2 }) else null)
        if (open == 2 && current != null) {
            val found = if (current.secrets == 0) "nothing personal" else if (current.secrets == 1) "1 secret" else "${current.secrets} secrets"
            Row(verticalAlignment = Alignment.Bottom) {
                MarkedText("$found found", mark = found, style = Type.rowTitle)
                current.language?.let { Caption("  ·  ${languageName(it).substringBefore(" (")}", Modifier.padding(bottom = 2.dp)) }
            }
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(
                    highlight(current.text, PLACEHOLDER, chip, ink),
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(burrow.smudge).padding(16.dp),
                    style = Type.reading, color = ink,
                )
            }
            Caption("Reversible on this phone only.", Modifier.padding(top = 6.dp))
            if (onReplace != null) HandLink("copy it instead", { copy(context, current.text, sensitive = false); stage = 3 })
        }

        Spacer(Modifier.height(8.dp))
        StepHead(3, "bring the reply back", done = restored != null, open = open == 3, onClick = if (current != null && open != 3) ({ stage = 3 }) else null)
        if (open == 3 && current != null) {
            Body("Paste what the AI wrote. Burrow puts the real details back in, here on the phone.")
            Spacer(Modifier.height(10.dp))
            HandField(reply, { reply = it }, placeholder = "Dear [NAME_1], your order to [ADDRESS_1]…", minHeight = 96.dp, textStyle = Type.reading)
            PillButton("paste from clipboard", { paste(context)?.let { reply = it } }, Modifier.padding(top = 8.dp))
            if (restored != null) {
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(
                        restoredText(restored, current.redaction.items.map { it.original }, ink),
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(burrow.smudge).padding(16.dp),
                        style = Type.reading, color = ink,
                    )
                }
            }
        }
        if (current != null) HandLink("start over with new text", { session.clear(); text = ""; reply = ""; stage = 1 }, Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(16.dp))
        Caption("Found on this phone by Redact. Nothing is stored; Burrow forgets this in 30 minutes or when you close it.")
    }
}

private fun highlight(text: String, pattern: Regex, bg: Color, fg: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    pattern.findAll(text).forEach { m ->
        // Placeholders are monospace on a chip, so they read as slots, not as words.
        addStyle(SpanStyle(background = bg, color = fg, fontWeight = FontWeight.SemiBold, fontFamily = Mono), m.range.first, m.range.last + 1)
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
