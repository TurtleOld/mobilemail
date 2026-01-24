package com.mobilemail.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object FileManager {
    suspend fun saveToDownloads(
        context: Context,
        filename: String,
        data: ByteArray,
        mimeType: String = "application/octet-stream"
    ): com.mobilemail.data.common.Result<Uri> = withContext(Dispatchers.IO) {
        com.mobilemail.data.common.runCatchingSuspend {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveToDownloadsApi29Plus(context, filename, data, mimeType)
            } else {
                saveToDownloadsLegacy(filename, data)
            }
            Log.d("FileManager", "Файл сохранен: $uri")
            uri
        }
    }

    @Suppress("DEPRECATION")
    private fun saveToDownloadsLegacy(filename: String, data: ByteArray): Uri {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }
        
        val file = File(downloadsDir, filename)
        var counter = 1
        var finalFile = file
        while (finalFile.exists()) {
            val nameWithoutExt = file.nameWithoutExtension
            val ext = file.extension
            finalFile = File(downloadsDir, "$nameWithoutExt ($counter).$ext")
            counter++
        }
        
        FileOutputStream(finalFile).use { out ->
            out.write(data)
        }
        
        return Uri.fromFile(finalFile)
    }

    private fun saveToDownloadsApi29Plus(
        context: Context,
        filename: String,
        data: ByteArray,
        mimeType: String
    ): Uri {
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        
        var uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        
        if (uri == null) {
            val nameWithoutExt = filename.substringBeforeLast(".")
            val ext = filename.substringAfterLast(".", "")
            var counter = 1
            do {
                contentValues.put(MediaStore.Downloads.DISPLAY_NAME, "$nameWithoutExt ($counter).$ext")
                uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                counter++
            } while (uri == null && counter < 100)
        }
        
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                out.write(data)
            }
        } ?: throw IllegalStateException("Не удалось создать файл в Downloads")
        
        return uri
    }

    fun getFilePath(context: Context, uri: Uri): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val filename = cursor.getString(nameIndex)
                    "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)}/$filename"
                } else {
                    uri.toString()
                }
            } ?: uri.toString()
        } else {
            uri.path ?: uri.toString()
        }
    }
    
    suspend fun saveToCache(
        context: Context,
        filename: String,
        data: ByteArray,
        mimeType: String = "application/octet-stream"
    ): com.mobilemail.data.common.Result<Uri> = withContext(Dispatchers.IO) {
        com.mobilemail.data.common.runCatchingSuspend {
            val cacheDir = context.cacheDir
            val extension = mimeType.substringAfter('/', "").substringBefore(';').ifBlank { "bin" }
            val normalizedExtension = if (extension == "octet-stream") "bin" else extension
            val safeFilename = if (filename.contains('.')) filename else "$filename.$normalizedExtension"
            val file = File(cacheDir, safeFilename)
            
            var counter = 1
            var finalFile = file
            while (finalFile.exists()) {
                val nameWithoutExt = file.nameWithoutExtension
                val ext = file.extension
                finalFile = File(cacheDir, "$nameWithoutExt ($counter).$ext")
                counter++
            }
            
            FileOutputStream(finalFile).use { out ->
                out.write(data)
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                finalFile
            )
            
            Log.d("FileManager", "Временный файл создан: $uri")
            uri
        }
    }
}
