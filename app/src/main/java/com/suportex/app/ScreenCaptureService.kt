package com.suportex.app

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

class ScreenCaptureService : Service() {

    companion object {
        const val ACTION_START = "com.suportex.app.action.START_CAPTURE"
        const val ACTION_STOP  = "com.suportex.app.action.STOP_CAPTURE"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_ROOM_CODE   = "extra_room_code"

        private const val NOTIF_ID = 1
        private const val NOTIF_CHANNEL_ID = "screen_capture"
        private const val TAG = "SXS/Service"
    }

    private var started = false

    // WebRTC / captura
    private var capturer: ScreenCapturerAndroid? = null
    private var eglBase: EglBase? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var factory: PeerConnectionFactory? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var peerConnection: PeerConnection? = null
    private var videoSender: RtpSender? = null
    private var dataChannel: DataChannel? = null
    private var certificates: RtcCertificatePem? = null

    // Sinalização
    private var socket: Socket? = null
    private var roomCode: String = ""

    // Keep-alive
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lastPongAt = AtomicLong(0L)
    private var pingRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (started) return START_NOT_STICKY

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                val room = intent.getStringExtra(EXTRA_ROOM_CODE) ?: ""

                if (resultCode != Activity.RESULT_OK || resultData == null || room.isBlank()) {
                    stopSelfSafe()
                    return START_NOT_STICKY
                }

                try {
                    roomCode = room

                    val projectionCallback = object : MediaProjection.Callback() {
                        override fun onStop() {
                            sendStatus("stopped", "mediaProjection.onStop")
                            stopSelfSafe()
                        }
                    }
                    capturer = ScreenCapturerAndroid(resultData, projectionCallback)

                    // === EGL antes da factory
                    eglBase = EglBase.create()

                    PeerConnectionFactory.initialize(
                        PeerConnectionFactory.InitializationOptions
                            .builder(applicationContext)
                            .createInitializationOptions()
                    )
                    factory = PeerConnectionFactory.builder()
                        .setVideoEncoderFactory(
                            DefaultVideoEncoderFactory(
                                eglBase!!.eglBaseContext,
                                /* enableIntelVp8Encoder */ true,
                                /* enableH264HighProfile */ true
                            )
                        )
                        .setVideoDecoderFactory(
                            DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
                        )
                        .createPeerConnectionFactory()

                    surfaceTextureHelper = SurfaceTextureHelper.create(
                        "CaptureThread",
                        eglBase!!.eglBaseContext
                    )

                    videoSource = factory!!.createVideoSource(true)
                    capturer!!.initialize(
                        surfaceTextureHelper,
                        applicationContext,
                        videoSource!!.capturerObserver
                    )
                    capturer!!.startCapture(720, 1280, 30)

                    val iceServers = listOf(
                        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
                        // Coloque seu TURN em produção
                    )

                    if (certificates == null) {
                        certificates = RtcCertificatePem.generateCertificate()
                    }

                    peerConnection = factory!!.createPeerConnection(
                        PeerConnection.RTCConfiguration(iceServers).apply {
                            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                        },
                        object : PeerConnection.Observer {
                            override fun onIceCandidate(c: IceCandidate) {
                                val cand = JSONObject().apply {
                                    putOpt("sdpMid", c.sdpMid)
                                    put("sdpMLineIndex", c.sdpMLineIndex)
                                    put("candidate", c.sdp ?: "")
                                }
                                val payload = JSONObject().apply {
                                    put("sessionId", roomCode)
                                    put("from", "client")
                                    put("role", "client")
                                    put("candidate", cand)
                                }
                                socket?.emit("signal:candidate", payload)
                            }
                            override fun onAddStream(p0: MediaStream?) {}
                            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                                when (newState) {
                                    PeerConnection.PeerConnectionState.CONNECTED -> sendStatus("connected")
                                    PeerConnection.PeerConnectionState.DISCONNECTED -> sendStatus("disconnected")
                                    PeerConnection.PeerConnectionState.FAILED -> {
                                        sendStatus("failed")
                                        renegotiate(iceRestart = true)
                                    }
                                    else -> {}
                                }
                            }
                            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
                            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
                            override fun onDataChannel(p0: DataChannel?) {}
                            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
                            override fun onRemoveStream(p0: MediaStream?) {}
                            override fun onRenegotiationNeeded() {}
                            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
                            override fun onTrack(p0: RtpTransceiver?) {}
                            override fun onIceConnectionReceivingChange(p0: Boolean) {}
                        }
                    )

