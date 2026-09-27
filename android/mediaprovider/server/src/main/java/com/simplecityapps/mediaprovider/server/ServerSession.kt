package com.simplecityapps.mediaprovider.server

import android.content.Context
import com.simplecityapps.mediaprovider.FlowEvent
import com.simplecityapps.mediaprovider.MessageProgress
import com.simplecityapps.mediaprovider.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * The skeleton a server sync runs in: fails straight away when no server [address] is set, otherwise reports that
 * it's querying the server, signs in with [authenticate] and runs [body] with the session, or fails when signing in
 * does.
 */
fun <C : Any, T> withServerSession(
    context: Context,
    address: String?,
    authenticate: suspend (address: String) -> C?,
    body: suspend FlowCollector<FlowEvent<T, MessageProgress>>.(address: String, credentials: C) -> Unit
): Flow<FlowEvent<T, MessageProgress>> {
    if (address == null) {
        return flowOf(FlowEvent.Failure(context.getString(R.string.media_provider_address_missing)))
    }

    return flow {
        emit(FlowEvent.Progress(MessageProgress(context.getString(R.string.media_provider_querying_api), null)))
        val credentials = authenticate(address)
        if (credentials == null) {
            emit(FlowEvent.Failure(context.getString(R.string.media_provider_authentication_error)))
        } else {
            body(address, credentials)
        }
    }
}
