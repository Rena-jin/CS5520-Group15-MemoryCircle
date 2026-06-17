package com.cs5520group15.memorycircle.common

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

/**
 * What: A central place to get Firebase service instances.
 * Why: So we never call FirebaseAuth.getInstance() or
 *      FirebaseFirestore.getInstance() scattered all over the project.
 *      Everyone imports from here instead.
 */
object FirebaseModule {

    // FirebaseAuth handles login, register, logout, current user
    val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance()

    // FirebaseFirestore is our cloud database
    val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    // FirebaseStorage holds uploaded files (e.g. scrapbook photos)
    val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()
}