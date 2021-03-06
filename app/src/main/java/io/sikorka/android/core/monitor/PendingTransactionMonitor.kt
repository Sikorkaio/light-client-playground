package io.sikorka.android.core.monitor

import androidx.lifecycle.Observer
import io.reactivex.disposables.Disposable
import io.sikorka.android.core.ethereumclient.LightClientProvider
import io.sikorka.android.data.syncstatus.SyncStatusProvider
import io.sikorka.android.data.transactions.PendingTransactionDao
import io.sikorka.android.utils.isDisposed
import io.sikorka.android.utils.schedulers.AppSchedulers
import timber.log.Timber

class PendingTransactionMonitor(
  private val syncStatusProvider: SyncStatusProvider,
  private val pendingTransactionDao: PendingTransactionDao,
  private val appSchedulers: AppSchedulers,
  private val lightClientProvider: LightClientProvider
) : LifecycleMonitor() {

  private var disposable: Disposable? = null

  override fun start() {
    super.start()
    syncStatusProvider.observe(this, Observer { _ ->

      if (!lightClientProvider.initialized) {
        Timber.v("No light client available yet")
        return@Observer
      }

      val lightClient = lightClientProvider.get()

      if (!disposable.isDisposed()) {
        return@Observer
      }

      disposable = pendingTransactionDao.pendingTransaction()
          .subscribeOn(appSchedulers.io)
          .observeOn(appSchedulers.io)
          .flatMapIterable { it }
          .toObservable()
          .flatMapSingle { lightClient.getTransactionReceipt(it.txHash) }
          .subscribe({
          }) {
            Timber.e(it, "Failure while processing pending transactions")
          }
    })
  }
}