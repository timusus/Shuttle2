package com.simplecityapps.shuttle.dagger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

import com.simplecityapps.shuttle.ui.screens.library.folders.FolderViewModel
import com.simplecityapps.shuttle.ui.screens.library.folders.ShuttleViewModelFactory

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

@Suppress("unused")
@Module
abstract class ViewModelModule {

    @Binds
    @IntoMap
    @ViewModelKey(FolderViewModel::class)
    abstract fun bindFolderViewModel(folderViewModel: FolderViewModel): ViewModel

    @Binds
    abstract fun bindViewModelFactory(factory: ShuttleViewModelFactory): ViewModelProvider.Factory
}