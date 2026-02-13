package com.suportex.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun rememberAudioPlaybackController(): AudioPlaybackController {
    val context = LocalContext.current
    val controller = remember(context) { AudioPlaybackController(context) }

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    LaunchedEffect(controller.activeAudioUrl, controller.isPlaying) {
        while (controller.activeAudioUrl != null && controller.isPlaying) {
            controller.syncProgress()
            delay(250L)
        }
    }

    return controller
}

@Composable
fun AudioMessagePlayer(
    audioUrl: String,
    controller: AudioPlaybackController,
    modifier: Modifier = Modifier
) {
    val isActive = controller.activeAudioUrl == audioUrl
    val isPlaying = isActive && controller.isPlaying
    val duration = if (isActive) controller.durationMs else 0L
    val position = if (isActive) controller.positionMs else 0L
    val progress = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = { controller.toggle(audioUrl) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar áudio" else "Tocar áudio"
                )
            }
            Text(text = "${formatMillis(position)} / ${formatMillis(duration)}")
        }

        Spacer(Modifier.height(4.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(4.dp)
        )
    }
}

@Composable
fun UploadingAudioPlaceholder(modifier: Modifier = Modifier) {
    Text(text = "🎤 Enviando áudio…", modifier = modifier)
}

private fun formatMillis(value: Long): String {
    val safe = value.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}
