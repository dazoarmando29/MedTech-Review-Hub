package com.medtech.reviewhub

import com.medtech.reviewhub.domain.Semver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemverTest {

    @Test
    fun `parses plain versions`() {
        assertEquals(Semver(1, 2, 3), Semver.parse("1.2.3"))
        assertEquals(Semver(1, 0, 0), Semver.parse("v1.0.0"))
        assertEquals(Semver(2, 3, 0), Semver.parse("2.3"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(Semver.parse(""))
        assertNull(Semver.parse("abc"))
        assertNull(Semver.parse("1.x.3"))
    }

    @Test
    fun `numeric comparison not lexicographic`() {
        assertTrue(Semver.parse("1.10.0")!! > Semver.parse("1.9.9")!!)
        assertTrue(Semver.parse("2.0.0")!! > Semver.parse("1.99.99")!!)
    }

    @Test
    fun `release outranks prerelease`() {
        assertTrue(Semver.parse("1.0.0")!! > Semver.parse("1.0.0-beta")!!)
    }

    @Test
    fun `isNewer for update checker`() {
        assertTrue(Semver.isNewer("1.0.1", "1.0.0"))
        assertTrue(Semver.isNewer("v1.1.0", "1.0.9"))
        assertFalse(Semver.isNewer("1.0.0", "1.0.0"))
        assertFalse(Semver.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun `parse strips build metadata`() {
        assertEquals(Semver(1, 2, 3), Semver.parse("1.2.3+build.5"))
    }

    @Test
    fun `toString round trip`() {
        assertEquals("1.2.3", Semver.parse("v1.2.3").toString())
    }
}
