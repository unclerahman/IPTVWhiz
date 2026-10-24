package com.iptvwhiz.app.data.epg

import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

data class XmltvProgramme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/**
 * Streaming XMLTV reader: calls back per `<channel>` and per `<programme>` instead of building
 * the whole guide in memory (guides can be 100+ MB). The parser is passed in so Android uses
 * its own and JVM tests can use kxml2.
 */
class XmltvParser(private val parser: XmlPullParser) {

    fun parse(
        input: InputStream,
        onChannel: (id: String, displayNames: List<String>) -> Unit,
        onProgramme: (XmltvProgramme) -> Unit,
    ) {
        parser.setInput(input, null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel()?.let { (id, names) -> onChannel(id, names) }
                    "programme" -> readProgramme()?.let(onProgramme)
                }
            }
            event = parser.next()
        }
    }

    private fun readChannel(): Pair<String, List<String>>? {
        val id = parser.getAttributeValue(null, "id")
        val names = mutableListOf<String>()
        forEachChild { tag -> if (tag == "display-name") names += readText() else skip() }
        return id?.takeIf { it.isNotBlank() }?.let { it to names }
    }

    private fun readProgramme(): XmltvProgramme? {
        val channel = parser.getAttributeValue(null, "channel")
        val start = parser.getAttributeValue(null, "start")?.let(::parseXmltvTime)
        val stop = parser.getAttributeValue(null, "stop")?.let(::parseXmltvTime)
        var title: String? = null
        var description: String? = null
        forEachChild { tag ->
            when (tag) {
                // The first title/desc is the main language; later ones are translations.
                "title" -> readText().let { if (title == null) title = it }
                "desc" -> readText().let { if (description == null) description = it }
                else -> skip()
            }
        }
        if (channel.isNullOrBlank() || start == null || stop == null || title.isNullOrBlank() || stop <= start) return null
        return XmltvProgramme(channel, start, stop, title!!.trim(), description?.trim()?.take(MAX_DESCRIPTION))
    }

    /** Calls [block] for each direct child element; [block] must consume that element. */
    private inline fun forEachChild(block: (String) -> Unit) {
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.depth == depth) return
            if (event == XmlPullParser.START_TAG) block(parser.name)
        }
    }

    /** Text of the current element; leaves the parser on its end tag. */
    private fun readText(): String {
        val text = StringBuilder()
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.TEXT || event == XmlPullParser.CDSECT) text.append(parser.text)
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
        }
        return text.toString().trim()
    }

    private fun skip() {
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) return
            if (event == XmlPullParser.END_TAG && parser.depth == depth) return
        }
    }

    private companion object {
        const val MAX_DESCRIPTION = 400
    }
}

/**
 * XMLTV time: "yyyyMMddHHmmss +hhmm" (seconds and offset optional; no offset means UTC).
 * Done by hand because java.time needs Android 8 and these run hundreds of thousands of times.
 */
fun parseXmltvTime(text: String): Long? {
    val value = text.trim()
    if (value.length < 12 || !value.substring(0, 12).all(Char::isDigit)) return null
    fun num(from: Int, to: Int) = value.substring(from, to).toInt()
    val year = num(0, 4)
    val month = num(4, 6)
    val day = num(6, 8)
    val hour = num(8, 10)
    val minute = num(10, 12)
    val second = if (value.length >= 14 && value[12].isDigit() && value[13].isDigit()) num(12, 14) else 0
    if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null

    var offsetMinutes = 0
    val offset = value.substringAfter(' ', "").trim()
    if (offset.length == 5 && (offset[0] == '+' || offset[0] == '-') && offset.substring(1).all(Char::isDigit)) {
        val minutes = offset.substring(1, 3).toInt() * 60 + offset.substring(3, 5).toInt()
        offsetMinutes = if (offset[0] == '-') -minutes else minutes
    }
    val days = daysFromCivil(year, month, day)
    val seconds = days * 86_400L + hour * 3600L + minute * 60L + second - offsetMinutes * 60L
    return seconds * 1000L
}

/** Days since 1970-01-01 for a proleptic Gregorian date (Howard Hinnant's algorithm). */
private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + 9) % 12
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146_097L + doe - 719_468L
}

/** Loose channel-name key for matching guide channels to playlist channels by name. */
fun epgNameKey(name: String): String {
    val base = name.lowercase()
        .replace(Regex("""\(.*?\)|\[.*?]"""), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
    return base.split(' ')
        .filter { it.isNotEmpty() }
        .dropLastWhile { it in QUALITY_WORDS }
        .joinToString("")
}

private val QUALITY_WORDS = setOf("hd", "fhd", "uhd", "sd", "4k", "hevc", "h265")
