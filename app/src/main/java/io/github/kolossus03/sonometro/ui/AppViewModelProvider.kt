package io.github.kolossus03.sonometro.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.kolossus03.sonometro.SonometroApp
import io.github.kolossus03.sonometro.ui.history.HistoryViewModel
import io.github.kolossus03.sonometro.ui.meter.MeterViewModel
import io.github.kolossus03.sonometro.ui.settings.SettingsViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { MeterViewModel(app().container) }
        initializer { HistoryViewModel(app().container) }
        initializer { SettingsViewModel(app().container) }
    }
}

private fun CreationExtras.app(): SonometroApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SonometroApp
