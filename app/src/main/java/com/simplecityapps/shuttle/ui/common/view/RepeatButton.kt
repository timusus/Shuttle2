package com.simplecityapps.shuttle.ui.common.view

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageButton
import com.simplecityapps.playback.queue.QueueManager
import com.simplecityapps.shuttle.R

class RepeatButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatImageButton(context, attrs, defStyleAttr) {

    var repeatMode: QueueManager.RepeatMode = QueueManager.RepeatMode.Off
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
        when (repeatMode) {
            QueueManager.RepeatMode.Off -> setImageResource(R.drawable.ic_repeat_off_black_24dp)
            QueueManager.RepeatMode.All -> setImageResource(R.drawable.ic_repeat_black_24dp)
            QueueManager.RepeatMode.One -> setImageResource(R.drawable.ic_repeat_one_black_24dp)
        }
    }
}