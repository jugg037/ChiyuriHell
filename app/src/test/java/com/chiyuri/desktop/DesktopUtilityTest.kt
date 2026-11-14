package com.chiyuri.desktop

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopUtilityTest {
    @Test
    fun archiveEntryNamesAreReducedToSafeLeafNames() {
        assertEquals("payload.txt", safeArchiveEntryName("../../payload.txt"))
        assertEquals("payload.txt", safeArchiveEntryName("folder/subfolder/payload.txt"))
        assertEquals("payload.txt", safeArchiveEntryName("""C:\outside\payload.txt"""))
    }

    @Test
    fun emptyAndDirectoryOnlyArchiveNamesAreRejectedAsEmpty() {
        assertEquals("", safeArchiveEntryName(""))
        assertEquals("", safeArchiveEntryName("folder/"))
        assertEquals("", safeArchiveEntryName("../"))
    }

    @Test
    fun readableSizeUsesByteAndKilobyteUnits() {
        assertEquals("0 Б", readableSize(0))
        assertEquals("512 Б", readableSize(512))
        assertEquals("1 КБ", readableSize(1024))
    }
}
