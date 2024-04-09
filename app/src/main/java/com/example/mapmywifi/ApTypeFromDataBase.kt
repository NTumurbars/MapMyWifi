package com.example.mapmywifi

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object AccessPointTypes {

    suspend fun fetchAccessPointTypes(): List<AccessPointType>? {
        val db = FirebaseFirestore.getInstance()
        val storage = FirebaseStorage.getInstance()
        val types = mutableListOf<AccessPointType>()

        try {
            val result = db.collection("AccessPointTypes").get().await()
            for (document in result.documents) {
                val name = document.getString("name") ?: "Unnamed"
                val range = document.getLong("range")?.toInt() ?: 0
                val rentalCost = document.getLong("rentalCost")?.toInt() ?: 0
                val purchaseCost = document.getLong("purchaseCost")?.toInt() ?: 0
                val imageRes = document.getString("imageRes") ?: ""

                val imageUrl = storage.getReferenceFromUrl(imageRes).downloadUrl.await().toString()
                types.add(AccessPointType(name, range, rentalCost, purchaseCost, imageUrl))
            }
            return types
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}