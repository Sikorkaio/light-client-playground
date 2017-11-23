package io.sikorka.android.core.contracts

import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.sikorka.android.contract.DiscountContract
import io.sikorka.android.contract.SikorkaRegistry
import io.sikorka.android.core.GethNode
import io.sikorka.android.core.NoContractCodeAtGivenAddressException
import io.sikorka.android.core.NoSuitablePeersAvailableException
import io.sikorka.android.core.accounts.AccountRepository
import io.sikorka.android.core.contracts.data.*
import io.sikorka.android.core.messageValue
import io.sikorka.android.data.PendingContract
import io.sikorka.android.data.PendingContractDao
import io.sikorka.android.helpers.Lce
import io.sikorka.android.helpers.fail
import io.sikorka.android.utils.schedulers.SchedulerProvider
import org.ethereum.geth.*
import org.threeten.bp.Instant
import timber.log.Timber
import java.math.BigDecimal
import java.util.concurrent.TimeUnit
import javax.inject.Inject


class ContractRepository
@Inject
constructor(
    private val gethNode: GethNode,
    private val accountRepository: AccountRepository,
    private val pendingContractDao: PendingContractDao,
    private val schedulerProvider: SchedulerProvider
) {

  fun getDeployedContracts(): Single<Lce<DeployedContractModel>> = gethNode.ethereumClient()
      .flatMap { ethereumClient ->
        return@flatMap Single.fromCallable {
          Timber.v("Binding registry contract")
          val sikorkaRegistry = SikorkaRegistry.bind(ethereumClient)

          val contractAddresses = sikorkaRegistry.getContractAddresses()
          val contractCoordinates = sikorkaRegistry.getContractCoordinates()

          val contractList = ArrayList<DeployedContract>()

          for (i in 0 until contractCoordinates.size() step 2) {
            val address = contractAddresses[i / 2]
            val modifier = BigDecimal(COORDINATES_MODIFIER)
            val latitude = BigDecimal(contractCoordinates[i].getString(10)).divide(modifier)
            val longitude = BigDecimal(contractCoordinates[i + 1].getString(10)).divide(modifier)
            val deployedContract = DeployedContract(address.hex, latitude.toDouble(), longitude.toDouble())
            contractList.add(deployedContract)
          }

          Timber.v("${contractList.size} deployed contracts found")
          return@fromCallable Lce.success(DeployedContractModel(contractList))
        }.timeout(1, TimeUnit.MINUTES).onErrorReturn {
          return@onErrorReturn when {
            it.messageValue.contains("no suitable peers available") -> Lce.failure<DeployedContractModel>(NoSuitablePeersAvailableException(it))
            it.messageValue.contains("no contract code at given address") -> Lce.failure(NoContractCodeAtGivenAddressException(it))
            else -> Lce.failure(it)
          }
        }
      }


  fun deployContract(passphrase: String, data: IContractData): Single<DiscountContract> {
    val signer = signer(passphrase, data.gas)
    return Single.zip(signer, gethNode.ethereumClient(), BiFunction { transactOpts: TransactOpts, ec: EthereumClient ->
      Timber.v("getting ready to deploy")
      DeployData(transactOpts, ec)
    }).flatMap { deploy(data, it) }
  }

  private fun deploy(data: IContractData, deployData: DeployData): Single<DiscountContract> = Single.fromCallable {
    val modifier = BigDecimal(COORDINATES_MODIFIER)
    val biLatitude = BigDecimal(data.latitude).multiply(modifier).toBigInteger()
    val biLogitude = BigDecimal(data.longitude).multiply(modifier).toBigInteger()

    val latitude = Geth.newBigInt(0).apply {
      setString(biLatitude.toString(10), 10)
    }

    val longitude = Geth.newBigInt(0).apply {
      setString(biLogitude.toString(10), 10)
    }

    val totalSupply = Geth.newBigInt(data.totalSupply)

    Timber.v("Settings lat: ${latitude.string()}, long: ${longitude.string()}")

    val secondsAllowed: BigInt?
    val detector: Address?
    if (data is DetectorContractData) {
      secondsAllowed = Geth.newBigInt(data.secondsAllowed.toLong())
      detector = Geth.newAddressFromHex(data.detectorAddress)
    } else {
      secondsAllowed = Geth.newBigInt(0)
      detector = Geth.newAddressFromHex("0000000000000000000000000000000000000000")
    }

    val registry = Geth.newAddressFromHex(SikorkaRegistry.REGISTRY_ADDRESS)
    val contract = DiscountContract.deploy(
        deployData.transactOpts,
        deployData.ec,
        data.name,
        detector,
        latitude,
        longitude,
        secondsAllowed,
        registry,
        totalSupply
    )

    val address = contract.address
    val deployer = contract.deployer
    val pendingContract = PendingContract(
        contractAddress = address.hex,
        transactionHash = deployer!!.hash.hex,
        dateCreated = Instant.now().epochSecond
    )
    Timber.v("pending contract: $pendingContract")
    pendingContractDao.insert(pendingContract)
    return@fromCallable contract
  }

  fun bindSikorkaInterface(addressHex: String): Single<DiscountContract> =
      gethNode.ethereumClient().flatMap { ethereumClient ->
        Single.fromCallable {
          val address = Geth.newAddressFromHex(addressHex)
          val boundContract = DiscountContract(address, ethereumClient)
          Timber.v("contract -> name ${boundContract.name()}")
          boundContract
        }
      }


  private fun signer(passphrase: String, gas: ContractGas): Single<TransactOpts> {
    return accountRepository.selectedAccount().flatMap {
      return@flatMap gethNode.createTransactOpts(it, gas) { _, transaction, chainId ->
        val signedTransaction = accountRepository.sign(it.addressHex, passphrase, transaction, chainId) ?: fail("null transaction was returned")
        Timber.v("signing ${transaction.hash.hex} ${transaction.cost} ${transaction.nonce}")
        signedTransaction
      }
    }
  }

  private fun bindRegistry() = gethNode.ethereumClient().map { SikorkaRegistry.bind(it) }

  private data class DeployData(val transactOpts: TransactOpts, val ec: EthereumClient)

  companion object {
    private const val COORDINATES_MODIFIER = 10_000_000_000_000_000
  }

  fun transact(function: (opts: TransactOpts) -> Transaction, passphrase: String, gas: ContractGas): Single<Transaction> {
    val signer = signer(passphrase, gas)
    return Single.zip(signer, gethNode.ethereumClient(), BiFunction { transactOpts: TransactOpts, ec: EthereumClient ->
      Timber.v("getting ready to deploy")
      val transaction = function(transactOpts)
      transaction
    })
  }
}