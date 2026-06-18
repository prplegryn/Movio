package com.prplegryn.movio.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class ImageCache(context: Context) {
    private val imageDir = File(context.filesDir, "tmdb_images").also { it.mkdirs() }
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun model(path: String, size: String): String {
        if (path.isBlank()) return ""
        val file = imageFile(path, size)
        return if (file.exists() && file.length() > 0L) {
            Uri.fromFile(file).toString()
        } else {
            remoteUrl(path, size)
        }
    }

    suspend fun prefetch(
        groups: List<MediaGroup>,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ) {
        val targets = groups.imageTargets()
        if (targets.isEmpty()) return
        withContext(Dispatchers.IO) {
            targets.forEachIndexed { index, target ->
                downloadIfMissing(target.path, target.size)
                onProgress(index + 1, targets.size)
            }
        }
    }

    private fun downloadIfMissing(path: String, size: String) {
        val file = imageFile(path, size)
        if (file.exists() && file.length() > 0L) return
        val tmp = File(file.parentFile, "${file.name}.tmp")
        runCatching {
            val request = Request.Builder().url(remoteUrl(path, size)).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body ?: return
                tmp.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
                if (tmp.length() > 0L) {
                    tmp.renameTo(file)
                } else {
                    tmp.delete()
                }
            }
        }.onFailure {
            tmp.delete()
        }
    }

    private fun imageFile(path: String, size: String): File =
        File(imageDir, "${size}_${path.sha1()}.img")

    private fun remoteUrl(path: String, size: String): String =
        "https://image.tmdb.org/t/p/$size$path"
}

private data class ImageTarget(
    val path: String,
    val size: String,
)

private fun List<MediaGroup>.imageTargets(): List<ImageTarget> {
    val targets = mutableListOf<ImageTarget>()
    fun add(path: String, vararg sizes: String) {
        if (path.isBlank()) return
        sizes.forEach { size -> targets += ImageTarget(path, size) }
    }
    forEach { group ->
        group.tmdb?.let { hit ->
            add(hit.posterPath, "w185", "w342")
            add(hit.backdropPath, "w500", "w780", "w1280")
            hit.cast.forEach { person ->
                add(person.profilePath, "w185")
            }
        }
        group.seasons.forEach { season ->
            add(season.posterPath, "w342", "w780")
            add(season.backdropPath, "w780", "w1280")
        }
        group.episodes.forEach { episode ->
            add(episode.tmdb?.stillPath.orEmpty(), "w300")
        }
    }
    return targets.distinct()
}

private fun String.sha1(): String {
    val digest = MessageDigest.getInstance("SHA-1").digest(toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
