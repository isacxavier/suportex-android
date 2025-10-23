@file:Suppress("unused")

package com.suportex.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suportex.app.ui.theme.TextMuted
import com.suportex.app.ui.theme.SuporteXTheme
import com.suportex.app.R

@Composable
fun HomeScreen(onRequestSupport: () -> Unit) {
    SuporteXTheme {
        Surface {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(48.dp))
                Image(painterResource(R.drawable.ic_suportex_logo), contentDescription = null,
                    modifier = Modifier.size(180.dp))
                Spacer(Modifier.height(100.dp))
                Button(
                    onClick = onRequestSupport,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = MaterialTheme.shapes.large
                ) { Text("SOLICITAR SUPORTE", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(16.dp))
                Text("Tempo médio de atendimento: 2–5 min", color = TextMuted, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text("Ajuda  ·  Privacidade  ·  Termos", color = TextMuted, fontSize = 14.sp)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}