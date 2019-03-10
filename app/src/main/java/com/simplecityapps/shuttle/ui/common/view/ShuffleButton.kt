package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R

class ShuffleButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    var shuffleMode: QueueManager.ShuffleMode = QueueManager.ShuffleMode.Off
        set(value) {
            if (field != value) {
                field = value
                updateImage()
            }
        }

    init {
        updateImage()
    }

    private fun updateImage() {
        when (shuffleMode) {
            QueueManager.ShuffleMode.Off -> setImageResource(R.drawable.ic_shuffle_off_black_24dp)
            QueueManager.ShuffleMode.On -> setImageResource(R.drawable.ic_shuffle_black_24dp)
        }
    }
}