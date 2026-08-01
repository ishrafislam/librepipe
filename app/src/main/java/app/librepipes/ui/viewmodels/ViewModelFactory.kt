package app.librepipes.ui.viewmodels

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.librepipes.LibrePipeApp
import app.librepipes.di.AppContainer

/**
 * Creates a ViewModel wired to the app's [AppContainer] with no DI framework.
 */
@Composable
inline fun <reified VM : ViewModel> appViewModel(
    noinline create: (AppContainer) -> VM,
): VM = viewModel(
    factory = viewModelFactory {
        initializer {
            val app = this[APPLICATION_KEY] as LibrePipeApp
            create(app.container)
        }
    }
)
