package com.simplecityapps.shuttle.ui.common.view.multisheet

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.simplecityapps.shuttle.R
import java.lang.ref.WeakReference

class CustomBottomSheetBehavior<V : View>(context: Context, attrs: AttributeSet?) : BottomSheetBehavior<V>(context, attrs) {

    var bottomSheet2Ref: WeakReference<BottomSheetBehavior<V>>? = null
    var peek2Ref: WeakReference<View>? = null

    override fun onAttachedToLayoutParams(layoutParams: CoordinatorLayout.LayoutParams) {
        super.onAttachedToLayoutParams(layoutParams)

        bottomSheet2Ref = null
        peek2Ref = null
    }

    override fun onDetachedFromLayoutParams() {
        super.onDetachedFromLayoutParams()

        bottomSheet2Ref = null
        peek2Ref = null
    }

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        if (bottomSheet2Ref == null) {
            bottomSheet2Ref = WeakReference(BottomSheetBehavior.from(parent.findViewById(R.id.sheet2)) as BottomSheetBehavior<V>)
        }
        if (peek2Ref == null) {
            peek2Ref = WeakReference(parent.findViewById(R.id.sheet2PeekView))
        }

        return super.onLayoutChild(parent, child, layoutDirection)
    }

    override fun onInterceptTouchEvent(parent: CoordinatorLayout, child: V, event: MotionEvent): Boolean {
        if (child.id == R.id.sheet1) {
            if (state == BottomSheetBehavior.STATE_EXPANDED) {
                // If the first sheet is expanded, then we ignore touch events if either the second sheet is also expanded, being dragged, or the touch event is on the peek view
                val bottomSheet2State = bottomSheet2Ref?.get()?.state
                val offsetViewBounds = Rect()
                peek2Ref?.get()?.let { peekView2 ->
                    peekView2.getDrawingRect(offsetViewBounds)
                    parent.offsetDescendantRectToMyCoords(peekView2, offsetViewBounds)
                    if (offsetViewBounds.contains(event.x.toInt(), event.y.toInt())
                        || bottomSheet2State == BottomSheetBehavior.STATE_DRAGGING
                        || bottomSheet2State == BottomSheetBehavior.STATE_EXPANDED
                    ) {
                        return false
                    }
                }
            }
            return super.onInterceptTouchEvent(parent, child, event)
        } else {
            return super.onInterceptTouchEvent(parent, child, event)
        }
    }
}