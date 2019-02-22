package com.simplecityapps.shuttle.ui.screens.library.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.simplecityapps.shuttle.dagger.AppScope
import javax.inject.Inject
import javax.inject.Provider

@AppScope
class ShuttleViewModelFactory @Inject constructor(
    private val creators: Map<Class<out ViewModel>, @JvmSuppressWildcards Provider<ViewModel>>
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val creator = creators[modelClass] ?: creators.entries.firstOrNull {
            modelClass.isAssignableFrom(it.key)
        }?.value ?: throw IllegalArgumentException("Unknown model class $modelClass")

        try {
            @Suppress("UNCHECKED_CAST")
            return creator.get() as T
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }
}