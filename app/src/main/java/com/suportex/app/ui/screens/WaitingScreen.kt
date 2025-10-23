package com.suportex.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suportex.app.ui.theme.TextMuted

@Suppress("unused")
@Composable
fun WaitingScreen(onCancel: () -> Unit, onAccepted: () -> Unit) {
    // DICA: aqui você pode escutar um evento do backend dizendo “técnico aceitou”
    // Por enquanto, botão de simulação:
    Surface {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Acionando técnico, aguarde…", fontSize = 18.sp)
            Text("Tempo médio: ~2–5 min", color = TextMuted)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("CANCELAR SOLICITAÇÃO", color = MaterialTheme.colorScheme.onSecondary)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAccepted, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("SIMULAR ACEITAÇÃO (dev)")
            }
        }
    }
}