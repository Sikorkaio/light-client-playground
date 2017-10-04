package io.sikorka.android.node.contracts

import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.schedulers.Schedulers
import io.sikorka.android.contract.SikorkaBasicInterface
import io.sikorka.android.contract.SikorkaRegistry
import io.sikorka.android.helpers.Lce
import io.sikorka.android.helpers.fail
import io.sikorka.android.helpers.hexStringToByteArray
import io.sikorka.android.helpers.sha3.kekkac256
import io.sikorka.android.node.ExceedsBlockGasLimit
import io.sikorka.android.node.GethNode
import io.sikorka.android.node.accounts.AccountRepository
import io.sikorka.android.node.accounts.InvalidPassphraseException
import org.ethereum.geth.EthereumClient
import org.ethereum.geth.Geth
import org.ethereum.geth.TransactOpts
import timber.log.Timber
import javax.inject.Inject


class ContractRepository
@Inject
constructor(
    private val gethNode: GethNode,
    private val accountRepository: AccountRepository,
    private val pendingContractDataSource: PendingContractDataSource
) {

  fun getDeployedContracts(): Single<Lce<DeployedContractModel>> = gethNode.ethereumClient()
      .flatMap { ethereumClient ->
        return@flatMap Single.fromCallable {
          val sikorkaRegistry = SikorkaRegistry.bind(ethereumClient)

          val contractCoordinates = sikorkaRegistry.getContractCoordinates()
          val contractAddresses = sikorkaRegistry.getContractAddresses()

          val contractList = ArrayList<DeployedContract>()

          for (i in 0 until contractCoordinates.size() step 2) {
            val address = contractAddresses[i / 2]
            val latitude = contractCoordinates[i].int64 / 10000.0
            val longitude = contractCoordinates[i + 1].int64 / 10000.0
            val deployedContract = DeployedContract(address.hex, latitude, longitude)
            contractList.add(deployedContract)
          }

          return@fromCallable Lce.success(DeployedContractModel(contractList))
        }
      }


  fun deployContract(passphrase: String, contractData: ContractData): Single<SikorkaBasicInterface> {
    val sign = accountRepository.selectedAccount().flatMap {
      val gas = contractData.gas
      return@flatMap gethNode.createTransactOpts(it, gas.price, gas.limit) { _, transaction, chainId ->
        val signedTransaction = accountRepository.sign(it.addressHex, passphrase, transaction, chainId) ?: fail("null transaction was returned")
        Timber.v("sign ${transaction.hash.hex} ${transaction.cost} ${transaction.nonce}")

        signedTransaction
      }
    }
    return Single.zip(sign, gethNode.ethereumClient(), BiFunction { transactOpts: TransactOpts, ec: EthereumClient ->
      Timber.v("getting ready to deploy")
      DeployData(transactOpts, ec)
    }).flatMap {
      Single.fromCallable {
        val lat = Geth.newBigInt((contractData.latitude * 10000).toLong())
        val long = Geth.newBigInt((contractData.longitude * 10000).toLong())
        val answerHash = contractData.answer.kekkac256()
        val contractName = "sikorka experiment"
        val basicInterface = SikorkaBasicInterface.deploy(it.transactOpts, it.ec, contractName, lat, long, contractData.question, answerHash.hexStringToByteArray())
        Timber.v("preparing to deploy contract: {$contractName} with lat: ${lat.int64}, long ${long.int64} question: ${contractData.question} => answer hash: $answerHash")
        val pendingContract = PendingContract(basicInterface.Address.hex, basicInterface.Deployer.hash.hex)
        Timber.v("pending contract: $pendingContract")
        pendingContractDataSource.insert(pendingContract)

        basicInterface
      }.onErrorResumeNext {
        val message = it.message ?: ""
        when {
          message.contains("could not decrypt key with given passphrase") -> Single.error<SikorkaBasicInterface>(InvalidPassphraseException(it))
          message.contains("exceeds block gas limit") -> Single.error<SikorkaBasicInterface>(ExceedsBlockGasLimit(it))
          else -> return@onErrorResumeNext Single.error<SikorkaBasicInterface>(it)
        }
      }
    }
  }

  fun bindSikorkaInterface(addressHex: String) {
    gethNode.ethereumClient().flatMap { ethereumClient ->
      Single.fromCallable {
        val address = Geth.newAddressFromHex(addressHex)
        val boundContract = SikorkaBasicInterface(address, ethereumClient)
        Timber.v("Question ${boundContract.question(null)} -> name ${boundContract.name(null)}")
        boundContract
      }
    }.subscribeOn(Schedulers.io())
        .subscribe({

        }) {
          Timber.v(it)
        }
  }

  private data class DeployData(val transactOpts: TransactOpts, val ec: EthereumClient)
}