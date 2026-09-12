package com.iranjan.hotspotscheduler.toggle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HotspotCommandsTest {

    @Test
    fun `start command has correct aosp syntax`() {
        val cmd = HotspotCommands.startSoftapCmd("MyHotspot", "password123")!!
        assertEquals("cmd wifi start-softap 'MyHotspot' wpa2 'password123'", cmd)
    }

    @Test
    fun `ssid and passphrase are shell-quoted`() {
        val cmd = HotspotCommands.startSoftapCmd("it's net", "p4ss'word")!!
        assertEquals("cmd wifi start-softap 'it'\\''s net' wpa2 'p4ss'\\''word'", cmd)
    }

    @Test
    fun `blank ssid falls back to default`() {
        val cmd = HotspotCommands.startSoftapCmd("  ", "password123")!!
        assertEquals("cmd wifi start-softap 'AndroidAP' wpa2 'password123'", cmd)
    }

    @Test
    fun `short passphrase is rejected`() {
        assertNull(HotspotCommands.startSoftapCmd("ssid", "short"))
    }

    @Test
    fun `too long passphrase is rejected`() {
        assertNull(HotspotCommands.startSoftapCmd("ssid", "x".repeat(64)))
    }

    @Test
    fun `non ascii passphrase is rejected`() {
        assertNull(HotspotCommands.startSoftapCmd("ssid", "passwörd123"))
    }

    @Test
    fun `63-char passphrase is valid`() {
        assertNotNull(HotspotCommands.startSoftapCmd("ssid", "x".repeat(63)))
    }

    @Test
    fun `stop command takes no argument`() {
        assertEquals("cmd wifi stop-softap", HotspotCommands.stopSoftapCmd())
    }

    @Test
    fun `start outcome detects enabled`() {
        assertEquals(
            HotspotCommands.StartOutcome.STARTED,
            HotspotCommands.parseStartOutcome("onStateChanged with state: 13\n SAP is enabled successfully")
        )
    }

    @Test
    fun `start outcome detects explicit failure`() {
        assertEquals(
            HotspotCommands.StartOutcome.FAILED,
            HotspotCommands.parseStartOutcome("Soft AP failed to start. Please check config parameters")
        )
    }

    @Test
    fun `start outcome detects exception`() {
        assertEquals(
            HotspotCommands.StartOutcome.FAILED,
            HotspotCommands.parseStartOutcome("Exception: Unknown network type wpa2-psk")
        )
    }

    @Test
    fun `start outcome unknown for empty output`() {
        assertEquals(
            HotspotCommands.StartOutcome.UNKNOWN,
            HotspotCommands.parseStartOutcome("")
        )
    }

    @Test
    fun `state probe parses tethered and up manager`() {
        val dump = """
            Dump of ActiveModeWarden
            Current wifi mode: EnabledState
            Dump of SoftApManager id=1
            current StateMachine mode: StartedState
            mRole: ROLE_SOFTAP_TETHERED
            mApInterfaceName: swlan0
            mIfaceIsUp: true
            mCurrentSoftApConfiguration: ssid = "Galaxy A22" 
             Passphrase = <non-empty> 
             SecurityType = 2 
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(dump)
        assertNotNull(probe)
        assertTrue(probe!!.on)
        assertEquals("Galaxy A22", probe.ssid)
        assertEquals(false, probe.open)
    }

    @Test
    fun `state probe parses open network`() {
        val dump = """
            Dump of ActiveModeWarden
            Dump of SoftApManager id=1
            mRole: ROLE_SOFTAP_TETHERED
            mIfaceIsUp: true
            mCurrentSoftApConfiguration: ssid = "FreeAP" 
             Passphrase = <empty> 
             SecurityType = 1 
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(dump)
        assertNotNull(probe)
        assertTrue(probe!!.on)
        assertEquals(true, probe.open)
    }

    @Test
    fun `state probe ignores local-only manager`() {
        val dump = """
            Dump of ActiveModeWarden
            Dump of SoftApManager id=1
            current StateMachine mode: StartedState
            mRole: ROLE_SOFTAP_LOCAL_ONLY
            mApInterfaceName: ap0
            mIfaceIsUp: true
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(dump)
        assertNotNull(probe)
        assertFalse(probe!!.on)
    }

    @Test
    fun `state probe reports off when no manager exists`() {
        val dump = """
            Dump of ActiveModeWarden
            Current wifi mode: EnabledState
            NumActiveModeManagers: 0
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(dump)
        assertNotNull(probe)
        assertFalse(probe!!.on)
    }

    @Test
    fun `state probe returns null when dump is empty or permission denied`() {
        assertNull(HotspotCommands.parseStateProbe(""))
        assertNull(HotspotCommands.parseStateProbe("Permission Denial: can't dump WifiService"))
    }

    @Test
    fun `state probe handles multiple managers`() {
        val dump = """
            Dump of ActiveModeWarden
            Dump of SoftApManager id=1
            mRole: ROLE_SOFTAP_LOCAL_ONLY
            mIfaceIsUp: true
            Dump of SoftApManager id=2
            mRole: ROLE_SOFTAP_TETHERED
            mIfaceIsUp: true
            mCurrentSoftApConfiguration: ssid = "MyAP" 
             Passphrase = <non-empty> 
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(dump)
        assertNotNull(probe)
        assertTrue(probe!!.on)
        assertEquals("MyAP", probe.ssid)
    }

    @Test
    fun `state probe requires warden header`() {
        val dump = """
            Dump of SoftApManager id=1
            mRole: ROLE_SOFTAP_TETHERED
            mIfaceIsUp: true
        """.trimIndent()
        assertNull(HotspotCommands.parseStateProbe(dump))
    }

    @Test
    fun `security type 1 alone means open when passphrase line missing`() {
        val dump = """
            Dump of ActiveModeWarden
            Dump of SoftApManager id=1
            mRole: ROLE_SOFTAP_TETHERED
            mIfaceIsUp: true
            mCurrentSoftApConfiguration: ssid = "AP2" 
             SecurityType = 1 
        """.trimIndent()
        val probe = HotspotCommands.parseStateProbe(dump)
        assertNotNull(probe)
        assertTrue(probe!!.on)
        assertEquals(true, probe.open)
    }
}
