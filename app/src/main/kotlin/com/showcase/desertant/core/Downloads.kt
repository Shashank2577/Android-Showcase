package com.showcase.desertant.core

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Download [url] to [out], reporting whole percents. It writes to a `.part`
 * file, so a cut-off download never looks complete, and picks up where a
 * cut-off one stopped (multi-GB packs on phone networks drop now and then).
 * Used for the model packs Burrow fetches on request: speech packs and the
 * "ask Burrow" model.
 */
fun fetch(url: String, out: File, progress: (Int) -> Unit) {
    val tmp = File(out.path + ".part")
    val have = tmp.length()
    fun open(u: URL) = (u.openConnection() as HttpURLConnection).apply {
        if (have > 0) setRequestProperty("Range", "bytes=$have-")
    }
    var conn = open(URL(url))
    // Hugging Face answers with a redirect to its CDN.
    var hops = 0
    while (conn.responseCode in 300..399 && hops++ < 5) {
        val next = conn.getHeaderField("Location") ?: break
        conn.disconnect()
        conn = open(URL(URL(url), next))
    }
    val resumed = conn.responseCode == 206
    if (conn.responseCode != 200 && !resumed) throw IllegalStateException("the model files weren't found (HTTP ${conn.responseCode})")
    val start = if (resumed) have else 0L
    val total = conn.contentLengthLong.takeIf { it > 0 }?.plus(start) ?: -1L
    conn.inputStream.use { input ->
        FileOutputStream(tmp, resumed).use { output ->
            val buf = ByteArray(1 shl 16)
            var read = start
            var last = -1
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                output.write(buf, 0, n)
                read += n
                if (total > 0) {
                    val p = (read * 100 / total).toInt()
                    if (p != last) { last = p; progress(p) }
                }
            }
        }
    }
    if (!tmp.renameTo(out)) throw IllegalStateException("couldn't save ${out.name}")
}
