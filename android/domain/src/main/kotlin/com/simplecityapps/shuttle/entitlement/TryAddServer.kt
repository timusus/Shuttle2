package com.simplecityapps.shuttle.entitlement

/** Whether a remote server's sign-in may open; false once the gate has sent the user to the paywall instead. */
fun interface TryAddServer {
    operator fun invoke(): Boolean
}
