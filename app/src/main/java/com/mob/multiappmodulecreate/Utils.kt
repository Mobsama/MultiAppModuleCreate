package com.mob.multiappmodulecreate

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.Exception

object Utils {

    private const val BUFFER_LEN = 1024 * 10
    val DOWNLOAD_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    fun zipDir(dirPath: String, zipFilePath: String): Boolean {
        val dir = File(dirPath)
        val zipFile = File(zipFilePath)
        if (!dir.exists() && !dir.isDirectory && dir.listFiles().isNullOrEmpty())
            throw Exception("路径错误")
        if (zipFile.exists())
            throw Exception("压缩文件存在")
        return try {
            zipFiles(dir.listFiles()!!, zipFilePath)
            true
        } catch (ignored: Exception) {
            throw IOException("压缩错误")
        }
    }

    private fun zipFiles(files: Array<File>, zipPath: String) {
        var zos: ZipOutputStream? = null
        try {
            zos = ZipOutputStream(BufferedOutputStream(FileOutputStream(zipPath)))
            files.forEach{
                if (it.isDirectory) {
                    recursionZip(zos, it, it.name + File.separator)
                } else {
                    recursionZip(zos, it, "")
                }
            }
        } catch (ignored: Exception) {
            throw IOException("压缩错误")
        } finally {
            zos?.finish()
            zos?.close()
        }
    }

    private fun recursionZip(zos: ZipOutputStream, file: File, baseDir: String) {
        if (file.isDirectory) {
            file.listFiles()?.forEach {
                if (it.isDirectory) {
                    val dir = baseDir + it.name + File.separator
                    recursionZip(zos, it, dir)
                } else {
                    recursionZip(zos, it, baseDir)
                }
            }
        } else {
            val buffers = ByteArray(BUFFER_LEN)
            zos.putNextEntry(ZipEntry(baseDir + file.name))
            val bis = BufferedInputStream(FileInputStream(file), BUFFER_LEN)
            var read = bis.read(buffers,0, BUFFER_LEN)
            while (read > 0) {
                zos.write(buffers, 0, read)
                read = bis.read(buffers,0, BUFFER_LEN)
            }
            bis.close()
        }
    }

    fun copyAssetsFile2AnyWhere(context: Context, assetsName: String, path: String ) {
        var fos: FileOutputStream? = null
        var inputStream: InputStream? = null
        try {
            val pathDir = File(path)
            if (!pathDir.exists()) {
                pathDir.mkdirs()
            }
            val file = File("$path/$assetsName")
            if (file.exists()) throw Exception("${file.path}文件存在")
            inputStream = context.resources.assets.open(assetsName)
            fos = FileOutputStream(file)
            val buffer = ByteArray(BUFFER_LEN)
            var read = inputStream.read(buffer)
            while (read > 0) {
                fos.write(buffer, 0, read)
                read = inputStream.read(buffer)
            }
        } catch (ignored: Exception) {
            throw IOException("复制错误$assetsName")
        } finally {
            fos?.close()
            inputStream?.close()
        }
    }


    fun copyFileToDownloads(context: Context, downloadedFile: File): Uri? {
        val resolver = context.contentResolver

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, downloadedFile.name)
            put(MediaStore.MediaColumns.MIME_TYPE, Files.probeContentType(Paths.get(downloadedFile.toURI())))
            put(MediaStore.MediaColumns.SIZE, downloadedFile.length())
        }
        return try {
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?.also { downloadedUri ->
                    resolver.openOutputStream(downloadedUri).use { outputStream ->
                        val brr = ByteArray(1024)
                        var len: Int
                        val bufferedInputStream = BufferedInputStream(FileInputStream(downloadedFile.absoluteFile))
                        while ((bufferedInputStream.read(brr, 0, brr.size).also { len = it }) != -1) {
                            outputStream?.write(brr, 0, len)
                        }
                        outputStream?.flush()
                        bufferedInputStream.close()
                    }
                }
        } catch (ignored: Exception) {
            throw IOException("Download目录下有同名文件")
        }
    }
}