package com.suportex.app.data

import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.Query
import com.suportex.app.data.model.Message
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ChatRepository {
    private val db = FirebaseDataSource.db
    private val storage = FirebaseDataSource.storage

    fun observeMessages(sessionId: String) = callbackFlow<List<Message>> {
        val reg = db.collection("sessions").document(sessionId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull {
                    it.toObject(Message::class.java)?.copy(id = it.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendText(sessionId: String, fromId: String, text: String) {
        val m = Message(fromId = fromId, text = text, createdAt = System.currentTimeMillis())
        db.collection("sessions").document(sessionId)
            .collection("messages").add(m).await()
    }

    suspend fun sendFile(sessionId: String, fromId: String, localUri: Uri): String {
        val ref = storage.reference
            .child("sessions/$sessionId/attachments/${System.currentTimeMillis()}")
        ref.putFile(localUri).await()
        val url = ref.downloadUrl.await().toString()
        val m = Message(fromId = fromId, fileUrl = url, createdAt = System.currentTimeMillis())
        db.collection("sessions").document(sessionId)
            .collection("messages").add(m).await()
        return url
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