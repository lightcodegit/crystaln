package com.crystaln.vpn.desktop.net

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class OpenVpnCredentialTest {
    @Test
    fun noCredentialsMeansNoAuthUserPassOverride() {
        assertNull(openVpnCredentialText(null, null))
        assertNull(openVpnCredentialText("", " "))
    }

    @Test
    fun credentialsUseOneValuePerLineAndTrailingNewline() {
        assertEquals("alice\nsecret\n", openVpnCredentialText(" alice ", " secret "))
        assertEquals("alice\n\n", openVpnCredentialText("alice", null))
    }
}
