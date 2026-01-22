package com.suportex.app.data

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    suspend fun ensureAnonAuth(): String {
        val projectId = FirebaseApp.getInstance().options.projectId
        val currentUid = auth.currentUser?.uid
        Log.d(TAG, "Firebase projectId=$projectId")
        Log.d(TAG, "AUTH state: currentUser=$currentUid")
        if (currentUid != null) {
            return currentUid
        }
        val result = auth.signInAnonymously().await()
        val uid = result.user?.uid ?: auth.currentUser?.uid
        Log.d(TAG, "AUTH state: currentUser=$uid")
        return uid ?: ""
    }

    private suspend fun <T> Task<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    cont.resume(task.result)
                } else {
                    cont.resumeWithException(task.exception ?: RuntimeException("Task failed"))
                }
            }
        }

    private companion object {
        const val TAG = "SXS/Auth"
    }
}
