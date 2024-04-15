package com.example.mapmywifi

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

object AccessPointTypes {

    private const val TAG = "AccessPointTypes"

    suspend fun fetchAccessPointTypes(): List<AccessPointType> {
        val firestoreDb = FirebaseFirestore.getInstance()
        val firebaseStorage = FirebaseStorage.getInstance()

        return try {
            firestoreDb.collection("AccessPointTypes").get().await().documents.mapNotNull { document ->
                val name = document.getString("name")
                val range = document.getLong("range")?.toInt()
                val rentalCost = document.getLong("rentalCost")?.toInt()
                val purchaseCost = document.getLong("purchaseCost")?.toInt()
                val imageRes = document.getString("imageRes")

                if (name == null || range == null || rentalCost == null || purchaseCost == null || imageRes == null) {
                    Log.d(TAG, "Null fields skipping this thing (It is here just to prevent some stupid things): $document")
                    return@mapNotNull null
                }

                try {
                    val imageUrl = firebaseStorage.getReference(imageRes).downloadUrl.await().toString()
                    val accessPointType = AccessPointType(name, range, rentalCost, purchaseCost, imageUrl)
                    Log.d(TAG, "Successfully created: $accessPointType")
                    accessPointType
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fetch imageUrl for document: $document", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch access point types", e)
            emptyList()
        }
    }
}