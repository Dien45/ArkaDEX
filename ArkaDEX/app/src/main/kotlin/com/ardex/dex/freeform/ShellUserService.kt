package com.ardex.dex.freeform

import android.os.RemoteException
import kotlin.system.exitProcess

/**
 * Runs in Shizuku's own process, so it carries the shell (or root) identity.
 *
 * This is the supported replacement for the removed `Shizuku.newProcess`:
 * rather than Shizuku spawning a shell for us, we ship our own small service
 * that Shizuku starts under its identity and talks to over binder.
 */
class ShellUserService : IShellService.Stub() {

    override fun exec(command: String): String {
        val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        process.waitFor()
        if (process.exitValue() != 0) {
            // Throwing across AIDL reaches the caller as a RemoteException,
            // which FreeformController turns into a null. Silently returning
            // an empty string here would read as "the setting was written".
            throw RemoteException("exit ${process.exitValue()}: ${stderr.trim()}")
        }
        return stdout
    }

    override fun exit() {
        destroy()
    }

    override fun destroy() {
        exitProcess(0)
    }
}
