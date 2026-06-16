package com.cs5520group15.memorycircle.common

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

/**
 * What: Handles all authentication operations with Firebase.
 * Why: Keeps Firebase-specific code out of the ViewModel.
 *      ViewModel only calls functions here and gets back a Result.
 * Who uses this: AuthViewModel (login/register),
 *                and any screen that needs the current user's uid or name.
 */
object AuthRepository {

    private val auth = FirebaseModule.auth
    private val db   = FirebaseModule.db

    // ── Current user helpers ──────────────────────────────────────────────────

    /**
     * The uid of whoever is logged in right now.
     * Returns null if no one is logged in.
     * This is what B will call to get the current user's uid.
     */
    val currentUid: String?
        get() = auth.currentUser?.uid

    /**
     * The full FirebaseUser object (uid, email, displayName, etc.)
     * Returns null if no one is logged in.
     */
    val currentUser: FirebaseUser?
        get() = auth.currentUser

    // ── Register ─────────────────────────────────────────────────────────────

    /**
     * What: Creates a new account with email + password,
     *       then writes a user document to Firestore.
     * Returns: Result.Success(uid) or Result.Error(message)
     */
    suspend fun register(name: String, email: String, password: String): Result<String> {
        return try {
            // Step 1: create the Firebase Auth account
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user!!.uid

            // Step 2: write a user document to Firestore
            // This is the users/{uid} document B will use for profile + friends
            val userDoc = mapOf(
                "uid"       to uid,
                "name"      to name,
                "email"     to email,
                "bio"       to "",
                "avatarUrl" to "",
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("users").document(uid).set(userDoc).await()

            Result.Success(uid)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Registration failed")
        }
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * What: Signs in with email + password.
     * Returns: Result.Success(uid) or Result.Error(message)
     */
    suspend fun login(email: String, password: String): Result<String> {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user!!.uid
            Result.Success(uid)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Login failed")
        }
    }

    // ── Password reset ────────────────────────────────────────────────────────

    /**
     * What: Sends a password reset email.
     * Returns: Result.Success(Unit) or Result.Error(message)
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to send reset email")
        }
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    fun logout() {
        auth.signOut()
    }

    // ── Get current user's name from Firestore ────────────────────────────────

    /**
     * What: Reads the current user's name from Firestore.
     * Why: Firebase Auth doesn't store name by default,
     *      so we read it from the users collection.
     */
    suspend fun getCurrentUserName(): Result<String> {
        return try {
            val uid = currentUid ?: return Result.Error("Not logged in")
            val doc = db.collection("users").document(uid).get().await()
            val name = doc.getString("name") ?: "User"
            Result.Success(name)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to get user name")
        }
    }
}