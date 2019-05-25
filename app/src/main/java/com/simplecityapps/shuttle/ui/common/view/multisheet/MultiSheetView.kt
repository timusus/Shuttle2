package com.simplecityapps.shuttle.ui.common.view.multisheet

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.annotation.IdRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.simplecityapps.shuttle.R
import com.simplecityapps.shuttle.ui.common.view.setMargins
import kotlinx.android.synthetic.main.multi_sheet.view.*

class MultiSheetView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : CoordinatorLayout(context, attrs, defStyleAttr) {

    interface SheetStateChangeListener {
        fun onSheetStateChanged(@Sheet sheet: Int, @BottomSheetBehavior.State state: Int)

        fun onSlide(@Sheet sheet: Int, slideOffset: Float)
    }

    private val bottomSheetBehavior1: CustomBottomSheetBehavior<*>
    private val bottomSheetBehavior2: CustomBottomSheetBehavior<*>

    private lateinit var navHostFragment: View

    private var sheetStateChangeListeners = mutableSetOf<SheetStateChangeListener>()

    override fun onFinishInflate() {
        super.onFinishInflate()

        navHostFragment = findViewById<View>(R.id.navHostFragment)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        if (changed) {
            sheet1.translationY = -bottomNavigationView.height.toFloat() + bottomNavigationView.translationY
            sheet1Container.setMargins(bottomMargin = sheet2PeekView.height)
            navHostFragment.setMargins(bottomMargin = bottomNavigationView.height + bottomSheetBehavior1.peekHeight)
        }
    }

