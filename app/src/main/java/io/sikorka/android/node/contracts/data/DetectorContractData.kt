package io.sikorka.android.node.contracts.data

data class DetectorContractData(
    override val name: String,
    override val gas: ContractGas,
    val detectorAddress: String,
    val secondsAllowed: Int,
    override val latitude: Double,
    override val longitude: Double
) : IContractData