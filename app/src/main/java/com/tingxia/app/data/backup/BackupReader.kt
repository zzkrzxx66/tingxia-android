package com.tingxia.app.data.backup

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** Content providers may report available() == 0 for streams of any size. */
object BackupReader {
    fun readUtf8(input: InputStream, maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= maxBytes) { "备份文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toString("UTF-8")
    }
}
