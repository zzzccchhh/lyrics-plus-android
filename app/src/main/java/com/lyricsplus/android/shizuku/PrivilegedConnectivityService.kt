package com.lyricsplus.android.shizuku

import android.util.Log
import androidx.annotation.Keep
import java.util.concurrent.TimeUnit

@Keep
class PrivilegedConnectivityService : IPrivilegedConnectivityService.Stub() {
    override fun setXmsfNetworkingEnabled(enabled: Boolean): Boolean {
        return runCatching {
            if (enabled) {
                runCommand("cmd", "connectivity", "set-package-networking-enabled", "true", XMSF_PACKAGE)
                runCommand("cmd", "connectivity", "set-chain3-enabled", "false")
            } else {
                runCommand("cmd", "connectivity", "set-chain3-enabled", "true")
                runCommand("cmd", "connectivity", "set-package-networking-enabled", "false", XMSF_PACKAGE)
            }
            true
        }.onFailure {
            Log.w(TAG, "Failed to set XMSF networking enabled=$enabled", it)
        }.getOrDefault(false)
    }

    private fun runCommand(vararg command: String) {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(3, TimeUnit.SECONDS)
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (!finished) {
            process.destroyForcibly()
            throw IllegalStateException("Command timed out: ${command.joinToString(" ")}")
        }
        if (process.exitValue() != 0) {
            throw IllegalStateException("Command failed (${process.exitValue()}): ${command.joinToString(" ")} $output")
        }
        Log.d(TAG, "Command ok: ${command.joinToString(" ")} $output")
    }

    companion object {
        private const val TAG = "PrivConnectivity"
        private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
    }
}
