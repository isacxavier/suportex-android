package com.suportex.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.suportex.app.data.ChatRepository
import com.suportex.app.data.model.Message
import com.suportex.app.R
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SessionScreen(
    sessionId: String? = null,
    isSharing: Boolean,
    remoteEnabled: Boolean,
    calling: Boolean,
    callConnected: Boolean,
    // << novas props para centralizar mensagens vindas da Activity
    systemMessage: String? = null,
    onSystemMessageConsumed: () -> Unit = {},
    onStartShare: () -> Unit,
    onStopShare: () -> Unit,
    onToggleRemote: (Boolean) -> Unit,
    onStartCall: () -> Unit,
    onEndCall: () -> Unit,
    onEndSupport: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val chat = remember { ChatRepository() }
    val focus = LocalFocusManager.current
    val ctx = LocalContext.current

    // Toast "deduper"
    data class ToastGuard(var last: String? = null, var at: Long = 0L)
    val toastGuard = remember { ToastGuard() }
    fun toastOnce(msg: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (msg != toastGuard.last || (now - toastGuard.at) > 1500L) {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
            toastGuard.last = msg
            toastGuard.at = now
        }
    }

    // timer
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            seconds++
        }
    }
    val timer = String.format(
        Locale.getDefault(),
        "%02d:%02d:%02d",
        seconds / 3600, (seconds % 3600) / 60, seconds % 60
    )

    // === Timer da ligação (só conta quando callConnected = true) ===
    var callSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(callConnected) {
        if (callConnected) {
            callSeconds = 0
            while (true) {
                kotlinx.coroutines.delay(1_000)
                callSeconds++
            }
        } else {
            callSeconds = 0
        }
    }

    val callTimer = String.format(
        Locale.getDefault(),
        "%02d:%02d:%02d",
        callSeconds / 3600, (callSeconds % 3600) / 60, callSeconds % 60
    )

    // mensagens
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    LaunchedEffect(sessionId) {
        if (sessionId != null) {
            chat.observeMessages(sessionId).collect { messages = it }
        }
    }

    // paleta
    val brandYellow = Color(0xFFFFCB19)
    val brandYellowActive = Color(0xFFB98F0E)
    val infoBlue = Color(0xFF1E88E5)
    val successGreen = Color(0xFF2DD36F)
    val dangerRed = Color(0xFFE63A3A)
    val borderSoft = Color(0xFFE1E3E8)
    val cardBg = Color(0xFFFFFFFF)
    val callYellow = Color(0xFFFFE27A)
    val shareOrange = Color(0xFFFF9800)

    val remoteEffective = isSharing && remoteEnabled

    val statusColor = when {
        remoteEffective -> dangerRed
        isSharing       -> shareOrange
        callConnected              -> callYellow             // só muda quando ACEITA
        else            -> infoBlue
    }
    val statusText = when {
        remoteEffective -> "Compartilhamento + acesso remoto"
        isSharing       -> "Tela compartilhada"
        callConnected              -> "Em chamada"
        else            -> "Atendimento via chat"
    }

    // bolinha “Sessão Ativa” pulsando
    val pulse by rememberInfiniteTransition(label = "pulse")
        .animateFloat(
            initialValue = 0.9f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAnim"
        )

    // --- dimensões e cores do botão de chamada ---
    val callPillHeight = 44.dp
    val callPillRadius = 22.dp
    val endCallContainer = Color(0xFFFFE5E5) // vermelho bem claro (tonal)
    val endCallContent  = Color(0xFFD32F2F) // vermelho escuro para texto/ícone
    val timeChipBg      = Color(0xFFEDEFF2) // fundo do chip
    val timeChipText    = Color(0xFF4A4A4A) // texto do chip


    // ======= CENTRALIZAÇÃO DOS TOASTS =======
    var prevSharing by remember { mutableStateOf(isSharing) }
    var prevRemoteEffective by remember { mutableStateOf(remoteEffective) }

    // 1) Mensagens disparadas por mudanças de estado (share/remote)
    LaunchedEffect(isSharing, remoteEnabled) {
        val nowRemoteEffective = isSharing && remoteEnabled

        // começou a compartilhar
        if (!prevSharing && isSharing) {
            if (nowRemoteEffective) {
                toastOnce("Compartilhamento de tela e acesso remoto permitido")
            } else {
                toastOnce("Compartilhamento iniciado")
            }
        }

        // parou de compartilhar
        if (prevSharing && !isSharing) {
            toastOnce("Compartilhamento encerrado")
        }

        // alternou remoto durante compartilhamento
        if (isSharing && prevRemoteEffective != nowRemoteEffective) {
            if (nowRemoteEffective) toastOnce("Acesso remoto ativado")
            else toastOnce("Acesso remoto desativado")
        }

        prevSharing = isSharing
        prevRemoteEffective = nowRemoteEffective
    }

    // 2) Mensagens vindas da Activity (perm. negada, sessão indisponível, etc.)
    LaunchedEffect(systemMessage) {
        systemMessage?.let {
            toastOnce(it)
            onSystemMessageConsumed()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .pointerInput(Unit) { detectTapGestures { focus.clearFocus() } }
    ) {

        // === LOGO (TOPO) ===
        Image(
            painter = painterResource(id = R.drawable.ic_suportex_logo_horizontal),
            contentDescription = "Suporte X",
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(bottom = 12.dp)
                .align(Alignment.CenterHorizontally),
            contentScale = ContentScale.Fit
        )
        // === LOGO (TOPO) — FIM ===

        // CAIXA DE STATUS
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg,
            tonalElevation = 0.dp,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, borderSoft)
        ) {
            Column(Modifier.padding(16.dp)) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Sessão Ativa", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(14.dp)
                                .graphicsLayer(scaleX = pulse, scaleY = pulse)
                                .clip(CircleShape)
                                .background(successGreen)
                        )
                    }
                    Row {
                        Text("Tempo: ", fontWeight = FontWeight.SemiBold)
                        Text(timer)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row { Text("Técnico: ", fontWeight = FontWeight.SemiBold); Text("Isac Xavier") }

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Status: ", fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(statusText)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // INICIAR / PARAR COMPARTILHAMENTO — mais “magro”
        Button(
            onClick = { if (isSharing) onStopShare() else onStartShare() },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),                // 64 -> 54
            shape = RoundedCornerShape(27.dp), // 32 -> 27 (pílula mais elegante)
            colors =
                if (isSharing)
                    ButtonDefaults.buttonColors(
                        containerColor = brandYellowActive,
                        contentColor = Color.White
                    )
                else
                    ButtonDefaults.buttonColors(
                        containerColor = brandYellow,
                        contentColor = Color.Black
                    )
        ) {
            Text(if (isSharing) "PARAR COMPARTILHAMENTO" else "INICIAR COMPARTILHAMENTO", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }

        // praticamente colado no botão de compartilhar
        Spacer(Modifier.height(2.dp))

        // SWITCH DE ACESSO REMOTO — compacto e alinhado como extensão do botão
        @OptIn(ExperimentalMaterial3Api::class)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-2).dp)        //puxa 2dp pra cima
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Permitir Acesso Remoto",
                    fontSize = 14.sp,
                    color = Color(0xFF6B6B6B)
                )
                Spacer(Modifier.width(6.dp))
                Switch(
                    modifier = Modifier.scale(0.9f), // deixa o switch um pouquinho menor
                    checked = remoteEnabled,
                    onCheckedChange = { enable ->
                        onToggleRemote(enable)
                        if (enable && !isSharing) {
                            // dica só quando a pessoa liga o remoto antes do compartilhamento
                            toastOnce("Inicie o compartilhamento para começar")
                        }
                        // Quando já está compartilhando, os toasts de “ativado/desativado”
                        // continuam centralizados no LaunchedEffect lá em cima.
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = infoBlue,
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFCBCBCB),
                        uncheckedThumbColor = Color.White
                    )
                )
            }
        }