                    videoTrack = factory!!.createVideoTrack("video", videoSource!!)
                    videoSender = peerConnection!!.addTrack(videoTrack!!, listOf("screen"))

                    applyEncoderQuality(scaleDownBy = 1.0, maxBitrateKbps = 2200, maxFps = 30)
                    applySenderParams(maxKbps = 2200, minKbps = 600, maxFps = 30, scaleDown = null)

                    dataChannel = peerConnection!!.createDataChannel("control", DataChannel.Init())
                    dataChannel?.registerObserver(object : DataChannel.Observer {
                        override fun onBufferedAmountChange(previousAmount: Long) {}
                        override fun onStateChange() {
                            if (dataChannel?.state() == DataChannel.State.OPEN) {
                                startPingLoop()
                                sendStatus("ready")
                            }
                        }
                        override fun onMessage(buffer: DataChannel.Buffer) {
                            handleDcMessage(buffer)
                        }
                    })

                    // 🔌 Socket.IO usando a mesma base do servidor
                    socket = IO.socket(Conn.SERVER_BASE)
                    socket?.on(Socket.EVENT_CONNECT) {
                        val joinPayload = JSONObject().apply {
                            put("sessionId", roomCode)
                            put("role", "client")
                        }
                        socket?.emit("join", joinPayload)
                    }
                    socket?.on("peer-joined") {
                        renegotiate(iceRestart = false)
                    }
                    socket?.on("signal:answer") { args ->
                        if (args.isEmpty()) return@on
                        val obj = args[0] as? JSONObject ?: return@on
                        handleSignalAnswer(obj)
                    }
                    socket?.on("signal:candidate") { args ->
                        if (args.isEmpty()) return@on
                        val obj = args[0] as? JSONObject ?: return@on
                        handleSignalCandidate(obj)
                    }
                    socket?.on("signal") { args ->
                        if (args.isEmpty()) return@on
                        val obj = args[0] as? JSONObject ?: return@on
                        when (obj.optString("type")) {
                            "answer" -> handleSignalAnswer(obj)
                            else -> handleSignalCandidate(obj)
                        }
                    }
                    socket?.connect()

