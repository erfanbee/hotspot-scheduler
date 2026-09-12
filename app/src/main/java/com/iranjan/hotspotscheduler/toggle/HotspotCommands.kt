package com.iranjan.hotspotscheduler.toggle

object HotspotCommands {

    data class StateProbe(
        val on: Boolean,
        val ssid: String?,
        val open: Boolean?
    )

    enum class StartOutcome { STARTED, FAILED, UNKNOWN }

    const val DEFAULT_SSID = "AndroidAP"

    fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /** WPA2-PSK passphrases: 8..63 printable ASCII characters. */
    fun validPassphrase(passphrase: String): Boolean =
        passphrase.length in 8..63 && passphrase.all { it.code in 32..126 }

    /** AOSP: cmd wifi start-softap <ssid> (open|wpa2|wpa3|wpa3_transition|owe|owe_transition) <passphrase> */
    fun startSoftapCmd(ssid: String, passphrase: String): String? {
        if (!validPassphrase(passphrase)) return null
        val quotedSsid = shellQuote(ssid.trim().ifBlank { DEFAULT_SSID })
        return "cmd wifi start-softap $quotedSsid wpa2 ${shellQuote(passphrase)}"
    }

    fun startSoftapOpenCmd(ssid: String): String {
        val quotedSsid = shellQuote(ssid.trim().ifBlank { DEFAULT_SSID })
        return "cmd wifi start-softap $quotedSsid open"
    }

    /** AOSP stop-softap takes no argument. */
    fun stopSoftapCmd(): String = "cmd wifi stop-softap"

    /**
     * Grep-filtered dumpsys probe. `Dump of ActiveModeWarden` is always printed when
     * the dump is readable, which lets us tell "hotspot off" (no SoftApManager section)
     * apart from "probe failed" (empty/permission-denied output).
     */
    fun stateProbeCmd(): String =
        "dumpsys wifi | grep -E 'Dump of ActiveModeWarden|Dump of SoftApManager|mRole:|mIfaceIsUp:|mCurrentSoftApConfiguration:|Passphrase = |SecurityType = '"

    /** `cmd wifi start-softap` always exits 0, so success must be read from the output. */
    fun parseStartOutcome(output: String): StartOutcome = when {
        output.contains("SAP is enabled successfully") -> StartOutcome.STARTED
        output.contains("Soft AP failed to start") -> StartOutcome.FAILED
        output.contains("Unknown network type") -> StartOutcome.FAILED
        output.contains("Exception") -> StartOutcome.FAILED
        else -> StartOutcome.UNKNOWN
    }

    private val ssidRegex = Regex("ssid = \"(.*)\"")

    /**
     * Parse the grep-filtered dumpsys output. A tethered SoftApManager block with the
     * interface up means the hotspot is ON. Returns null only when the probe itself
     * failed (no warden header = unreadable dump).
     */
    fun parseStateProbe(output: String): StateProbe? {
        var sawWarden = false
        var sawManager = false
        var on = false
        var ssid: String? = null
        var open: Boolean? = null

        var blockTethered = false
        var blockUp = false
        var blockSsid: String? = null
        var blockOpen: Boolean? = null

        fun closeBlock() {
            if (blockTethered && blockUp) {
                on = true
                blockSsid?.let { ssid = it }
                blockOpen?.let { open = it }
            }
        }

        for (rawLine in output.lineSequence()) {
            val line = rawLine.trim()
            when {
                line.startsWith("Dump of ActiveModeWarden") -> sawWarden = true
                line.startsWith("Dump of SoftApManager") -> {
                    closeBlock()
                    sawManager = true
                    blockTethered = false
                    blockUp = false
                    blockSsid = null
                    blockOpen = null
                }
                line.startsWith("mRole:") && line.contains("ROLE_SOFTAP_TETHERED") -> blockTethered = true
                line.startsWith("mIfaceIsUp:") && line.endsWith("true") -> blockUp = true
                line.startsWith("mCurrentSoftApConfiguration:") ->
                    ssidRegex.find(line)?.let { blockSsid = it.groupValues[1] }
                line.startsWith("Passphrase =") ->
                    blockOpen = line.contains("<empty>")
                line.startsWith("SecurityType:") && blockOpen == null ->
                    blockOpen = line.endsWith("1")
            }
        }
        closeBlock()

        if (!sawWarden) return null
        if (!sawManager) return StateProbe(on = false, ssid = null, open = null)
        return StateProbe(on, ssid, open)
    }
}
