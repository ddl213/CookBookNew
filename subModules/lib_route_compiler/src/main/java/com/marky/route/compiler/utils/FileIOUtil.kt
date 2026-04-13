package com.marky.route.compiler.utils

import java.io.BufferedInputStream
import java.io.BufferedWriter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileWriter
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException

object FileIOUtil {



    var sBufferSize: Int = 524288
    
    fun writeFileFromString(file: File?, content: String?): Boolean {
        return writeFileFromString(file, content, false)
    }

    fun writeFileFromString(
        file: File?,
        content: String?,
        append: Boolean
    ): Boolean {
        if (file == null || content == null) return false
        if (!createOrExistsFile(file)) {
            return false
        }
        var bw: BufferedWriter? = null
        try {
            bw = BufferedWriter(FileWriter(file, append))
            bw.write(content)
            return true
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        } finally {
            try {
                bw?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun createOrExistsFile(file: File?): Boolean {
        if (file == null) return false
        if (file.exists()) return file.isFile
        if (!createOrExistsDir(file.parentFile)) return false
        try {
            return file.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    fun createOrExistsDir(file: File?): Boolean {
        return file != null && (if (file.exists()) file.isDirectory else file.mkdirs())
    }

    fun readFile2String(filePath: String): String {
        return readFile2String(getFileByPath(filePath), null)
    }

    fun readFile2String(file: File?, charsetName: String?): String {
        val bytes: ByteArray = readFile2BytesByStream(file) ?: return ""
        if (isSpace(charsetName)) {
            return String(bytes)
        } else {
            try {
                return String(bytes, charset(charsetName!!))
            } catch (e: UnsupportedEncodingException) {
                e.printStackTrace()
                return ""
            }
        }
    }



    fun readFile2BytesByStream(
        file: File?,
    ): ByteArray? {
        if (!isFileExists(file)) return null
        try {
            var os: ByteArrayOutputStream? = null
            val `is`: InputStream =
                BufferedInputStream(FileInputStream(file), sBufferSize)
            try {
                os = ByteArrayOutputStream()
                val b = ByteArray(sBufferSize)
                var len: Int
                while ((`is`.read(b, 0, sBufferSize).also { len = it }) != -1) {
                    os.write(b, 0, len)
                }
                return os.toByteArray()
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            } finally {
                try {
                    `is`.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                try {
                    os?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
            return null
        }
    }
    fun isFileExists(file: File?): Boolean {
        if (file == null) return false
        if (file.exists()) {
            return true
        }
        return isFileExists(file.absolutePath)
    }

    fun isFileExists(filePath: String): Boolean {
        val file: File = getFileByPath(filePath)
            ?: return false
        if (file.exists()) {
            return true
        }
        return false
    }
    fun getFileByPath(filePath: String): File {
        return File(filePath)
    }

    fun isSpace(s: String?): Boolean {
        if (s == null) return true
        var i = 0
        val len = s.length
        while (i < len) {
            if (!Character.isWhitespace(s[i])) {
                return false
            }
            ++i
        }
        return true
    }
}