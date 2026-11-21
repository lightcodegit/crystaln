package com.crystaln.vpn.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Routing of pasted/shared links: only subscription-shaped links (http(s)
 * with a real path) go to the subscription flow. Proxy-style links and share
 * links keep their previous meaning.
 */
class SubscriptionsStoreTest {

    @Test
    fun subscriptionLinksReturnTheirUrl() {
        assertEquals(
            "https://sub.example.com/list",
            SubscriptionsStore.isSubscriptionUrl("https://sub.example.com/list"),
        )
        assertEquals(
            "https://sub.example.com/feed?token=abc",
            SubscriptionsStore.isSubscriptionUrl("https://sub.example.com/feed?token=abc"),
        )
    }

    @Test
    fun proxyLinksAreNotSubscriptions() {
        // Proxy-style links have no path — ShareLinkParser turns them into
        // proxy profiles, so they must not reach the subscription flow.
        assertNull(SubscriptionsStore.isSubscriptionUrl("https://example.com"))
        assertNull(SubscriptionsStore.isSubscriptionUrl("https://example.com/"))
        assertNull(SubscriptionsStore.isSubscriptionUrl("https://example.com:8443"))
    }

    @Test
    fun userinfoLinksAreNotSubscriptions() {
        // http(s) URLs with credentials are user:pass@host proxies in this app.
        assertNull(SubscriptionsStore.isSubscriptionUrl("https://user:pass@example.com:8080"))
        assertNull(SubscriptionsStore.isSubscriptionUrl("https://user:pass@example.com:8080/sub"))
    }

    @Test
    fun shareLinksAndGarbageAreNotSubscriptions() {
        assertNull(SubscriptionsStore.isSubscriptionUrl("vless://uuid@host:443?encryption=none"))
        assertNull(SubscriptionsStore.isSubscriptionUrl("trojan://pass@host:443"))
        assertNull(SubscriptionsStore.isSubscriptionUrl("   "))
        assertNull(SubscriptionsStore.isSubscriptionUrl(""))
        assertNull(SubscriptionsStore.isSubscriptionUrl("file:///etc/hosts"))
    }
}
