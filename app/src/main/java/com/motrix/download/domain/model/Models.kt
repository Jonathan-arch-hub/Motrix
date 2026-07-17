package com.motrix.download.domain.model

enum class AppTheme {
    AUTO, LIGHT, DARK
}

enum class TaskStatus(val value: String) {
    ACTIVE("active"),
    WAITING("waiting"),
    PAUSED("paused"),
    ERROR("error"),
    COMPLETE("complete"),
    REMOVED("removed"),
    SEEDING("seeding");

    val displayName: String
        get() = when (this) {
            ACTIVE -> "Active"
            WAITING -> "Waiting"
            PAUSED -> "Paused"
            ERROR -> "Error"
            COMPLETE -> "Complete"
            REMOVED -> "Removed"
            SEEDING -> "Seeding"
        }

    companion object {
        fun fromString(value: String): TaskStatus =
            entries.find { it.value == value } ?: WAITING
    }
}

enum class DownloadProtocol(val value: String) {
    HTTP("http"),
    HTTPS("https"),
    FTP("ftp"),
    MAGNET("magnet"),
    THUNDER("thunder"),
    TORRENT("torrent");

    companion object {
        fun detect(url: String): DownloadProtocol = when {
            url.startsWith("magnet:") -> MAGNET
            url.startsWith("thunder://") -> THUNDER
            url.startsWith("ftp://") -> FTP
            url.startsWith("https://") -> HTTPS
            url.startsWith("http://") -> HTTP
            else -> HTTP
        }
    }
}

enum class AddTaskType {
    URI, TORRENT
}

enum class TaskListTab(val displayName: String) {
    ACTIVE("Active"),
    WAITING("Waiting"),
    STOPPED("Stopped")
}

data class AppSettings(
    val theme: AppTheme = AppTheme.AUTO,
    val locale: String = "en-US",
    val downloadDir: String = "",
    val maxConcurrentDownloads: Int = 5,
    val maxConnectionPerServer: Int = 16,
    val split: Int = 16,
    val proxyEnabled: Boolean = false,
    val proxyServer: String = "",
    val autoSyncTracker: Boolean = true,
    val enableNotifications: Boolean = true,
    val keepSeeding: Boolean = false,
    val seedRatio: Float = 1.0f,
    val seedTime: Int = 60,
    val rpcPort: Int = 16800,
    val rpcSecret: String = "",
    val userAgent: String = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/111.0.0.0 Mobile Safari/537.36",
    val btTracker: String = "",
    val resumeAllOnStart: Boolean = false
)

data class DownloadTask(
    val gid: String = "",
    val status: TaskStatus = TaskStatus.WAITING,
    val totalLength: Long = 0,
    val completedLength: Long = 0,
    val uploadSpeed: Long = 0,
    val downloadSpeed: Long = 0,
    val uploadLength: Long = 0,
    val connections: Int = 0,
    val errorCode: String = "",
    val errorMessage: String = "",
    val dir: String = "",
    val files: List<TaskFile> = emptyList(),
    val name: String = "",
    val bittorrent: BitTorrentInfo? = null,
    val infoHash: String = "",
    val seeder: String = "false"
) {
    val progress: Float
        get() = if (totalLength > 0) completedLength.toFloat() / totalLength else 0f

    val displayName: String
        get() = name.ifEmpty {
            files.firstOrNull()?.path?.substringAfterLast('/') ?: gid
        }
}

data class TaskFile(
    val index: Int = 0,
    val path: String = "",
    val length: Long = 0,
    val completedLength: Long = 0,
    val selected: Boolean = true,
    val uris: List<TaskUri> = emptyList()
)

data class TaskUri(
    val uri: String = "",
    val status: String = ""
)

data class BitTorrentInfo(
    val info: BTInfo? = null,
    val announceList: List<String> = emptyList(),
    val mode: String = ""
)

data class BTInfo(
    val name: String = ""
)

data class PeerInfo(
    val peerId: String = "",
    val ip: String = "",
    val port: String = "",
    val clientName: String = ""
)

data class GlobalStat(
    val downloadSpeed: Long = 0,
    val uploadSpeed: Long = 0,
    val numActive: Int = 0,
    val numWaiting: Int = 0,
    val numStopped: Int = 0
)
