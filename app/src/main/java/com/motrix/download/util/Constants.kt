package com.motrix.download.util

object Constants {
    const val ENGINE_RPC_HOST = "127.0.0.1"
    const val ENGINE_RPC_PORT = 16800
    const val ENGINE_MAX_CONCURRENT_DOWNLOADS = 10
    const val ENGINE_MAX_CONNECTION_PER_SERVER = 64
    const val UNKNOWN_PEERID = "%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00%00"
    const val UNKNOWN_PEERID_NAME = "unknown"
    const val GRAPHIC = "░▒▓█"
    const val ONE_SECOND = 1000L
    const val ONE_MINUTE = ONE_SECOND * 60
    const val ONE_HOUR = ONE_MINUTE * 60
    const val ONE_DAY = ONE_HOUR * 24
    const val AUTO_SYNC_TRACKER_INTERVAL = ONE_HOUR * 12
    const val MAX_NUM_OF_DIRECTORIES = 5
    const val NONE_SELECTED_FILES = "none"
    const val SELECTED_ALL_FILES = "all"

    val IMAGE_SUFFIXES = listOf(
        ".ai", ".bmp", ".eps", ".fig", ".gif", ".heic", ".icn", ".ico",
        ".jpeg", ".jpg", ".png", ".psd", ".raw", ".sketch", ".svg",
        ".tif", ".webp", ".xd"
    )
    val AUDIO_SUFFIXES = listOf(
        ".aac", ".ape", ".flac", ".flav", ".m4a", ".mp3", ".ogg", ".wav", ".wma"
    )
    val VIDEO_SUFFIXES = listOf(
        ".avi", ".m4v", ".mkv", ".mov", ".mp4", ".mpg", ".rmvb", ".vob", ".wmv"
    )
    val SUB_SUFFIXES = listOf(
        ".ass", ".idx", ".smi", ".srt", ".ssa", ".sst", ".sub"
    )
    val DOCUMENT_SUFFIXES = listOf(
        ".azw3", ".csv", ".doc", ".docx", ".epub", ".key", ".mobi",
        ".numbers", ".pages", ".pdf", ".ppt", ".pptx", ".txt", ".xsl", ".xslx"
    )

    val COMMON_RESOURCE_TAGS = listOf("http://", "https://", "ftp://", "magnet:")
    val THUNDER_RESOURCE_TAGS = listOf("thunder://")

    val SUPPORT_RTL_LOCALES = listOf("ar", "fa", "he", "ku", "pa", "ps", "sd", "ur", "yi")
}
