package io.sikorka.android.ui.wizard.slides.network_selection

import io.sikorka.android.mvp.BasePresenter
import io.sikorka.android.settings.AppPreferences
import javax.inject.Inject

class NetworkSelectionPresenterImpl
@Inject
constructor(private val appPreferences: AppPreferences) : NetworkSelectionPresenter,
    BasePresenter<NetworkSelectionView>() {
  override fun selectNetwork(network: Long) {
    appPreferences.selectNetwork(network)
    view?.updateNetworkSelection(network)
  }
}