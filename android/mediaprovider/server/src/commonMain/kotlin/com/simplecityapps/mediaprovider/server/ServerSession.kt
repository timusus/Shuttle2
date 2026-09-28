package com.simplecityapps.mediaprovider.server

import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.ImportPhase
import com.simplecityapps.mediaprovider.MessageProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * The skeleton a server sync runs in: fails straight away when no server [address] is set, otherwise reports that
 * it's connecting, signs in with [authenticate] and runs [body] with the session, or fails when signing in
 * does.
 */
fun <C : Any, T> withServerSession(
    strings: ServerStrings,
    address: String?,
    authenticate: suspend (address: String) -> C?,
    body: suspend FlowCollector<FlowEvent<T, MessageProgress>>.(address: String, credentials: C) -> Unit
): Flow<FlowEvent<T, MessageProgress>> {
    if (address == null) {
        return flowOf(FlowEvent.Failure(strings.addressMissing))
    }

    return flow {
        emit(FlowEvent.Progress(MessageProgress(ImportPhase.Connecting, progress = null)))
        val credentials = authenticate(address)
        if (credentials == null) {
            emit(FlowEvent.Failure(strings.authenticationError))
        } else {
            body(address, credentials)
        }
    }
}
