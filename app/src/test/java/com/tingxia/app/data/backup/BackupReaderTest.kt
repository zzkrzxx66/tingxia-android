package com.tingxia.app.data.backup

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class BackupReaderTest {
    @Test fun rejectsOversizeEvenWhenProviderReportsZeroAvailableBytes() {
        val input = object : ByteArrayInputStream(ByteArray(100)) { override fun available() = 0 }
        assertTrue(runCatching { BackupReader.readUtf8(input, 50) }.exceptionOrNull() is IllegalArgumentException)
    }
    @Test fun boundCountsBytesAndPreservesMultibyteText() {
        val encoded = "中文备份".toByteArray(Charsets.UTF_8)
        assertEquals("中文备份", BackupReader.readUtf8(ByteArrayInputStream(encoded), encoded.size))
        assertTrue(runCatching { BackupReader.readUtf8(ByteArrayInputStream(encoded), encoded.size - 1) }.exceptionOrNull() is IllegalArgumentException)
    }
}
