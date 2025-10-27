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
            .orderBy("ts", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map { doc ->
                    val data = doc.data ?: emptyMap()
                    Message(
                        id = doc.id,
                        from = data["from"] as? String ?: "",
                        fromName = data["fromName"] as? String?,
                        text = data["text"] as? String?,
                        fileUrl = data["fileUrl"] as? String?,
                        audioUrl = data["audioUrl"] as? String?,
                        createdAt = when (val ts = data["ts"] ?: data["createdAt"]) {
                            is Number -> ts.toLong()
                            else -> System.currentTimeMillis()
                        }
                    )
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun sendText(sessionId: String, from: String, text: String) {
        val timestamp = System.currentTimeMillis()
        val payload = buildMessagePayload(
            from = from,
            fromName = null,
            text = text,
            fileUrl = null,
            audioUrl = null,
            timestamp = timestamp
        )
        db.collection("sessions").document(sessionId)
            .collection("messages").add(payload).await()
    }

    suspend fun upsertIncoming(sessionId: String, message: Message) {
        val collection = db.collection("sessions").document(sessionId)
            .collection("messages")
        val docId = message.id.takeIf { it.isNotBlank() } ?: collection.document().id
        val payload = buildMessagePayload(
            from = message.from,
            fromName = message.fromName,
            text = message.text,
            fileUrl = message.fileUrl,
            audioUrl = message.audioUrl,
            timestamp = message.createdAt
        )
        collection.document(docId).set(payload).await()
    }

    suspend fun sendFile(sessionId: String, from: String, localUri: Uri): String {
        val timestamp = System.currentTimeMillis()
        val ref = storage.reference
            .child("sessions/$sessionId/attachments/$timestamp")
        ref.putFile(localUri).await()
        val url = ref.downloadUrl.await().toString()
        val payload = buildMessagePayload(
            from = from,
            fromName = null,
            text = null,
            fileUrl = url,
            audioUrl = null,
            timestamp = timestamp
        )
        db.collection("sessions").document(sessionId)
            .collection("messages").add(payload).await()
        return url
    }

    private fun buildMessagePayload(
        from: String,
        fromName: String?,
        text: String?,
        fileUrl: String?,
        audioUrl: String?,
        timestamp: Long
    ): Map<String, Any?> {
        val payload = mutableMapOf<String, Any?>(
            "from" to from,
            "ts" to timestamp,
            "createdAt" to timestamp
        )
        fromName?.let { payload["fromName"] = it }
        text?.let { payload["text"] = it }
        fileUrl?.let { payload["fileUrl"] = it }
        audioUrl?.let { payload["audioUrl"] = it }
        return payload
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