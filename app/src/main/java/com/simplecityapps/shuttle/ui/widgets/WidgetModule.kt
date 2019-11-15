package com.simplecityapps.shuttle.ui.widgets

import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class WidgetModule {

    @ContributesAndroidInjector
    abstract fun bindWidgetProvider(): ShuttleAppWidgetProvider

}