// pode manter o espaçamento seguinte como estava, ou reduzir um pouco se quiser
        Spacer(Modifier.height(10.dp))

        // BOTÃO DE CHAMADA — três estados: parado / chamando / conectado (com chip de tempo)
        when {
            // 1) NÃO EM CHAMADA
            !calling -> {
                OutlinedButton(
                    onClick = onStartCall,
                    enabled = sessionId != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(callPillHeight),
                    shape = RoundedCornerShape(callPillRadius),
                    border = BorderStroke(1.dp, borderSoft),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = infoBlue
                    )
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = infoBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("REALIZAR CHAMADA", fontWeight = FontWeight.SemiBold)
                }
            }

            // 2) EM CHAMADA, AINDA CONECTANDO (técnico não atendeu)
            calling && !callConnected -> {
                Button(
                    onClick = onEndCall, // permite cancelar enquanto chama
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(callPillHeight),
                    shape = RoundedCornerShape(callPillRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEFF3FF),
                        contentColor = infoBlue
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Icon(Icons.Default.Phone, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("CHAMANDO...", fontWeight = FontWeight.SemiBold)
                }
            }

            // 3) EM CHAMADA E CONECTADO — botão + chip de tempo
            else /* calling && callConnected */ -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botão "Finalizar"
                    Button(
                        onClick = onEndCall,
                        modifier = Modifier
                            .weight(1f)
                            .height(callPillHeight),
                        shape = RoundedCornerShape(callPillRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = endCallContainer,
                            contentColor = endCallContent
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Icon(Icons.Default.Phone, contentDescription = null, tint = endCallContent)
                        Spacer(Modifier.width(8.dp))
                        Text("FINALIZAR CHAMADA", fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(Modifier.width(6.dp))

                    // Chip do tempo
                    Surface(
                        modifier = Modifier.height(callPillHeight),
                        shape = RoundedCornerShape(callPillRadius),
                        color = timeChipBg,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp,
                        border = BorderStroke(1.dp, borderSoft)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(callTimer, color = timeChipText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // CHAT — agora branco e com o input embutido
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = cardBg, // <<< branco (mesmo do card de status)
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, borderSoft)
        ) {
            // estado do input fica aqui dentro do card
            var input by remember { mutableStateOf("") }

            Column(Modifier.fillMaxSize()) {

                // Lista de mensagens
                val listState = rememberLazyListState()
                val uiMessages = remember(messages) { messages.asReversed() } // novas embaixo

                LazyColumn(
                    state = listState,
                    reverseLayout = true, // âncora embaixo
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, alignment = Alignment.Bottom) // << fixa no fundo
                ) {
                    items(uiMessages, key = { it.id }) { m ->   // << usa uiMessages aqui
                        val isOutgoing = m.fromId == "client" // você -> amarelo
                        val bubbleColor =
                            if (isOutgoing) Color(0xFFFFF4C1) else Color(0xFFF1F3F6)
                        val align =
                            if (isOutgoing) Arrangement.End else Arrangement.Start

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = align
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = bubbleColor,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                    m.text?.let { Text(it) }
                                    m.fileUrl?.let { Text("📎 Anexo", color = infoBlue) }
                                }
                            }
                        }
                    }
                }

                LaunchedEffect(uiMessages.size) {
                    if (uiMessages.isNotEmpty()) listState.animateScrollToItem(0)
                }

                // --- Pílulas do input e do Enviar ---
                val inputPillHeight = 48.dp
                val inputPillRadius = 24.dp

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = inputPillHeight, max = 140.dp)   // cresce, mas com limite
                            .clip(RoundedCornerShape(inputPillRadius)),       // clip igual ao shape
                        placeholder = { Text("Mensagem…") },

                        // MULTILINHA
                        singleLine = false,
                        minLines = 1,
                        maxLines = 4,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default), // mostra Enter

                        // pílula perfeita
                        shape = RoundedCornerShape(inputPillRadius),

                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { /* áudio futuro */ }, enabled = sessionId != null) {
                                    Icon(Icons.Default.Mic, contentDescription = "Áudio")
                                }
                                IconButton(onClick = { /* anexo futuro */ }, enabled = sessionId != null) {
                                    Icon(Icons.Default.AttachFile, contentDescription = "Anexo")
                                }
                            }
                        },

                        colors = OutlinedTextFieldDefaults.colors(
                            // BORDA VISÍVEL: cinza quando sem foco, amarela quando focado
                            focusedBorderColor = brandYellow,
                            unfocusedBorderColor = borderSoft,
                            disabledBorderColor = borderSoft,
                            errorBorderColor = MaterialTheme.colorScheme.error,
                            // fundo clarinho para destacar do branco do card
                            focusedContainerColor = Color(0xFFF6F7FA),
                            unfocusedContainerColor = Color(0xFFF6F7FA),

                            cursorColor = Color.Black
                        )
                    )

                    Button(
                        onClick = {
                            if (sessionId != null && input.isNotBlank()) {
                                scope.launch {
                                    ChatRepository().sendText(sessionId, fromId = "client", text = input.trim())
                                    input = ""
                                }
                            }
                        },
                        enabled = sessionId != null && input.isNotBlank(),
                        modifier = Modifier.height(inputPillHeight),
                        shape = RoundedCornerShape(inputPillRadius),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandYellow,
                            contentColor = Color.Black
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                    ) {
                        Text("Enviar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ENCERRAR SUPORTE
        Button(
            onClick = onEndSupport,
            modifier = Modifier.fillMaxWidth().height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = dangerRed, contentColor = Color.White)
        ) { Text("ENCERRAR SUPORTE") }
    }
}