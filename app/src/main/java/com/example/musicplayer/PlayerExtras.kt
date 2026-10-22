package com.example.musicplayer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import androidx.compose.foundation.layout.padding

/** Подпись скорости: 1×, 1,25×, 0,5×. */
fun formatSpeed(v: Float): String {
    val text = String.format(Locale.getDefault(), "%.2f", v).trimEnd('0').trimEnd(',', '.')
    return "$text×"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Скорость воспроизведения") },
        text = {
            Column {
                Text(
                    formatSpeed(vm.speed),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = vm.speed,
                    onValueChange = { vm.changeSpeed(it) },
                    valueRange = 0.5f..2f,
                    steps = 29
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        FilterChip(
                            selected = abs(vm.speed - value) < 0.001f,
                            onClick = { vm.changeSpeed(value) },
                            label = { Text(formatSpeed(value)) }
                        )
                    }
                }
                Text(
                    "Высота звука при этом не меняется. Скорость запоминается.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = { TextButton(onClick = { vm.changeSpeed(1f) }) { Text("Сбросить (1×)") } }
    )
}
