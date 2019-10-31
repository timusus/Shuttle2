package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import com.simplecityapps.shuttle.R

class PlayPauseButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    enum class State {
        Playing, Paused
    }

    var state: State = State.Paused
        set(value) {
            field = value

            when (state) {
                State.Playing -> setImageResource(R.drawable.ic_pause_black_24dp)
                State.Paused -> setImageResource(R.drawable.ic_play_arrow_black_24dp)
            }
        }

    init {
        state = State.Paused
    }
}