    val isHidden: Boolean
        get() {
            val peekHeight = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_1_height)
            return bottomSheetBehavior1.peekHeight < peekHeight
        }

    /**
     * @return the currently expanded Sheet
     */
    val currentSheet: Int
        @Sheet
        get() = if (bottomSheetBehavior2.state == BottomSheetBehavior.STATE_EXPANDED) {
            Sheet.SECOND
        } else if (bottomSheetBehavior1.state == BottomSheetBehavior.STATE_EXPANDED) {
            Sheet.FIRST
        } else {
            Sheet.NONE
        }

    val mainContainerResId: Int
        @IdRes
        get() = R.id.navHostFragment


    init {
        View.inflate(getContext(), R.layout.multi_sheet, this)

        val sheet1 = findViewById<View>(R.id.sheet1)
        bottomSheetBehavior1 = BottomSheetBehavior.from(sheet1) as CustomBottomSheetBehavior<*>
        bottomSheetBehavior1.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                fadeView(Sheet.FIRST, newState)

                sheetStateChangeListeners.forEach { listener -> listener.onSheetStateChanged(Sheet.FIRST, newState)}
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                fadeView(findViewById(getSheetPeekViewResId(Sheet.FIRST)), slideOffset)

                sheetStateChangeListeners.forEach { listener -> listener.onSlide(Sheet.FIRST, slideOffset)}

                bottomNavigationView.translationY = bottomNavigationView.height.toFloat() * slideOffset
                sheet1.translationY = -bottomNavigationView.height.toFloat() + bottomNavigationView.translationY
            }
        })

        val sheet2 = findViewById<View>(R.id.sheet2)
        bottomSheetBehavior2 = BottomSheetBehavior.from(sheet2) as CustomBottomSheetBehavior<*>
        bottomSheetBehavior2.setBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_DRAGGING) {
                    bottomSheetBehavior1.setAllowDragging(false)
                } else {
                    bottomSheetBehavior1.setAllowDragging(true)
                }

                fadeView(Sheet.SECOND, newState)

                sheetStateChangeListeners.forEach { listener -> listener.onSheetStateChanged(Sheet.SECOND, newState)}
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                bottomSheetBehavior1.setAllowDragging(false)
                fadeView(findViewById(getSheetPeekViewResId(Sheet.SECOND)), slideOffset)

                sheetStateChangeListeners.forEach { listener -> listener.onSlide(Sheet.SECOND, slideOffset)}
            }
        })

        //First sheet view click listener
        findViewById<View>(getSheetPeekViewResId(Sheet.FIRST)).setOnClickListener { expandSheet(Sheet.FIRST) }

        //Second sheet view click listener
        findViewById<View>(getSheetPeekViewResId(Sheet.SECOND)).setOnClickListener { expandSheet(Sheet.SECOND) }

        findViewById<View>(getSheetPeekViewResId(Sheet.SECOND)).setOnTouchListener { _, _ ->
            bottomSheetBehavior1.setAllowDragging(false)
            bottomSheetBehavior2.setAllowDragging(true)
            false
        }
    }

    fun addSheetStateChangeListener(sheetStateChangeListener: SheetStateChangeListener) {
        sheetStateChangeListeners.add(sheetStateChangeListener)
    }

    fun removeSheetStateChangeListener(sheetStateChangeListener: SheetStateChangeListener){
        sheetStateChangeListeners.remove(sheetStateChangeListener)
    }

    fun expandSheet(@Sheet sheet: Int) {
        when (sheet) {
            Sheet.FIRST -> bottomSheetBehavior1.setState(BottomSheetBehavior.STATE_EXPANDED)
            Sheet.SECOND -> bottomSheetBehavior2.setState(BottomSheetBehavior.STATE_EXPANDED)
        }
    }

    fun collapseSheet(@Sheet sheet: Int) {
        when (sheet) {
            Sheet.FIRST -> bottomSheetBehavior1.setState(BottomSheetBehavior.STATE_COLLAPSED)
            Sheet.SECOND -> bottomSheetBehavior2.setState(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }

    /**
     * Sets the peek height of sheet one to 0.
     *
     * @param collapse true if all expanded sheets should be collapsed.
     * @param animate  true if the change in peek height should be animated
     */
    fun hide(collapse: Boolean, animate: Boolean) {

        bottomSheetBehavior1.setAllowDragging(false)

        if (!isHidden) {
            val peekHeight = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_1_height)
            if (animate) {
                val valueAnimator = ValueAnimator.ofInt(peekHeight, 0)
                valueAnimator.duration = 200
                valueAnimator.addUpdateListener { valueAnimator1 ->
                    bottomSheetBehavior1.peekHeight = valueAnimator1.animatedValue as Int
                    navHostFragment.setMargins(bottomMargin = bottomNavigationView.height + bottomSheetBehavior1.peekHeight)
                }
                valueAnimator.start()
            } else {
                bottomSheetBehavior1.peekHeight = 0
                navHostFragment.setMargins(bottomMargin = bottomNavigationView.height + bottomSheetBehavior1.peekHeight)
            }
            (findViewById<View>(mainContainerResId).layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = 0
            if (collapse) {
                goToSheet(Sheet.NONE)
            }
        }
    }

    /**
     * Restores the peek height to its default value.
     *
     * @param animate true if the change in peek height should be animated
     */
    fun unhide(animate: Boolean) {

        bottomSheetBehavior1.setAllowDragging(true)

        if (isHidden) {
            val peekHeight = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_1_height)
            val currentHeight = bottomSheetBehavior1.peekHeight
            val ratio = (1 - currentHeight / peekHeight).toFloat()
            if (animate) {
                val valueAnimator = ValueAnimator.ofInt(bottomSheetBehavior1.peekHeight, peekHeight)
                valueAnimator.duration = (200 * ratio).toLong()
                valueAnimator.addUpdateListener { valueAnimator1 ->
                    bottomSheetBehavior1.peekHeight = valueAnimator1.animatedValue as Int
                    navHostFragment.setMargins(bottomMargin = bottomNavigationView.height + bottomSheetBehavior1.peekHeight)
                }
                valueAnimator.start()
            } else {
                bottomSheetBehavior1.peekHeight = peekHeight
                navHostFragment.setMargins(bottomMargin = bottomNavigationView.height + bottomSheetBehavior1.peekHeight)
            }
            (findViewById<View>(mainContainerResId).layoutParams as CoordinatorLayout.LayoutParams).bottomMargin = peekHeight
        }
    }

    /**
     * Expand the passed in sheet, collapsing/expanding the other sheet(s) as required.
     */
    fun goToSheet(@Sheet sheet: Int) {
        when (sheet) {
            Sheet.NONE -> {
                collapseSheet(Sheet.FIRST)
                collapseSheet(Sheet.SECOND)
            }
            Sheet.FIRST -> {
                collapseSheet(Sheet.SECOND)
                expandSheet(Sheet.FIRST)
            }
            Sheet.SECOND -> {
                expandSheet(Sheet.FIRST)
                expandSheet(Sheet.SECOND)
            }
        }
    }

    fun restoreSheet(@Sheet sheet: Int) {
        goToSheet(sheet)
        fadeView(Sheet.FIRST, bottomSheetBehavior1.state)
        fadeView(Sheet.SECOND, bottomSheetBehavior2.state)
    }

    fun consumeBackPress(): Boolean {
        when (currentSheet) {
            Sheet.SECOND -> {
                collapseSheet(Sheet.SECOND)
                return true
            }
            Sheet.FIRST -> {
                collapseSheet(Sheet.FIRST)
                return true
            }
        }
        return false
    }

    @SuppressLint("DefaultLocale")
    @IdRes
    fun getSheetContainerViewResId(@Sheet sheet: Int): Int {
        when (sheet) {
            Sheet.FIRST -> return R.id.sheet1Container
            Sheet.SECOND -> return R.id.sheet2Container
        }

        throw IllegalStateException(String.format("No container view resId found for sheet: %d", sheet))
    }

    @SuppressLint("DefaultLocale")
    @IdRes
    fun getSheetPeekViewResId(@Sheet sheet: Int): Int {
        when (sheet) {
            Sheet.FIRST -> return R.id.sheet1PeekView
            Sheet.SECOND -> return R.id.sheet2PeekView
        }

        throw IllegalStateException(String.format("No peek view resId found for sheet: %d", sheet))
    }

    private fun fadeView(@Sheet sheet: Int, @BottomSheetBehavior.State state: Int) {
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            fadeView(findViewById(getSheetPeekViewResId(sheet)), 1f)
        } else if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            fadeView(findViewById(getSheetPeekViewResId(sheet)), 0f)
        }
    }

    private fun fadeView(v: View, offset: Float) {
        val alpha = 1 - offset
        v.alpha = alpha
        v.visibility = if (alpha == 0f) View.GONE else View.VISIBLE
    }

    annotation class Sheet {
        companion object {
            const val NONE = 0
            const val FIRST = 1
            const val SECOND = 2
        }
    }
}


/**
 * A helper method to return the first MultiSheetView parent of the passed in View,
 * or null if none can be found.
 *
 * @param v the view whose hierarchy will be traversed.
 * @return the first MultiSheetView of the passed in view, or null if none can be found.
 */
fun View?.findParentMultiSheetView(): MultiSheetView? {
    return (this as? MultiSheetView) ?: (this?.parent as? View)?.findParentMultiSheetView()
}