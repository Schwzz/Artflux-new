package com.example.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.util.ArtfluxNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** The system owns transfers, so queued downloads continue after the app closes. */
data class ArtworkDownload(
    val downloadId: Long, val item: MediaItem, val offline: Boolean,
    val status: Int = DownloadManager.STATUS_PENDING, val bytes: Long = 0,
    val total: Long = -1, val reason: Int = 0, val localUri: String? = null,
    val fileMissing: Boolean = false
) {
    val complete get() = status == DownloadManager.STATUS_SUCCESSFUL && localUri != null && !fileMissing
    val storedBytes get() = if (fileMissing) 0L else (if (total > 0 && complete) total else bytes).coerceAtLeast(0)
    val label: String get() = if (fileMissing) "File missing" else when (status) {
        DownloadManager.STATUS_SUCCESSFUL -> "Downloaded"
        DownloadManager.STATUS_FAILED -> "Failed ($reason)"
        DownloadManager.STATUS_PAUSED -> "Waiting for network or storage"
        DownloadManager.STATUS_RUNNING -> if (total > 0) "${(bytes * 100 / total).coerceIn(0, 100)}%" else "Downloading"
        else -> "Queued"
    }
}

data class OfflineStorageSummary(val usedBytes: Long = 0, val freeBytes: Long = 0, val fileCount: Int = 0, val available: Boolean = false)

class ArtworkDownloads(private val context: Context) {
    private val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = context.getSharedPreferences("artflux_downloads", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private val mutableStorage = MutableStateFlow(OfflineStorageSummary())
    val storage = mutableStorage.asStateFlow()
    private var lastStorageScan = 0L
    private val refreshMutex = Mutex()

    private fun read(): List<ArtworkDownload> = runCatching {
        val a = JSONArray(prefs.getString("records", "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { o -> ArtworkDownload(o.getLong("downloadId"),
            MediaItem.fromJson(o.getJSONObject("item")), o.getBoolean("offline"), o.optInt("status", DownloadManager.STATUS_PENDING),
            bytes = o.optLong("bytes"), total = o.optLong("total", -1), reason = o.optInt("reason"),
            localUri = o.optString("uri").ifBlank { null }, fileMissing = o.optBoolean("missing")) }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray().apply { mutable.value.forEach { put(JSONObject().put("downloadId", it.downloadId)
            .put("item", it.item.toJson()).put("offline", it.offline).put("status", it.status).put("uri", it.localUri.orEmpty())
            .put("bytes", it.bytes).put("total", it.total).put("reason", it.reason).put("missing", it.fileMissing)) } }
        prefs.edit().putString("records", array.toString()).apply()
    }

    @Synchronized fun enqueue(item: MediaItem, offline: Boolean, url: String = item.actualMediaUrl, filename: String? = null): Long {
        val valid = url.toHttpUrlOrNull() ?: throw IllegalArgumentException("This artwork has no downloadable HTTP URL")
        val previous = mutable.value.firstOrNull { it.item.libraryKey == item.libraryKey && it.offline == offline && !it.fileMissing && it.status != DownloadManager.STATUS_FAILED &&
            (offline || it.status != DownloadManager.STATUS_SUCCESSFUL) }
        if (previous != null) return previous.downloadId
        val ext = valid.pathSegments.lastOrNull().orEmpty().substringAfterLast('.', "").lowercase().takeIf { it in setOf("jpg","jpeg","png","webp","gif","mp4","webm","mkv","avif") }
            ?: item.fileExt?.takeIf { it.matches(Regex("[a-zA-Z0-9]{2,5}")) } ?: if (item.mediaType == MediaType.VIDEO) "mp4" else "jpg"
        val base = (filename ?: item.title).replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(70).trim().trim('.')
            .substringBeforeLast('.', missingDelimiterValue = (filename ?: item.title).replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").take(70).trim().trim('.')).ifBlank { "artwork" }
        val name = "${base}_${System.nanoTime()}.$ext"
        val request = DownloadManager.Request(Uri.parse(valid.toString())).setTitle(item.title.take(100))
            .setDescription(if (offline) "Artflux offline library" else "Artflux • ${item.sourceName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
        ArtfluxNetwork.getHeadersForUrl(url).forEach { (key, value) -> request.addRequestHeader(key, value) }
        if (offline) request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
        else request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Artflux/$name")
        val id = manager.enqueue(request)
        mutable.value = listOf(ArtworkDownload(id, item, offline)) + mutable.value
        persist()
        return id
    }

    suspend fun refresh(verifyFiles: Boolean = true) = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
        val snapshot = mutable.value
        val updates = mutableMapOf<Long, ArtworkDownload>()
        if (snapshot.isNotEmpty()) (manager.query(DownloadManager.Query().setFilterById(*snapshot.map { it.downloadId }.toLongArray()))
            ?: throw java.io.IOException("Download service unavailable")).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val old = snapshot.firstOrNull { it.downloadId == id } ?: continue
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val uri = if (status == DownloadManager.STATUS_SUCCESSFUL) manager.getUriForDownloadedFile(id)?.toString() else null
                var total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                var missing = old.fileMissing
                if (status == DownloadManager.STATUS_SUCCESSFUL && (verifyFiles || old.status != status || old.localUri != uri)) {
                    missing = uri == null || runCatching {
                        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { descriptor ->
                            if (descriptor.statSize >= 0) total = descriptor.statSize
                            true
                        } ?: false
                    }.getOrDefault(false).not()
                }
                updates[id] = old.copy(status = status, bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                    total = total, reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)), localUri = uri, fileMissing = missing)
            }
        }
        synchronized(this@ArtworkDownloads) {
            mutable.value = mutable.value.map { old -> updates[old.downloadId] ?: if (old in snapshot) old.copy(status = DownloadManager.STATUS_FAILED, localUri = null, fileMissing = old.offline) else old }
            persist()
        }
        if (verifyFiles || System.currentTimeMillis() - lastStorageScan > 10000) scanStorage()
        }
    }

    internal fun scanStorage() {
        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        mutableStorage.value = if (directory != null && directory.exists()) {
            val files = directory.listFiles()?.filter { it.isFile }.orEmpty()
            OfflineStorageSummary(files.sumOf { it.length() }, directory.usableSpace.coerceAtLeast(0), files.size, true)
        } else OfflineStorageSummary()
        lastStorageScan = System.currentTimeMillis()
    }

    @Synchronized fun remove(id: Long) {
        val record = mutable.value.firstOrNull { it.downloadId == id }
        if (record?.offline == true || record?.status != DownloadManager.STATUS_SUCCESSFUL) manager.remove(id)
        mutable.value = mutable.value.filterNot { it.downloadId == id }
        persist()
    }

    /** Only explicitly selected private downloads may be removed by the storage screen. */
    @Synchronized fun removeOffline(ids: Set<Long>): Int {
        val selected = mutable.value.filter { it.offline && it.downloadId in ids }
        var removed = 0
        for (record in selected) {
            try { remove(record.downloadId); removed++ } catch (_: Exception) { /* Keep records that could not be removed. */ }
        }
        scanStorage()
        return removed
    }

    fun localItem(item: MediaItem, offlineOnly: Boolean = true): MediaItem? {
        val record = state.value.firstOrNull { (!offlineOnly || it.offline) && it.item.libraryKey == item.libraryKey && it.complete } ?: return null
        return item.copy(actualMediaUrl = record.localUri!!, previewUrl = if (item.mediaType == MediaType.VIDEO) "" else record.localUri.orEmpty(), sampleUrl = null)
    }
}
