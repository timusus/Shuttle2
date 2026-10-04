package com.simplecityapps.shuttle.shared.platform

import com.simplecityapps.shuttle.model.MediaProviderType
import com.simplecityapps.shuttle.ui.shell.ServerSessions
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Never reports an expired session: iOS has no shell-level alert or server sign-in view yet, so the sign-out
 * prompt (#595) is Android-only for now, and `ShellViewModel` has nothing to collect that nothing would consume.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosServerSessions @Inject constructor() : ServerSessions {
    override val expired: Flow<MediaProviderType> = emptyFlow()
}
