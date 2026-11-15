package com.crystaln.vpn.data

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Subscription refreshes must be idempotent: the same server appearing twice
 * in the feed must never stack duplicates, and servers that leave the feed
 * must be cleaned up — but never the user's manual profiles.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionMergeTest {

    private lateinit var context: Context
    private var subId: String? = null

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication() as Context
        // Both stores are JVM singletons; Robolectric gives a fresh filesDir
        // per test, so the caches must be reset too.
        ProfileStore.resetForTest()
        SubscriptionsStore.resetForTest()
        subId = SubscriptionsStore.add(context, "https://sub.example.com/list").id
    }

    private fun vlessProfile(host: String, uuid: String, name: String): Profile =
        Profile(name = name, protocol = Protocol.VLESS).apply {
            address = host
            port = 443
            fields["uuid"] = uuid
        }

    @Test
    fun refreshAddsProfilesAndMarksSubscriptionOwnership() {
        val result = ProfileStore.mergeProfiles(
            context,
            listOf(vlessProfile("a.example.com", "uuid-a", "A")),
            subscriptionId = subId,
        )
        assertEquals(1, result.added)
        val stored = ProfileStore.load(context)
        assertEquals(subId, stored.single().subscriptionId)
    }

    @Test
    fun secondRefreshUpdatesInsteadOfDuplicating() {
        ProfileStore.mergeProfiles(context, listOf(vlessProfile("a.example.com", "uuid-a", "A")), subId)
        // Same server and credentials, only the display name changed — the
        // existing profile is reused (identity = protocol+host+port+credentials).
        val second = vlessProfile("a.example.com", "uuid-a", "A renamed")
        val result = ProfileStore.mergeProfiles(context, listOf(second), subId)
        assertEquals(0, result.added)
        assertEquals(1, result.updated)
        val stored = ProfileStore.load(context)
        assertEquals(1, stored.size)
        assertEquals("A renamed", stored.single().name)
        // The profile keeps its stable id across refreshes.
        assertEquals(result.selectedProfile?.id, stored.single().id)
    }

    @Test
    fun refreshRemovesServersThatLeftTheFeed() {
        ProfileStore.mergeProfiles(
            context,
            listOf(vlessProfile("a.example.com", "uuid-a", "A"), vlessProfile("b.example.com", "uuid-b", "B")),
            subId,
        )
        val result = ProfileStore.mergeProfiles(
            context,
            listOf(vlessProfile("a.example.com", "uuid-a", "A")),
            subId,
        )
        assertEquals(1, result.removed)
        assertEquals(listOf("a.example.com"), ProfileStore.load(context).map { it.address })
    }

    @Test
    fun manualProfilesAreNeverOwnedBySubscription() {
        // Pre-existing manual profile with identical server data.
        ProfileStore.upsert(context, vlessProfile("a.example.com", "uuid-a", "Manual A"))
        val result = ProfileStore.mergeProfiles(
            context,
            listOf(vlessProfile("a.example.com", "uuid-a", "From feed")),
            subId,
        )
        assertEquals(1, result.updated)
        val stored = ProfileStore.load(context)
        assertEquals(1, stored.size)
        assertNull(stored.single().subscriptionId)
        assertEquals("From feed", stored.single().name)
    }

    @Test
    fun removingSubscriptionRemovesOnlyItsProfiles() {
        ProfileStore.mergeProfiles(context, listOf(vlessProfile("a.example.com", "uuid-a", "A")), subId)
        ProfileStore.upsert(context, vlessProfile("manual.example.com", "uuid-m", "Manual"))

        ProfileStore.removeBySubscription(context, subId!!)

        val stored = ProfileStore.load(context)
        assertEquals(listOf("manual.example.com"), stored.map { it.address })
    }

    @Test
    fun identityKeyIsStableAcrossRenaming() {
        val a = vlessProfile("a.example.com", "uuid-a", "Name 1")
        val b = vlessProfile("A.EXAMPLE.COM", "uuid-a", "Name 2")
        assertEquals(a.identityKey(), b.identityKey())

        // Different credentials = different server.
        val c = vlessProfile("a.example.com", "uuid-other", "Name 3")
        assertNotEquals(a.identityKey(), c.identityKey())

        // Under-identified profiles cannot collide.
        assertTrue(Profile(name = "empty", protocol = Protocol.VLESS).identityKey() == null)
    }
}
