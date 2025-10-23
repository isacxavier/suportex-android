package com.suportex.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.suportex.app.ui.screens.SessionScreen
import io.socket.client.IO
import io.socket.client.Socket
import okhttp3.*
import org.json.JSONObject
import java.util.UUID

enum class Screen { HOME, WAITING, SESSION }

class MainActivity : ComponentActivity() {

    private lateinit var mediaProjectionManager: MediaProjectionManager

    // (mantido só para cancelar request por HTTP, se desejar)
    private val http = OkHttpClient()

    // Bridges Activity -> Compose (já existiam)
    private var setIsSharingFromLauncher: ((Boolean) -> Unit)? = null
    private var setSystemMessageFromLauncher: ((String?) -> Unit)? = null

    // Bridges Socket -> Compose (novos)
    private var setRequestIdFromSocket: ((String?) -> Unit)? = null
    private var setSessionIdFromSocket: ((String?) -> Unit)? = null
    private var setScreenFromSocket: ((Screen) -> Unit)? = null

    private var currentSessionId: String? = null
    private lateinit var socket: Socket

    // -------- Helpers --------
    @Suppress("unused")
    private fun deviceId(): String {
        val p = getSharedPreferences("app", MODE_PRIVATE)
        val cur = p.getString("device_id", null)
        if (cur != null) return cur
        val gen = UUID.randomUUID().toString()
        p.edit { putString("device_id", gen) }
        return gen
    }
    @Suppress("unused")
    private fun copyToClipboard(label: String, text: String) {
        val cb = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, "Copiado", Toast.LENGTH_SHORT).show()
    }

    // -------- Socket.IO --------
    private fun connectSocket() {
        val opts = IO.Options().apply {
            forceNew = true
            reconnection = true
            // server.js já tem allowEIO3: true
        }
        socket = IO.socket(Conn.SERVER_BASE, opts)
        Conn.socket = socket

        socket.on(Socket.EVENT_CONNECT) {
            runOnUiThread {
                // opcional: Toast.makeText(this, "Conectado", Toast.LENGTH_SHORT).show()
            }
        }

        // cliente entrou na fila
        socket.on("support:enqueued") { args ->
            val any  = args.getOrNull(0) ?: return@on
            val data = (any as? JSONObject) ?: return@on
            // optString precisa de String default; usamos "" e depois tratamos vazio como null
            val reqId = data.optString("requestId", "").takeIf { it.isNotBlank() }
            setRequestIdFromSocket?.invoke(reqId)
        }

        // técnico aceitou → recebemos sessionId e (opcional) techName
        socket.on("support:accepted") { args ->
            val data = args.getOrNull(0) as? JSONObject ?: return@on
            val sid = data.optString("sessionId", "")
            val tname = data.optString("techName", "Técnico")
            if (sid.isNotBlank()) {
                Conn.sessionId = sid
                Conn.techName = tname
                currentSessionId = sid
                // Entrar na sala para sinalização (ANSWER/ICE virão depois, no Service)
                socket.emit("join", sid)
                // Ir para a tela de sessão
                setSessionIdFromSocket?.invoke(sid)
                setScreenFromSocket?.invoke(Screen.SESSION)
            }
        }

        socket.connect()
    }

    // Disparar pedido de suporte
    private fun requestSupport() {
        val payload = JSONObject(
            mapOf(
                "clientName" to "Android ${Build.MODEL ?: ""}".trim(),
                "brand" to (Build.BRAND ?: "Android"),
                "model" to (Build.MODEL ?: "")
            )
        )
        socket.emit("support:request", payload)
    }

    // Cancelar (opcional) – pelo endpoint HTTP do servidor
    private fun cancelRequest(requestId: String, onDone: () -> Unit = {}) {
        val req = Request.Builder()
            .url("${Conn.SERVER_BASE}/api/requests/$requestId")
            .delete()
            .build()
        http.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) = onDone()
            override fun onResponse(call: Call, response: Response) = onDone()
        })
    }

    // -------- Screen share launcher --------
    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val sid = currentSessionId
                if (sid.isNullOrBlank()) {
                    setSystemMessageFromLauncher?.invoke("Sessão ainda não aceita pelo técnico.")
                    return@registerForActivityResult
                }
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_START
                    putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                    putExtra(ScreenCaptureService.EXTRA_ROOM_CODE, sid)
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                setIsSharingFromLauncher?.invoke(true)
            } else {
                setSystemMessageFromLauncher?.invoke("Permissão de captura negada.")
            }
        }

    private fun stopScreenShare() {
        val stop = Intent(this, ScreenCaptureService::class.java).apply {
            action = ScreenCaptureService.ACTION_STOP
        }
        ContextCompat.startForegroundService(this, stop)
        setIsSharingFromLauncher?.invoke(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mediaProjectionManager = getSystemService(MediaProjectionManager::class.java)

        connectSocket() // 🔌 conecta o Socket.IO assim que abrir o app

        setContent {
            val brandPrimary = Color(0xFFFFCB19)
            val onPrimary = Color(0xFF111111)
            val secondary = Color(0xFF0A84FF)
            val error = Color(0xFFE63A3A)
            val background = Color(0xFFF4F6F8)
            val surfaceC = Color(0xFFFFFFFF)

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = brandPrimary,
                    onPrimary = onPrimary,
                    secondary = secondary,
                    onSecondary = Color.White,
                    error = error,
                    background = background,
                    surface = surfaceC
                ),
                typography = Typography()
            ) {
                var current by remember { mutableStateOf(Screen.HOME) }

                var requestId by remember { mutableStateOf<String?>(null) }
                var sessionId by remember { mutableStateOf<String?>(null) }
                var isSharing by remember { mutableStateOf(false) }
                var remoteEnabled by remember { mutableStateOf(false) }
                var calling by remember { mutableStateOf(false) }
                var callConnected by remember { mutableStateOf(false) }
                var systemMessage by remember { mutableStateOf<String?>(null) }

                // Bridges Activity -> Compose
                LaunchedEffect(Unit) {
                    setIsSharingFromLauncher = { isSharing = it }
                    setSystemMessageFromLauncher = { msg -> systemMessage = msg }

                    // Bridges Socket -> Compose
                    setRequestIdFromSocket = { req -> requestId = req }
                    setSessionIdFromSocket = { sid ->
                        sessionId = sid
                        currentSessionId = sid
                    }
                    setScreenFromSocket = { scr -> current = scr }
                }

                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (current) {
                        Screen.HOME -> HomeScreen(
                            onRequestSupport = {
                                // Vai para esperando e dispara o evento de suporte
                                current = Screen.WAITING
                                requestSupport()
                            },
                            textMuted = Color(0xFF8A8A8E)
                        )

                        Screen.WAITING -> WaitingScreen(
                            onCancel = {
                                requestId?.let { cancelRequest(it) }
                                requestId = null
                                sessionId = null
                                currentSessionId = null
                                current = Screen.HOME
                            },
                            onAccepted = { /* não usamos; aceitação vem do socket */ },
                            textMuted = Color(0xFF8A8A8E)
                        )

                        Screen.SESSION -> SessionScreen(
                            sessionId = sessionId,
                            isSharing = isSharing,
                            remoteEnabled = remoteEnabled,
                            calling = calling,
                            callConnected = callConnected,
                            systemMessage = systemMessage,
                            onSystemMessageConsumed = { systemMessage = null },
                            onStartShare = {
                                if (sessionId.isNullOrBlank()) {
                                    systemMessage = "Sessão não disponível ainda."
                                } else {
                                    val intent = mediaProjectionManager.createScreenCaptureIntent()
                                    screenCaptureLauncher.launch(intent)
                                }
                            },
                            onStopShare = { stopScreenShare() },
                            onToggleRemote = { enable -> remoteEnabled = enable },
                            onStartCall = {
                                calling = true
                                callConnected = false
                                // Se for implementar áudio/voz depois, coloque a lógica aqui
                            },
                            onEndCall = {
                                calling = false
                                callConnected = false
                            },
                            onEndSupport = {
                                if (isSharing) stopScreenShare()
                                current = Screen.HOME
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ===================== Composables locais (Home/Waiting) ===================== */

@Composable
private fun HomeScreen(
    onRequestSupport: () -> Unit,
    textMuted: Color
) {
    val ctx = LocalContext.current

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Image(painterResource(R.drawable.ic_suportex_logo), null, modifier = Modifier.size(180.dp))
        Spacer(Modifier.height(100.dp))
        Button(
            onClick = onRequestSupport,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(20.dp)
        ) { Text("SOLICITAR SUPORTE", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(16.dp))
        Text("Tempo médio de atendimento: 2–5 min", color = textMuted, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ajuda", color = textMuted,
                modifier = Modifier.clickable {
                    Toast.makeText(ctx, "Ajuda (em breve)", Toast.LENGTH_SHORT).show()
                })
            Text("  ·  ", color = textMuted)
            Text("Privacidade", color = textMuted,
                modifier = Modifier.clickable {
                    Toast.makeText(ctx, "Política de privacidade (em breve)", Toast.LENGTH_SHORT).show()
                })
            Text("  ·  ", color = textMuted)
            Text("Termos", color = textMuted,
                modifier = Modifier.clickable {
                    Toast.makeText(ctx, "Termos de uso (em breve)", Toast.LENGTH_SHORT).show()
                })
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Suppress("unused")
@Composable
private fun WaitingScreen(
    onCancel: () -> Unit,
    onAccepted: () -> Unit,
    textMuted: Color
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(80.dp))
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Acionando técnico, aguarde…", fontSize = 18.sp)
        Text("Tempo médio: ~2–5 min", color = textMuted)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) { Text("CANCELAR SOLICITAÇÃO", color = Color.White) }
    }
}