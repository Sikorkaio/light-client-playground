package io.sikorka.android.ui.contracts

import io.sikorka.android.mvp.BaseView
import io.sikorka.android.mvp.Presenter
import io.sikorka.android.node.contracts.data.ContractData
import io.sikorka.android.node.contracts.data.ContractGas
import toothpick.config.Module

interface DeployContractView : BaseView {
  fun setSuggestedGasPrice(gasPrice: Long)
  fun requestDeployAuthorization(gas: ContractGas)
  fun showError(message: String?)
  fun complete(hex: String?)
  fun showGasDialog(gas: ContractGas)

  fun showError(code: Int)
}

interface DeployContractPresenter : Presenter<DeployContractView> {
  fun load()
  fun deployContract(passphrase: String, contractInfo: ContractData)
  fun prepareGasSelection()
  fun prepareDeployWithDefaults()
}


class DeployContractModule : Module() {
  init {
    bind(DeployContractPresenter::class.java).to(DeployContractPresenterImpl::class.java).singletonInScope()
  }
}