                    started = true
                } catch (_: SecurityException) {
                    stopSelfSafe()
                    return START_NOT_STICKY
                } catch (t: Throwable) {
                    Log.e(TAG, "Start error", t)
                    stopSelfSafe()
                    return START_NOT_STICKY
                }
            }

            ACTION_STOP -> {
                sendStatus("stopped", "action_stop")
                stopSelfSafe()
            }
        }

        return START_NOT_STICKY
    }

    private fun applySenderParams(
        maxKbps: Int,
        minKbps: Int?,
        maxFps: Int?,
        scaleDown: Double?
    ) {
        val sender = videoSender ?: return
        try {
            val params = sender.parameters
            if (params.encodings.isEmpty()) {
                Log.w(TAG, "applySenderParams: encodings vazio; nada a aplicar")
                return
            }
            val e = params.encodings[0]
            e.maxBitrateBps = maxKbps * 1000
            if (minKbps != null) e.minBitrateBps = minKbps * 1000
            if (maxFps   != null) e.maxFramerate  = maxFps
            if (scaleDown != null) e.scaleResolutionDownBy = scaleDown

            val ok = sender.setParameters(params)
            if (!ok) Log.w(TAG, "applySenderParams: setParameters=false")
        } catch (t: Throwable) {
            Log.w(TAG, "applySenderParams error", t)
        }
    }

    // ---------- Sinalização / SDP / ICE ----------
    private var remoteDescriptionSet = false
    private val pendingRemoteIce = mutableListOf<IceCandidate>()

    private fun renegotiate(iceRestart: Boolean) {
        val pc = peerConnection ?: return
        remoteDescriptionSet = false
        pendingRemoteIce.clear()

        val mediaConstraints = MediaConstraints().apply {
            if (iceRestart) optional.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        pc.createOffer(object : SdpObserverAdapter() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserverAdapter() {
                    override fun onSetSuccess() {
                        val payload = JSONObject().apply {
                            put("sessionId", roomCode)
                            put("from", "client")
                            put("role", "client")
                            put("sdp", sdp.description)
                        }
                        socket?.emit("signal:offer", payload)
                        Log.d(TAG, "Offer enviada (${sdp.description.length} chars)")
                    }
                    override fun onSetFailure(error: String) {
                        Log.e(TAG, "setLocalDescription fail: $error")
                    }
                }, sdp)
            }
            override fun onCreateFailure(error: String) {
                Log.e(TAG, "createOffer fail: $error")
            }
        }, mediaConstraints)
    }

    private fun safeAddIce(cand: IceCandidate) {
        try { peerConnection?.addIceCandidate(cand) } catch (t: Throwable) {
            Log.e(TAG, "addIceCandidate error", t)
        }
    }

    private fun handleSignalAnswer(obj: JSONObject) {
        val sdp = obj.optString("sdp", "")
        if (sdp.isBlank()) return
        peerConnection?.setRemoteDescription(
            object : SdpObserverAdapter() {
                override fun onSetSuccess() {
                    Log.d(TAG, "ANSWER aplicada")
                    remoteDescriptionSet = true
                    pendingRemoteIce.forEach { safeAddIce(it) }
                    pendingRemoteIce.clear()
                }

                override fun onSetFailure(error: String) {
                    Log.e(TAG, "setRemoteDescription fail: $error")
                }
            },
            SessionDescription(SessionDescription.Type.ANSWER, sdp)
        )
    }

    private fun handleSignalCandidate(obj: JSONObject) {
        iceFrom(obj)?.let { cand ->
            if (remoteDescriptionSet) safeAddIce(cand) else pendingRemoteIce.add(cand)
        }
    }

    private fun applyEncoderQuality(scaleDownBy: Double?, maxBitrateKbps: Int?, maxFps: Int?) {
        val sender = videoSender ?: return
        val params = sender.parameters
        if (params.encodings.isEmpty()) return
        val enc = params.encodings[0]
        enc.scaleResolutionDownBy = scaleDownBy
        enc.maxBitrateBps = maxBitrateKbps?.let { it * 1000 }
        enc.maxFramerate = maxFps
        sender.parameters = params
    }

    private fun iceFrom(obj: JSONObject): IceCandidate? {
        return try {
            val c = if (obj.has("candidate") && obj.opt("candidate") is JSONObject) {
                obj.getJSONObject("candidate")
            } else obj
            if (!c.has("candidate") || !c.has("sdpMLineIndex")) return null
            val mid: String = c.optString("sdpMid", "")
            IceCandidate(mid, c.optInt("sdpMLineIndex", 0), c.optString("candidate", ""))
        } catch (_: Throwable) { null }
    }

    // ---------- DataChannel & util ----------
    private fun startPingLoop() {
        lastPongAt.set(System.currentTimeMillis())
        if (pingRunnable != null) return

        pingRunnable = object : Runnable {
            override fun run() {
                try { sendDc(JSONObject().put("t", "ping")) } catch (_: Throwable) {}
                val silentFor = System.currentTimeMillis() - lastPongAt.get()
                if (silentFor > 25_000) {
                    renegotiate(iceRestart = true)
                    lastPongAt.set(System.currentTimeMillis())
                }
                mainHandler.postDelayed(this, 10_000)
            }
        }
        mainHandler.postDelayed(pingRunnable!!, 1_000)
    }

    private fun handleDcMessage(buffer: DataChannel.Buffer) {
        val data = if (buffer.data.hasRemaining()) {
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            String(bytes, Charsets.UTF_8)
        } else ""
        val obj = runCatching { JSONObject(data) }.getOrNull() ?: return

        when (obj.optString("t")) {
            "pong" -> lastPongAt.set(System.currentTimeMillis())
            else -> when (obj.optString("cmd")) {
                "stop" -> { sendStatus("stopping", "remote_stop"); stopSelfSafe() }
                "quality" -> {
                    when (obj.optString("level")) {
                        "low" -> {
                            applyEncoderQuality(scaleDownBy = 2.0,  maxBitrateKbps = 700,  maxFps = 20)
                            applySenderParams(maxKbps = 700,  minKbps = 200, maxFps = 20, scaleDown = 2.0)
                        }
                        "mid" -> {
                            applyEncoderQuality(scaleDownBy = 1.33, maxBitrateKbps = 1300, maxFps = 24)
                            applySenderParams(maxKbps = 1300, minKbps = 400, maxFps = 24, scaleDown = 1.33)
                        }
                        "high" -> {
                            applyEncoderQuality(scaleDownBy = 1.0,  maxBitrateKbps = 2200, maxFps = 30)
                            applySenderParams(maxKbps = 2200, minKbps = 600, maxFps = 30, scaleDown = 1.0)
                        }
                    }
                }
                "request-stats" -> sendStatsOnce()
                "ice-restart"   -> renegotiate(iceRestart = true)
            }
        }
    }

    private fun sendStatsOnce() {
        val pc = peerConnection ?: return
        pc.getStats { report ->
            var bytesSent = 0L
            var framesEncoded = 0L
            var fps: Int? = null
            var rttMs: Double? = null
            var qlim: String? = null
            var encoderImpl: String? = null

            for (s in report.statsMap.values) {
                when (s.type) {
                    "outbound-rtp" -> {
                        (s.members["bytesSent"] as? Long)?.let { bytesSent = it }
                        (s.members["framesEncoded"] as? Long)?.let { framesEncoded = it }
                        (s.members["framesPerSecond"] as? Double)?.let { fps = it.toInt() }
                        (s.members["qualityLimitationReason"] as? String)?.let { qlim = it }
                        (s.members["encoderImplementation"] as? String)?.let { encoderImpl = it }
                    }
                    "candidate-pair" -> {
                        if (s.members["selected"] == true) {
                            (s.members["currentRoundTripTime"] as? Double)?.let { rttMs = it * 1000.0 }
                        }
                    }
                }
            }
            val js = JSONObject()
                .put("t", "stats")
                .put("bytesSent", bytesSent)
                .put("framesEncoded", framesEncoded)
                .put("fps", fps ?: JSONObject.NULL)
                .put("rttMs", rttMs ?: JSONObject.NULL)
                .put("qualityLimitation", qlim ?: JSONObject.NULL)
                .put("encoderImpl", encoderImpl ?: JSONObject.NULL)
            sendDc(js)
        }
    }

    private fun sendStatus(status: String, reason: String? = null) {
        val js = JSONObject().put("t", "status").put("status", status)
        if (!reason.isNullOrBlank()) js.put("reason", reason)
        sendDc(js)
    }
    private fun sendDc(obj: JSONObject) {
        val dc = dataChannel ?: return
        val bytes = obj.toString().toByteArray(Charsets.UTF_8)
        dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
    }

    // ---------- Foreground & cleanup ----------
    private fun startAsForeground() {
        val nm = getSystemService(NotificationManager::class.java)

        // Cria o canal **somente** no Android 8.0+ (API 26)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(NOTIF_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    NOTIF_CHANNEL_ID,
                    "Captura de Tela",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Compartilhamento de tela para suporte remoto"
                }
                nm.createNotificationChannel(ch)
            }
        }

        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stopPending = PendingIntent.getService(this, 0, stopIntent, stopFlags)

        // Pode passar o channelId mesmo em <26; o sistema apenas ignora
        val notif = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setContentTitle("Suporte X")
            .setContentText("Capturando a tela…")
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                NotificationCompat.Action(
                    android.R.drawable.ic_media_pause,
                    "Parar",
                    stopPending
                )
            )
            .build()

        startForeground(NOTIF_ID, notif)
    }

    private fun stopSelfSafe() {
        pingRunnable?.let { mainHandler.removeCallbacks(it) }
        pingRunnable = null
        try { capturer?.stopCapture() } catch (_: Exception) {}
        try { capturer?.dispose() } catch (_: Exception) {}
        capturer = null
        try { dataChannel?.close() } catch (_: Exception) {}
        dataChannel = null
        try { videoTrack?.dispose() } catch (_: Exception) {}
        videoTrack = null
        try { videoSource?.dispose() } catch (_: Exception) {}
        videoSource = null
        try { surfaceTextureHelper?.dispose() } catch (_: Exception) {}
        surfaceTextureHelper = null
        try { peerConnection?.close() } catch (_: Exception) {}
        peerConnection = null
        try {
            socket?.off(Socket.EVENT_CONNECT)
            socket?.off("peer-joined")
            socket?.off("signal:answer")
            socket?.off("signal:candidate")
            socket?.off("signal")
        } catch (_: Exception) {}
        try { socket?.disconnect() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        try { eglBase?.release() } catch (_: Exception) {}
        eglBase = null
        started = false
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        stopSelfSafe()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) {}
    override fun onSetFailure(error: String) {}
}