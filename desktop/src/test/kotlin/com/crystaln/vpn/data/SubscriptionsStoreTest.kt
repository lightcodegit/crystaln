package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SubscriptionsStoreTest {

    private fun tmp(): File {
        val dir = File.createTempFile("crystaln-subs", ".dir")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    @Test
    fun `add, persist and reload`() {
        val dir = tmp()
        SubscriptionsStore.resetForTest()
        val sub = SubscriptionsStore.add(dir, "https://example.com/sub?token=1")
        SubscriptionsStore.setStatus(dir, sub.id, true)

        SubscriptionsStore.resetForTest()
        val loaded = SubscriptionsStore.load(dir)
        assertEquals(1, loaded.size)
        assertEquals("https://example.com/sub?token=1", loaded[0].url)
        assertEquals("example.com", loaded[0].name)
        assertEquals(true, loaded[0].lastResult)
        assertTrue(loaded[0].lastUpdated > 0)
    }

    @Test
    fun `same url twice reuses the subscription`() {
        val dir = tmp()
        SubscriptionsStore.resetForTest()
        val a = SubscriptionsStore.add(dir, "https://example.com/sub")
        val b = SubscriptionsStore.add(dir, "https://example.com/sub")
        assertEquals(a.id, b.id)
        assertEquals(1, SubscriptionsStore.load(dir).size)
    }

    @Test
    fun `invalid url is rejected`() {
        val dir = tmp()
        SubscriptionsStore.resetForTest()
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionsStore.add(dir, "ftp://nope")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SubscriptionsStore.add(dir, "не ссылка")
        }
        assertTrue(SubscriptionsStore.load(dir).isEmpty())
    }

    @Test
    fun `setStatus records failure with message`() {
        val dir = tmp()
        SubscriptionsStore.resetForTest()
        val sub = SubscriptionsStore.add(dir, "https://example.com/s")
        SubscriptionsStore.setStatus(dir, sub.id, false, "timeout")
        val s = SubscriptionsStore.byId(dir, sub.id)!!
        assertFalse(s.lastResult!!)
        assertEquals("timeout", s.lastError)
    }

    @Test
    fun `remove and update`() {
        val dir = tmp()
        SubscriptionsStore.resetForTest()
        val sub = SubscriptionsStore.add(dir, "https://example.com/s")
        SubscriptionsStore.update(dir, sub.copy(enabled = false, intervalHours = 12))
        assertEquals(false, SubscriptionsStore.byId(dir, sub.id)!!.enabled)
        SubscriptionsStore.remove(dir, sub.id)
        assertNull(SubscriptionsStore.byId(dir, sub.id))
    }
}
