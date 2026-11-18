package com.ruben.spotitube.playback

import androidx.media3.common.MediaItem
import com.ruben.spotitube.extensions.metadata
import com.ruben.spotitube.models.MediaMetadata
import com.ruben.spotitube.utils.reportException
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class PersistentStateStore(@PublishedApi internal val filesDir: File) {
    @PublishedApi
    internal val persistentStateLock = Any()

    inline fun <reified T> readPersistentObject(fileName: String): T? {
        val persistentFile = filesDir.resolve(fileName)
        if (!persistentFile.exists() || !persistentFile.isFile) return null

        return synchronized(persistentStateLock) {
            runCatching {
                persistentFile.inputStream().use { fis ->
                    ObjectInputStream(fis).use { input ->
                        input.readObject() as? T
                    }
                }
            }.onFailure {
                Timber.tag("MusicService").w(it, "Failed to read persistent file: $fileName")
                runCatching { persistentFile.delete() }
            }.getOrNull()
        }
    }

    fun writePersistentObject(fileName: String, payload: Serializable) {
        val persistentFile = filesDir.resolve(fileName)
        val tempFile = filesDir.resolve("$fileName.tmp")

        synchronized(persistentStateLock) {
            runCatching {
                FileOutputStream(tempFile).use { fos ->
                    ObjectOutputStream(fos).use { output ->
                        output.writeObject(payload)
                        output.flush()
                    }
                    runCatching { fos.fd.sync() }
                }

                if (persistentFile.exists() && !persistentFile.delete()) {
                    error("Could not replace $fileName")
                }
                if (!tempFile.renameTo(persistentFile)) {
                    error("Could not atomically move $fileName")
                }
            }.onFailure {
                runCatching { tempFile.delete() }
                reportException(it)
            }
        }
    }
}

fun MediaItem.toPersistableMetadata(): MediaMetadata? {
    val tagged = metadata
    if (tagged != null) return tagged

    val id =
        mediaId.trim().ifBlank {
            localConfiguration?.uri?.toString()?.trim().orEmpty()
        }.takeIf { it.isNotBlank() } ?: return null

    val title =
        mediaMetadata.title?.toString()?.trim().takeIf { !it.isNullOrBlank() }
            ?: id

    val artistText =
        mediaMetadata.artist?.toString()?.trim().takeIf { !it.isNullOrBlank() }
            ?: mediaMetadata.subtitle?.toString()?.trim().takeIf { !it.isNullOrBlank() }

    val artists =
        artistText
            ?.split(",")
            ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
            ?.map { name -> MediaMetadata.Artist(id = null, name = name) }
            .orEmpty()

    val thumbnailUrl = mediaMetadata.artworkUri?.toString()
    val albumTitle = mediaMetadata.albumTitle?.toString()?.trim().takeIf { !it.isNullOrBlank() }
    val album =
        albumTitle?.let { titleValue ->
            MediaMetadata.Album(id = titleValue, title = titleValue)
        }

    return MediaMetadata(
        id = id,
        title = title,
        artists = artists,
        duration = -1,
        thumbnailUrl = thumbnailUrl,
        album = album,
        explicit = false,
    )
}
