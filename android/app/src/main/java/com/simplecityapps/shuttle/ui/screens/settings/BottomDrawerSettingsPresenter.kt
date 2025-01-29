package com.simplecityapps.shuttle.ui.screens.settings

import android.annotation.SuppressLint
import androidx.annotation.NavigationRes
import com.simplecityapps.mediaprovider.repository.songs.SongRepository
import com.simplecityapps.playback.PlaybackManager
import com.simplecityapps.shuttle.di.AppCoroutineScope
import com.simplecityapps.shuttle.query.SongQuery
import com.simplecityapps.shuttle.ui.common.error.UserFriendlyError
import com.simplecityapps.shuttle.ui.common.mvp.BaseContract
import com.simplecityapps.shuttle.ui.common.mvp.BasePresenter
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

interface BottomDrawerSettingsContract {
    interface View {
        fun setData(
            settingsItems: List<SettingsMenuItem>,
            currentDestination: Int?
        )

        fun showLoadError(error: Error)
    }

    interface Presenter : BaseContract.Presenter<View> {
        fun loadData()

        fun shuffleAll()
    }
}

class BottomDrawerSettingsPresenter
@Inject
constructor(
    private val songRepository: SongRepository,
    private val playbackManager: PlaybackManager,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : BasePresenter<BottomDrawerSettingsContract.View>(),
    BottomDrawerSettingsContract.Presenter {
    @NavigationRes
    var currentDestinationIdRes: Int? = null
        set(value) {
            field = value
            view?.setData(
                SettingsMenuItem.values().toList(),
                value
            )
        }

    override fun loadData() {
        view?.setData(
            SettingsMenuItem.values().toList(),
            currentDestinationIdRes
        )
    }

    @SuppressLint("CheckResult")
    override fun shuffleAll() {
        appCoroutineScope.launch {
            val songs =
                songRepository
                    .getSongs(SongQuery.All())
                    .firstOrNull()
                    .orEmpty()
            if (songs.isEmpty()) {
                view?.showLoadError(UserFriendlyError("Your library is empty"))
                return@launch
            }

            playbackManager.shuffle(songs) { result ->
                result.onSuccess { playbackManager.play() }
                result.onFailure { error -> view?.showLoadError(Error(error)) }
            }
        }
    }
}
