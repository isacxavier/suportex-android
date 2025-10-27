package com.suportex.app.data

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SessionRepository {
    private val db = FirebaseDataSource.db

    suspend fun startSession(
        sessionId: String,
        client: SessionClientInfo,
        tech: SessionTechInfo?
    ) {
        val now = System.currentTimeMillis()
        val payload = buildMap<String, Any> {
            put("createdAt", now)
            put("status", "active")
            put("client", client.toMap())
            tech?.toMap()?.let { put("tech", it) }
        }
        db.collection("sessions").document(sessionId)
            .set(payload, SetOptions.merge()).await()
    }

    suspend fun markSessionClosed(sessionId: String) {
        val payload = mapOf(
            "status" to "closed",
            "closedAt" to System.currentTimeMillis()
        )
        db.collection("sessions").document(sessionId)
            .set(payload, SetOptions.merge()).await()
    }

    suspend fun updateRealtimeState(
        sessionId: String,
        state: SessionState,
        telemetry: SessionTelemetry? = null
    ) {
        val data = mutableMapOf<String, Any>("state" to state.toMap())
        telemetry?.toMap()?.let { data["telemetry"] = it }
        db.collection("sessions").document(sessionId)
            .set(data, SetOptions.merge()).await()
    }

    suspend fun addEvent(
        sessionId: String,
        type: String,
        timestamp: Long = System.currentTimeMillis(),
        payload: Map<String, Any?>? = null
    ) {
        val data = mutableMapOf<String, Any>(
            "ts" to timestamp,
            "type" to type
        )
        payload?.let { data["payload"] = cleanPayload(it) }
        db.collection("sessions").document(sessionId)
            .collection("events")
            .add(data).await()
    }

    private fun cleanPayload(payload: Map<String, Any?>): Map<String, Any?> {
        return payload.filterValues { it != null }
    }
}

data class SessionClientInfo(
    val deviceModel: String?,
    val androidVersion: String?
) {
    fun toMap(): Map<String, Any> = buildMap {
        deviceModel?.let { put("deviceModel", it) }
        androidVersion?.let { put("androidVersion", it) }
    }
}

data class SessionTechInfo(
    val uid: String? = null,
    val name: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "name" to name
    ).filterValues { it != null }
}

data class SessionState(
    val sharing: Boolean,
    val remoteEnabled: Boolean,
    val calling: Boolean,
    val callConnected: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "sharing" to sharing,
        "remoteEnabled" to remoteEnabled,
        "calling" to calling,
        "callConnected" to callConnected,
        "updatedAt" to updatedAt
    )
}

data class SessionTelemetry(
    val battery: Int?,
    val net: String?,
    val sharing: Boolean,
    val remoteEnabled: Boolean,
    val calling: Boolean,
    val callConnected: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = buildMap {
        battery?.let { put("battery", it) }
        net?.let { put("net", it) }
        put("sharing", sharing)
        put("remoteEnabled", remoteEnabled)
        put("calling", calling)
        put("callConnected", callConnected)
        put("updatedAt", updatedAt)
    }
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
