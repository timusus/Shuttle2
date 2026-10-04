package com.simplecityapps.shuttle.scrobbling.di

import com.simplecityapps.shuttle.scrobbling.FinishLastFmSignIn
import com.simplecityapps.shuttle.scrobbling.IsLastFmConfigured
import com.simplecityapps.shuttle.scrobbling.ObserveLastFmAccount
import com.simplecityapps.shuttle.scrobbling.SignOutOfLastFm
import com.simplecityapps.shuttle.scrobbling.StartLastFmSignIn
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmAuthenticator
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmCredentials
import com.simplecityapps.shuttle.scrobbling.lastfm.LastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.lastfm.SecurePreferenceLastFmSessionStore
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDao
import com.simplecityapps.shuttle.scrobbling.queue.ScrobbleDatabase
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

/**
 * Scrobbling's platform-free bindings. Each platform provides the rest: the [ScrobbleDatabase], the
 * [com.simplecityapps.shuttle.scrobbling.lastfm.LastFmApi], the [LastFmCredentials] and a
 * [com.simplecityapps.shuttle.scrobbling.queue.ScrobbleFlushScheduler] (AndroidScrobblingModule and
 * WorkManagerScrobbleFlushScheduler on Android, `:shared`'s IosScrobblingModule on iOS).
 */
@BindingContainer
@ContributesTo(AppScope::class)
abstract class ScrobblingBindingsModule {
    @Binds
    abstract fun bindLastFmSessionStore(impl: SecurePreferenceLastFmSessionStore): LastFmSessionStore
}

@BindingContainer
@ContributesTo(AppScope::class)
object ScrobblingModule {
    @Provides
    fun provideScrobbleDao(database: ScrobbleDatabase): ScrobbleDao = database.scrobbleDao()

    @Provides
    fun provideIsLastFmConfigured(credentials: LastFmCredentials): IsLastFmConfigured = IsLastFmConfigured { credentials.isConfigured }

    @Provides
    fun provideObserveLastFmAccount(authenticator: LastFmAuthenticator): ObserveLastFmAccount = ObserveLastFmAccount { authenticator.state }

    @Provides
    fun provideStartLastFmSignIn(authenticator: LastFmAuthenticator): StartLastFmSignIn = StartLastFmSignIn(authenticator::startSignIn)

    @Provides
    fun provideFinishLastFmSignIn(authenticator: LastFmAuthenticator): FinishLastFmSignIn = FinishLastFmSignIn(authenticator::finishSignIn)

    @Provides
    fun provideSignOutOfLastFm(authenticator: LastFmAuthenticator): SignOutOfLastFm = SignOutOfLastFm(authenticator::signOut)
}
