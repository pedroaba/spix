package com.pedroaba.spix.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.net.Uri
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class StorageUsage(
    val device: DeviceStorage?,
    val app: AppStorage?,
    val downloads: DownloadStorage?,
)

internal data class DeviceStorage(val total: Long, val available: Long)
internal data class AppStorage(val installation: Long, val data: Long, val cache: Long)
internal data class DownloadStorage(val measuredBytes: Long, val count: Int, val unknownSizes: Int)

internal suspend fun readStorageUsage(context: Context, repository: SpixRepository): StorageUsage =
    withContext(Dispatchers.IO) {
        val manager = context.getSystemService(StorageStatsManager::class.java)
        val device = runCatching {
            val volume = StorageManager.UUID_DEFAULT
            val total = manager.getTotalBytes(volume)
            DeviceStorage(total, manager.getFreeBytes(volume).coerceIn(0, total))
        }.getOrNull() ?: runCatching {
            StatFs(context.filesDir.path).let { DeviceStorage(it.totalBytes, it.availableBytes) }
        }.getOrNull()
        val app = runCatching {
            manager.queryStatsForPackage(context.applicationInfo.storageUuid, context.packageName, Process.myUserHandle())
                .let { AppStorage(it.appBytes, (it.dataBytes - it.cacheBytes).coerceAtLeast(0), it.cacheBytes) }
        }.getOrNull()

        val uris = try {
            repository.savedMediaUris()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val downloads = uris?.let {
            var measuredBytes = 0L
            var unknownSizes = 0
            for (uri in uris) {
                ensureActive()
                val size = readSavedSize(context, Uri.parse(uri))
                if (size == null) unknownSizes++ else measuredBytes += size
            }
            DownloadStorage(measuredBytes, uris.size, unknownSizes)
        }
        StorageUsage(device, app, downloads)
    }

private fun readSavedSize(context: Context, uri: Uri): Long? {
    if (uri.scheme == "file") {
        return uri.path?.let(::File)?.takeIf { it.isFile }?.length()
    }
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0).takeIf { it >= 0 } else null
        }
    }.getOrNull()
}
