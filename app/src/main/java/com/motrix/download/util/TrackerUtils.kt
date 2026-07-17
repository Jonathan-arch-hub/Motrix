package com.motrix.download.util

object TrackerUtils {

    const val MAX_BT_TRACKER_LENGTH = 6144

    val NGOSANG_TRACKERS_BEST_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
    val NGOSANG_TRACKERS_BEST_IP_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best_ip.txt"
    val NGOSANG_TRACKERS_ALL_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_all.txt"
    val NGOSANG_TRACKERS_ALL_IP_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_all_ip.txt"

    val XIU2_TRACKERS_BEST_URL = "https://raw.githubusercontent.com/XIU2/TrackersListCollection/master/best.txt"
    val XIU2_TRACKERS_ALL_URL = "https://raw.githubusercontent.com/XIU2/TrackersListCollection/master/all.txt"
    val XIU2_TRACKERS_HTTP_URL = "https://raw.githubusercontent.com/XIU2/TrackersListCollection/master/http.txt"

    val TRACKER_SOURCES = listOf(
        "ngosang/trackerslist" to listOf(
            NGOSANG_TRACKERS_BEST_URL to "trackers_best.txt",
            NGOSANG_TRACKERS_BEST_IP_URL to "trackers_best_ip.txt",
            NGOSANG_TRACKERS_ALL_URL to "trackers_all.txt",
            NGOSANG_TRACKERS_ALL_IP_URL to "trackers_all_ip.txt"
        ),
        "XIU2/TrackersListCollection" to listOf(
            XIU2_TRACKERS_BEST_URL to "best.txt",
            XIU2_TRACKERS_ALL_URL to "all.txt",
            XIU2_TRACKERS_HTTP_URL to "http.txt"
        )
    )

    suspend fun fetchBtTrackers(urls: List<String>, client: okhttp3.OkHttpClient): List<String> {
        val results = mutableListOf<String>()
        for (url in urls) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("${url}?t=${System.currentTimeMillis()}")
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    response.body?.string()?.let { body ->
                        val trackers = body.lines()
                            .map { it.trim() }
                            .filter { it.startsWith("udp://") || it.startsWith("http://") || it.startsWith("wss://") }
                        results.addAll(trackers)
                    }
                }
            } catch (_: Exception) { }
        }
        return results.distinct()
    }

    fun convertTrackerDataToComma(arr: List<String>): String {
        return arr.joinToString(",").trim()
    }

    fun reduceTrackerString(str: String): String {
        if (str.length <= MAX_BT_TRACKER_LENGTH) return str
        val subStr = str.substring(0, MAX_BT_TRACKER_LENGTH)
        val index = subStr.lastIndexOf(',')
        return if (index == -1) subStr else subStr.substring(0, index)
    }
}
