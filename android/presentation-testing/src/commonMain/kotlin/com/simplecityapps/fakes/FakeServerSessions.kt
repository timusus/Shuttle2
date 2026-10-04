package com.simplecityapps.fakes

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.shell.ServerSessions
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeServerSessions : ServerSessions {
    private val _expired = MutableSharedFlow<MediaProviderType>(extraBufferCapacity = 8)
    override val expired = _expired

    fun expire(type: MediaProviderType) {
        _expired.tryEmit(type)
    }
}
