package com.motrix.download.util

object FormatUtils {

    fun bytesToSize(bytes: Long, precision: Int = 1): String {
        if (bytes == 0L) return "0 KB"
        val sizes = arrayOf("B", "KB", "MB", "GB", "TB")
        val i = (Math.floor(Math.log(bytes.toDouble()) / Math.log(1024.0))).toInt()
        if (i == 0) return "$bytes ${sizes[0]}"
        return "${String.format("%.${precision}f", bytes / Math.pow(1024.0, i.toDouble()))} ${sizes[i]}"
    }

    fun calcProgress(totalLength: Long, completedLength: Long): Float {
        if (totalLength == 0L || completedLength == 0L) return 0f
        return completedLength.toFloat() / totalLength
    }

    fun timeRemaining(totalLength: Long, completedLength: Long, downloadSpeed: Long): Long {
        if (downloadSpeed == 0L) return 0
        return Math.ceil((totalLength - completedLength).toDouble() / downloadSpeed).toLong()
    }

    fun timeFormat(seconds: Long): String {
        if (seconds <= 0) return ""
        if (seconds > 86400) return "> 1 day"
        var secs = seconds
        val hours: String
        val minutes: String
        if (secs > 3600) { hours = "${secs / 3600}h "; secs %= 3600 } else { hours = "" }
        minutes = if (secs > 60) { val m = "${secs / 60}m "; secs %= 60; m } else ""
        return "${hours}${minutes}${secs}s"
    }

    fun ellipsis(str: String, maxLen: Int = 64): String {
        if (str.length < maxLen || maxLen <= 0) return str
        return "${str.substring(0, maxLen)}..."
    }

    fun decodeThunderLink(url: String): String {
        if (!url.startsWith("thunder://")) return url
        return try {
            val decoded = android.util.Base64.decode(url.removePrefix("thunder://"), android.util.Base64.DEFAULT)
            val str = String(decoded, Charsets.UTF_8)
            str.substring(2, str.length - 2)
        } catch (_: Exception) { url }
    }

    fun splitTaskLinks(links: String): List<String> {
        return links.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { decodeThunderLink(it) }
    }

    fun detectResource(content: String): Boolean {
        return Constants.COMMON_RESOURCE_TAGS.any { content.contains(it) }
    }

    fun formatOptionsForEngine(options: Map<String, Any>): Map<String, String> {
        return options.mapValues { (_, v) ->
            when (v) {
                is List<*> -> v.joinToString("\n")
                else -> v.toString()
            }
        }
    }

    fun changeKeysToCamelCase(map: Map<String, String>): Map<String, String> {
        return map.mapKeys { (key, _) ->
            key.split("-").mapIndexed { index, part ->
                if (index == 0) part else part.replaceFirstChar { it.uppercase() }
            }.joinToString("")
        }
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0) return ""
        if (seconds > 86400) return "> 1 day"
        var secs = seconds
        val hours: String
        val minutes: String
        if (secs > 3600) { hours = "${secs / 3600}h "; secs %= 3600 } else { hours = "" }
        minutes = if (secs > 60) { val m = "${secs / 60}m "; secs %= 60; m } else ""
        return "${hours}${minutes}${secs}s"
    }
}
