package com.ardex.dex.freeform;

/**
 * Runs shell commands under the privileged identity Shizuku hands us.
 *
 * When Shizuku's server runs as the ADB shell user (uid 2000) this service
 * inherits that identity, which is what carries WRITE_SECURE_SETTINGS. When it
 * runs as root, uid 0.
 *
 * Shizuku reserves 16777114 for destroy; it must keep that exact code or the
 * framework cannot tear the service down.
 */
interface IShellService {

    /** Reserved by Shizuku. Do not change the code. */
    void destroy() = 16777114;

    /** Ends the service process. */
    void exit() = 1;

    /**
     * Runs `command` via `sh -c` and returns stdout. Throws a RemoteException
     * to the caller when the command exits non-zero, so a failed write is
     * never mistaken for a successful one.
     */
    String exec(String command) = 2;
}
