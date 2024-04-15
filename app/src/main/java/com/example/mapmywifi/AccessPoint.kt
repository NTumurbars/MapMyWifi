package com.example.mapmywifi

import java.util.UUID

data class AccessPointType(
    val name: String,
    val range: Int,
    val rentalCost: Int,
    val purchaseCost: Int,
    val imageUrl: String
)

data class AccessPointInstance(
    val type: AccessPointType,
    val id: String = UUID.randomUUID().toString(),
    var x: Float = 0f,
    var y: Float = 0f
)

data class ProposalOption(
    val accessPointType: AccessPointType,
    val quantity: Int,
    val rentalTotalCost: Int,
    val purchaseTotalCost: Int,
    val visitationFee: Int = 15000
)

data class ProposalSummary(
    val proposalOptions: List<ProposalOption>,
    val visitationFee: Int
)