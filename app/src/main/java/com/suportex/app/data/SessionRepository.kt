package com.suportex.app.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.SetOptions
import com.suportex.app.data.model.Session
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SessionRepository {
    private val db = FirebaseDataSource.db

    fun observeSession(sessionId: String) = callbackFlow<Session?> {
        val reg = db.collection("sessions").document(sessionId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(Session::class.java)?.copy(id = sessionId))
            }
        awaitClose { reg.remove() }
    }

    suspend fun upsertSession(session: Session) {
        db.collection("sessions").document(session.id)
            .set(session, SetOptions.merge()).await()
    }

    suspend fun updateSharing(sessionId: String, sharing: Boolean) {
        db.collection("sessions").document(sessionId)
            .update(mapOf("sharing" to sharing)).await()
    }

    suspend fun updateRemote(sessionId: String, enabled: Boolean) {
        db.collection("sessions").document(sessionId)
            .update(mapOf("remoteEnabled" to enabled)).await()
    }

    suspend fun startCall(sessionId: String) {
        db.collection("sessions").document(sessionId)
            .update(mapOf("callState" to "ringing")).await()
    }

    suspend fun endCall(sessionId: String) {
        db.collection("sessions").document(sessionId)
            .update(mapOf("callState" to "ended")).await()
    }

    suspend fun endSession(sessionId: String) {
        db.collection("sessions").document(sessionId)
            .update(mapOf("status" to "ended")).await()
    }
}

/* ---- Task<T>.await sem dependências extras ---- */
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