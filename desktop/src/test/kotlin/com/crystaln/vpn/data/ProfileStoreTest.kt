package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProfileStoreTest {

    private fun tmp(): File {
        val dir = File.createTempFile("crystaln-store", ".dir")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    private fun vlessProfile(name: String, host: String = "h.io", port: Int = 443): Profile =
        Profile(name = name, protocol = Protocol.VLESS, address = host, port = port)
            .apply { fields["uuid"] = "fixed-uuid" }

    @Test
    fun `upsert, select and reload from disk`() {
        val dir = tmp()
        ProfileStore.resetForTest()
        val p = vlessProfile("первый")
        ProfileStore.upsert(dir, p)
        ProfileStore.select(dir, p.id)

        // Fresh load from the same directory (simulating app restart).
        ProfileStore.resetForTest()
        val list = ProfileStore.load(dir)
        assertEquals(1, list.size)
        assertEquals("первый", list[0].name)
        assertEquals(p.id, ProfileStore.selectedId)
        assertEquals(p.id, ProfileStore.selected(dir)?.id)
    }

    @Test
    fun `remove clears selection`() {
        val dir = tmp()
        ProfileStore.resetForTest()
        val a = vlessProfile("a")
        val b = vlessProfile("b")
        ProfileStore.upsert(dir, a)
        ProfileStore.upsert(dir, b)
        ProfileStore.select(dir, a.id)
        ProfileStore.remove(dir, a.id)
        assertEquals(b.id, ProfileStore.selectedId)
        ProfileStore.remove(dir, b.id)
        assertNull(ProfileStore.selectedId)
    }

    @Test
    fun `merge is idempotent by identity key`() {
        val dir = tmp()
        ProfileStore.resetForTest()
        val first = ProfileStore.mergeProfiles(dir, listOf(vlessProfile("srv")), null)
        assertEquals(1, first.added)

        // Same server + same credential → update, not duplicate.
        val second = ProfileStore.mergeProfiles(dir, listOf(vlessProfile("srv (обновлён)")), null)
        assertEquals(0, second.added)
        assertEquals(1, second.updated)
        assertEquals(1, ProfileStore.load(dir).size)
        assertEquals("srv (обновлён)", ProfileStore.load(dir)[0].name)
    }

    @Test
    fun `subscription merge owns and prunes profiles`() {
        val dir = tmp()
        ProfileStore.resetForTest()
        val manual = vlessProfile("manual")
        ProfileStore.upsert(dir, manual)

        val sub1 = vlessProfile("s1", host = "sub1.io")
        val r1 = ProfileStore.mergeProfiles(dir, listOf(sub1), "sub-1")
        assertEquals(1, r1.added)
        assertEquals("sub-1", ProfileStore.load(dir).last { it.id != manual.id }.subscriptionId)

        // Refresh drops servers that disappeared from the feed.
        val sub2 = vlessProfile("s2", host = "sub2.io")
        ProfileStore.mergeProfiles(dir, listOf(sub2), "sub-1")
        val after = ProfileStore.load(dir)
        assertFalse(after.any { it.address == "sub1.io" })
        assertTrue(after.any { it.address == "sub2.io" })

        // Deleting the subscription removes its profiles but not manual ones.
        ProfileStore.removeBySubscription(dir, "sub-1")
        val list = ProfileStore.load(dir)
        assertEquals(listOf("manual"), list.map { it.name })
    }

    @Test
    fun `corrupt file is preserved and store starts empty`() {
        val dir = tmp()
        File(dir, "profiles.json").writeText("{ this is not json")
        ProfileStore.resetForTest()
        val list = ProfileStore.load(dir)
        assertTrue(list.isEmpty())
        assertTrue(File(dir, "profiles.json.corrupt").exists())
        // A later save must not resurrect the corrupt content.
        ProfileStore.upsert(dir, vlessProfile("ok"))
        ProfileStore.resetForTest()
        assertEquals(listOf("ok"), ProfileStore.load(dir).map { it.name })
    }
}
