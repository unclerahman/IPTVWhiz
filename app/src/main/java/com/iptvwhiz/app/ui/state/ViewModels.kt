package com.iptvwhiz.app.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.IptvWhizApp

/** `viewModel()` with access to the [AppContainer] and navigation arguments. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    crossinline create: (container: AppContainer, savedState: SavedStateHandle) -> VM,
): VM {
    val app = LocalContext.current.applicationContext as IptvWhizApp
    return viewModel(
        factory = viewModelFactory {
            initializer { create(app.container, createSavedStateHandle()) }
        },
    )
}
