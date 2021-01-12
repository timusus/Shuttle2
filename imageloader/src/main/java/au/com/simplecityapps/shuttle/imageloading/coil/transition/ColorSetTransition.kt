package au.com.simplecityapps.shuttle.imageloading.coil.transition

import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import au.com.simplecityapps.shuttle.imageloading.palette.ColorSet
import coil.request.ImageResult
import coil.request.SuccessResult
import coil.transition.Transition
import coil.transition.TransitionTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ColorSetTransition(
    private val context: Context,
    private val delegate: Transition? = null,
    private val onGenerated: (ColorSet) -> Unit
) : Transition {

    override suspend fun transition(target: TransitionTarget, result: ImageResult) {
        // Execute the delegate transition.
        val delegateJob = coroutineScope {
            launch(Dispatchers.Main.immediate) {
                (delegate ?: Transition.NONE).transition(target, result)
            }
        }

        // Compute the palette on a background thread.
        if (result is SuccessResult) {
            val bitmap = result.drawable.toBitmap()
            val colorSet = withContext(Dispatchers.IO) {
                ColorSet.fromBitmap(context, bitmap)
            }
            onGenerated(colorSet)
        }

        delegateJob.join()
    }
}