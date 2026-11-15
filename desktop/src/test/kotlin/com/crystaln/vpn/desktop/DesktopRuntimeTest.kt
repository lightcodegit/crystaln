package com.crystaln.vpn.desktop

import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runtime environment sanity: on the JVM Dispatchers.Main resolves through
 * a service loader (kotlinx-coroutines-swing for Compose Desktop). If the
 * module is missing, every withContext(Dispatchers.Main) in the GUI throws
 * "Module with the Main dispatcher is missing" — as seen from a background
 * coroutine inside a try/catch-free path it would surface as an error dialog.
 */
class DesktopRuntimeTest {

    @Test
    fun `swing Main dispatcher is on the classpath`() {
        val main = try {
            Dispatchers.Main
        } catch (t: Throwable) {
            throw AssertionError(
                "Dispatchers.Main недоступен: добавьте kotlinx-coroutines-swing в зависимости desktop",
                t,
            )
        }
        assertFalse(main === Dispatchers.Default)
        assertTrue(main !== Dispatchers.IO)
    }
}
