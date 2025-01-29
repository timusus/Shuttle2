package com.simplecityapps.shuttle.ui.common

import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * @see AutoClearedValue
 */
class AutoClearedNullableValue<T : Any?>(val fragment: Fragment) : ReadWriteProperty<Fragment, T?> {
    private var value: T? = null

    init {
        fragment.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onCreate(owner: LifecycleOwner) {
                    fragment.viewLifecycleOwnerLiveData.observe(fragment) { viewLifecycleOwner ->
                        viewLifecycleOwner?.lifecycle?.addObserver(
                            object : DefaultLifecycleObserver {
                                override fun onDestroy(owner: LifecycleOwner) {
                                    value = null
                                }
                            }
                        )
                    }
                }
            }
        )
    }

    override fun getValue(
        thisRef: Fragment,
        property: KProperty<*>
    ): T? = value

    override fun setValue(
        thisRef: Fragment,
        property: KProperty<*>,
        value: T?
    ) {
        this.value = value
    }
}

/**
 * @see [AutoClearedNullableValue]
 */
fun <T : Any> Fragment.autoClearedNullable() = AutoClearedNullableValue<T?>(this)
