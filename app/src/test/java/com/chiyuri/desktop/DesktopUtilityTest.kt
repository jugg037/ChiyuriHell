package com.chiyuri.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopUtilityTest {
    @Test
    fun archiveEntryNamesAreReducedToSafeLeafNames() {
        assertEquals("payload.txt", safeArchiveEntryName("../../payload.txt"))
        assertEquals("payload.txt", safeArchiveEntryName("folder/subfolder/payload.txt"))
        assertEquals("payload.txt", safeArchiveEntryName("""C:\outside\payload.txt"""))
    }

    @Test
    fun archivePathsPreserveSafeFoldersAndRejectTraversal() {
        assertEquals(listOf("folder", "sub", "file.txt"), safeArchiveEntryPath("folder/sub/file.txt"))
        assertEquals(listOf("folder", "file.txt"), safeArchiveEntryPath("folder//file.txt"))
        assertEquals(null, safeArchiveEntryPath("../escape.txt"))
        assertEquals(null, safeArchiveEntryPath("folder/../escape.txt"))
        assertEquals(null, safeArchiveEntryPath("/absolute.txt"))
        assertEquals(null, safeArchiveEntryPath("""C:\Windows\evil.txt"""))
        assertEquals(null, safeArchiveEntryPath((1..65).joinToString("/") { "x" }))
        assertEquals(null, safeArchiveEntryPath("a".repeat(256)))
        assertEquals(null, safeArchiveEntryPath("bad\u0000name"))
        assertEquals(null, safeArchiveEntryPath(""))
    }

    @Test
    fun emptyAndDirectoryOnlyArchiveNamesAreRejectedAsEmpty() {
        assertEquals("", safeArchiveEntryName(""))
        assertEquals("", safeArchiveEntryName("folder/"))
        assertEquals("", safeArchiveEntryName("../"))
    }

    @Test
    fun pinnedAppsCanBeReorderedWithoutChangingOtherItems() {
        assertEquals(listOf("b", "a", "c"), reorderPinnedPackages(listOf("a", "b", "c"), "b", -1))
        assertEquals(listOf("a", "c", "b"), reorderPinnedPackages(listOf("a", "b", "c"), "b", 1))
    }

    @Test
    fun pinReorderingKeepsBoundaryItemsInPlace() {
        assertEquals(listOf("a", "b"), reorderPinnedPackages(listOf("a", "b"), "a", -1))
        assertEquals(listOf("a", "b"), reorderPinnedPackages(listOf("a", "b"), "b", 1))
        assertEquals(listOf("a", "b"), reorderPinnedPackages(listOf("a", "b"), "missing", 1))
    }

    @Test
    fun rarMultipartSetsRequireEveryPartStartingAtOne() {
        assertEquals(true, isCompleteRarPartSequence(listOf(1)))
        assertEquals(true, isCompleteRarPartSequence(listOf(1, 2, 3)))
        assertEquals(false, isCompleteRarPartSequence(listOf(1, 3)))
        assertEquals(false, isCompleteRarPartSequence(listOf(2, 3)))
        assertEquals(false, isCompleteRarPartSequence(listOf(1, 1, 2)))
        assertEquals(false, isCompleteRarPartSequence(emptyList()))
    }

    @Test
    fun legacyRarVolumeSequenceStartsAtR00AndHasNoGaps() {
        assertEquals(true, isCompleteLegacyRarPartSequence(listOf(0)))
        assertEquals(true, isCompleteLegacyRarPartSequence(listOf(0, 1, 2)))
        assertEquals(true, isCompleteLegacyRarPartSequence((0..100).toList()))
        assertEquals(false, isCompleteLegacyRarPartSequence(listOf(0, 2)))
        assertEquals(false, isCompleteLegacyRarPartSequence(listOf(1, 2)))
        assertEquals(false, isCompleteLegacyRarPartSequence(emptyList()))
    }


    @Test
    fun fileAndWorkspaceNamesAreValidated() {
        assertTrue(isValidFileName("track.mp3"))
        assertFalse(isValidFileName("../track.mp3"))
        assertFalse(isValidFileName("""folder\track"""))
        assertFalse(isValidFileName("a".repeat(256)))
        assertFalse(isValidFileName("bad\u0000name"))
        assertTrue(isValidWorkspaceName("Рабочий стол"))
        assertFalse(isValidWorkspaceName(" "))
        assertFalse(isValidWorkspaceName("a".repeat(25)))
        assertFalse(isValidWorkspaceName("bad\u0000name"))
    }

    @Test
    fun dualPanelLayoutRequiresEnoughHorizontalSpace() {
        assertEquals(false, isWideDesktop(700))
        assertEquals(false, isWideDesktop(1023))
        assertEquals(true, isWideDesktop(1024))
        assertEquals(true, isWideDesktop(1280))
    }

    @Test
    fun fileSortModeCyclesAndPersistsAsAStableValue() {
        assertEquals("date", nextFileSortMode("name"))
        assertEquals("size", nextFileSortMode("date"))
        assertEquals("type", nextFileSortMode("size"))
        assertEquals("name", nextFileSortMode("type"))
        assertEquals("name", nextFileSortMode("unknown"))
    }

    @Test
    fun readableSizeUsesByteAndKilobyteUnits() {
        assertEquals("0 Б", readableSize(0))
        assertEquals("512 Б", readableSize(512))
        assertEquals("1 КБ", readableSize(1024))
    }
}
