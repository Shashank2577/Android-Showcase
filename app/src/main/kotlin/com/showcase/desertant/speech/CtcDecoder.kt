package com.showcase.desertant.speech

import java.io.File

/** A recognised word with its time in the clip, in seconds. */
data class TimedWord(val text: String, val start: Double, val end: Double)

/**
 * Greedy CTC decoding into timed words. SentencePiece tokens that start with
 * "▁" begin a new word, and so does a space or "|" token (character models);
 * blanks and repeats are dropped.
 */
class CtcDecoder(private val tokens: List<String>, private val blank: Int) {

    fun decode(frames: Array<FloatArray>, frameSec: Double, offsetSec: Double = 0.0): List<TimedWord> {
        val words = ArrayList<TimedWord>()
        var current = StringBuilder()
        var wordStart = -1
        var wordEnd = -1
        var previous = -1

        fun flush() {
            val text = current.toString().trim()
            if (text.isNotEmpty() && wordStart >= 0) {
                words += TimedWord(text, offsetSec + wordStart * frameSec, offsetSec + (wordEnd + 1) * frameSec)
            }
            current = StringBuilder()
            wordStart = -1
        }

        frames.forEachIndexed { t, scores ->
            var best = 0
            for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
            if (best != blank && best != previous && best < tokens.size) {
                val piece = tokens[best]
                when {
                    // Character models (Omnilingual) mark word breaks with a space or "|" token.
                    piece.isBlank() || piece == "|" -> flush()
                    piece.startsWith(WORD_START) -> {
                        flush()
                        current.append(piece.removePrefix(WORD_START))
                    }
                    !piece.startsWith("<") -> current.append(piece)
                }
                if (current.isNotEmpty()) {
                    if (wordStart < 0) wordStart = t
                    wordEnd = t
                }
            }
            previous = best
        }
        flush()
        return words
    }

    companion object {
        const val WORD_START = "▁"

        /**
         * Reads a sherpa-onnx style `tokens.txt` ("<piece> <id>" per line). The
         * blank is the `<blk>` entry, or else the last id.
         */
        fun fromTokensFile(file: File, vocabSize: Int? = null, blankId: Int? = null): CtcDecoder {
            val byId = HashMap<Int, String>()
            file.readLines().forEach { line ->
                // The piece itself may be a space (" 4"), so only the id is trimmed.
                val cut = line.trimEnd().lastIndexOf(' ')
                if (cut < 0) return@forEach
                val id = line.substring(cut + 1).trim().toIntOrNull() ?: return@forEach
                byId[id] = line.substring(0, cut)
            }
            val size = maxOf(byId.keys.maxOrNull()?.plus(1) ?: 0, vocabSize ?: 0)
            val tokens = List(size) { byId[it] ?: "<unk>" }
            val blank = blankId ?: byId.entries.firstOrNull { it.value == "<blk>" || it.value == "<blank>" }?.key ?: (size - 1)
            return CtcDecoder(tokens, blank)
        }
    }
}
