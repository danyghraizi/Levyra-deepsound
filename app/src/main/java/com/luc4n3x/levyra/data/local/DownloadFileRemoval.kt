package com.luc4n3x.levyra.data.local

import android.content.Context
import android.net.Uri
import java.io.File

internal fun deleteDownloadedMediaFile(context: Context, uriString: String): Boolean {
    if (uriString.isBlank()) return true
    return runCatching {
        val uri = Uri.parse(uriString)
        when (uri.scheme?.lowercase()) {
            "content" -> {
                val resolver = context.contentResolver
                val deletedRows = resolver.delete(uri, null, null)
                if (deletedRows > 0) {
                    true
                } else {
                    resolver.openAssetFileDescriptor(uri, "r")?.use { false } ?: true
                }
            }
            "file" -> uri.path?.let(::File)?.let { file -> !file.exists() || file.delete() } ?: true
            else -> File(uriString).let { file -> !file.exists() || file.delete() }
        }
    }.getOrDefault(false)
}
