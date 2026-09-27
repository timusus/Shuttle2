package com.simplecityapps.shuttle.di

import android.content.Context

/**
 * Implemented by the Application, which creates and holds the Metro app graph (`AppGraph` in :android:app).
 *
 * Android instantiates activities, services and receivers itself, so they can't be constructor-injected (an
 * AppComponentFactory would need API 28). Each declares an `@ContributesTo(AppScope::class)` interface with an
 * `inject` function or accessors, which Metro merges into the graph, and reaches it through [appGraph].
 */
interface AppGraphOwner {
    val appGraph: Any
}

/** The app graph, as the contributed interface [T] it implements. */
inline fun <reified T : Any> Context.appGraph(): T = (applicationContext as AppGraphOwner).appGraph as T
