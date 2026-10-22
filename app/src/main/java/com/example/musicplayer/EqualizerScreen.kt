package com.example.musicplayer

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Короткая подпись частоты, как на кривой: 60, 230, 910, 3.6k, 14k. */
private fun shortFreq(hz: Int): String =
    if (hz >= 1000) {
        val k = hz / 1000f
        if (k == k.toInt().toFloat()) "${k.toInt()}k" else String.format(Locale.US, "%.1fk", k)
    } else {
        "$hz"
    }

private fun fullFreq(hz: Int): String =
    if (hz >= 1000) {
        if (hz % 1000 == 0) "${hz / 1000} кГц" else "%.1f кГц".format(hz / 1000f)
    } else {
        "$hz Гц"
    }

private fun levelForY(y: Float, heightPx: Float, padPx: Float, minLevel: Int, maxLevel: Int): Int {
    val frac = ((y - padPx) / (heightPx - 2 * padPx)).coerceIn(0f, 1f)
    return (maxLevel - frac * (maxLevel - minLevel)).roundToInt()
}

private fun bandForX(x: Float, widthPx: Float, bands: Int): Int =
    ((x / widthPx) * bands).toInt().coerceIn(0, bands - 1)

@Composable
fun EqualizerScreen() {
    val eq = EqualizerManager

    if (!eq.available) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Эквалайзер недоступен на этом устройстве или ещё запускается. " +
                            "Запустите песню и вернитесь на эту вкладку.",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Статус: ${eq.status}",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        EqualizerContent()
    }
}

@Composable
private fun EqualizerContent() {
    val eq = EqualizerManager
    val context = LocalContext.current
    var active by remember { mutableIntStateOf(-1) }
    var showPresets by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    val bands = eq.levels.size
    val headerName = eq.presetNames.getOrNull(eq.currentPreset) ?: eq.currentCustomName ?: "Свой"
    val panelContent = MaterialTheme.colorScheme.onPrimaryContainer

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Заголовок: название пресета (нажать — выбрать другой), карандаш — сохранить как свой, переключатель
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showPresets = true }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    headerName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Выбрать пресет")
            }
            IconButton(onClick = { showSave = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Сохранить как свой пресет")
            }
            Switch(checked = eq.enabled, onCheckedChange = { eq.turn(it) })
        }

        Text(
            if (active in 0 until bands) {
                val hz = eq.freqsHz.getOrElse(active) { 0 }
                "${fullFreq(hz)}: ${"%+.1f дБ".format(eq.levels[active] / 100f)}"
            } else {
                "Тяните точки вверх и вниз"
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // Панель с кривой
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                eq.freqsHz.forEach { hz ->
                    Text(
                        shortFreq(hz),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = panelContent
                    )
                }
            }

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .alpha(if (eq.enabled) 1f else 0.55f)
                    .pointerInput(bands, eq.minLevel, eq.maxLevel) {
                        val pad = 22.dp.toPx()
                        detectTapGestures { pos ->
                            if (bands > 0) {
                                if (!eq.enabled) eq.turn(true)
                                eq.changeBand(
                                    bandForX(pos.x, size.width.toFloat(), bands),
                                    levelForY(pos.y, size.height.toFloat(), pad, eq.minLevel, eq.maxLevel)
                                )
                            }
                        }
                    }
                    .pointerInput(bands, eq.minLevel, eq.maxLevel) {
                        val pad = 22.dp.toPx()
                        detectDragGestures(
                            onDragStart = { pos ->
                                if (bands > 0) {
                                    if (!eq.enabled) eq.turn(true)
                                    active = bandForX(pos.x, size.width.toFloat(), bands)
                                    eq.changeBand(
                                        active,
                                        levelForY(pos.y, size.height.toFloat(), pad, eq.minLevel, eq.maxLevel)
                                    )
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (active in 0 until bands) {
                                    eq.changeBand(
                                        active,
                                        levelForY(
                                            change.position.y,
                                            size.height.toFloat(),
                                            pad,
                                            eq.minLevel,
                                            eq.maxLevel
                                        )
                                    )
                                }
                            },
                            onDragEnd = { active = -1 },
                            onDragCancel = { active = -1 }
                        )
                    }
            ) {
                val n = eq.levels.size
                if (n > 0) {
                    val w = size.width
                    val h = size.height
                    val pad = 22.dp.toPx()
                    val cell = w / n
                    val range = (eq.maxLevel - eq.minLevel).toFloat().coerceAtLeast(1f)
                    fun px(i: Int) = cell * (i + 0.5f)
                    fun py(level: Int) = pad + (1f - (level - eq.minLevel) / range) * (h - 2 * pad)

                    val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 12f))

                    // вертикальные линии сетки
                    for (i in 0 until n) {
                        drawLine(
                            color = panelContent.copy(alpha = 0.3f),
                            start = Offset(px(i), 0f),
                            end = Offset(px(i), h),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dash
                        )
                    }
                    // линия «0 дБ»
                    val zeroY = py(0)
                    drawLine(
                        color = panelContent.copy(alpha = 0.5f),
                        start = Offset(0f, zeroY),
                        end = Offset(w, zeroY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dash
                    )

                    val points = List(n) { Offset(px(it), py(eq.levels.getOrElse(it) { 0 })) }

                    // плавная кривая через все точки
                    if (n >= 2) {
                        val path = Path()
                        path.moveTo(points[0].x, points[0].y)
                        for (i in 0 until n - 1) {
                            val p0 = points[max(i - 1, 0)]
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val p3 = points[min(i + 2, n - 1)]
                            path.cubicTo(
                                p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
                                p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
                                p2.x, p2.y
                            )
                        }
                        drawPath(
                            path = path,
                            color = panelContent,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // точки
                    for (i in 0 until n) {
                        drawCircle(
                            color = panelContent,
                            radius = if (i == active) 15.dp.toPx() else 10.dp.toPx(),
                            center = points[i]
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf("НИЗК.", "СРЕДН.", "ВЫСОК.").forEachIndexed { i, label ->
                    Text(
                        label,
                        modifier = Modifier.weight(1f),
                        textAlign = when (i) {
                            0 -> TextAlign.Start
                            1 -> TextAlign.Center
                            else -> TextAlign.End
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = panelContent.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { eq.resetFlat() }, modifier = Modifier.weight(1f)) {
                Text("Сбросить")
            }
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    val name = eq.currentCustomName
                    if (name != null) {
                        eq.saveCustom(name)
                        Toast.makeText(context, "Пресет «$name» сохранён", Toast.LENGTH_SHORT).show()
                    } else {
                        showSave = true
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Сохранить") }
        }

        Spacer(Modifier.height(20.dp))
        EffectCard(
            title = "Усиление басов",
            hint = "Добавляет глубину низким частотам",
            supported = eq.bassSupported,
            enabled = eq.bassEnabled,
            strength = eq.bassStrength,
            onToggle = { eq.turnBass(it) },
            onStrength = { eq.changeBass(it) }
        )
        Spacer(Modifier.height(12.dp))
        EffectCard(
            title = "Объёмный звук",
            hint = "Расширяет звуковую сцену, лучше слышно в наушниках",
            supported = eq.virtSupported,
            enabled = eq.virtEnabled,
            strength = eq.virtStrength,
            onToggle = { eq.turnVirtualizer(it) },
            onStrength = { eq.changeVirtualizer(it) }
        )
    }

    if (showPresets) {
        AlertDialog(
            onDismissRequest = { showPresets = false },
            title = { Text("Пресеты") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (eq.customPresets.isNotEmpty()) {
                        item {
                            Text("Мои", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(eq.customPresets, key = { it.name }) { preset ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    eq.applyCustom(preset)
                                    if (!eq.enabled) eq.turn(true)
                                    showPresets = false
                                },
                                headlineContent = {
                                    Text(preset.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = {
                                    IconButton(onClick = { eq.deleteCustom(preset.name) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить пресет")
                                    }
                                }
                            )
                        }
                    }
                    item {
                        Text(
                            "Стандартные",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    itemsIndexed(eq.presetNames) { i, name ->
                        ListItem(
                            modifier = Modifier.clickable {
                                eq.applyPreset(i)
                                if (!eq.enabled) eq.turn(true)
                                showPresets = false
                            },
                            headlineContent = { Text(name) }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresets = false }) { Text("Закрыть") } }
        )
    }

    if (showSave) {
        var name by remember { mutableStateOf(eq.currentCustomName ?: "") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Сохранить пресет") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        eq.saveCustom(name)
                        Toast.makeText(context, "Пресет «${name.trim()}» сохранён", Toast.LENGTH_SHORT).show()
                        showSave = false
                    }
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun EffectCard(
    title: String,
    hint: String,
    supported: Boolean,
    enabled: Boolean,
    strength: Int,
    onToggle: (Boolean) -> Unit,
    onStrength: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (supported) Switch(checked = enabled, onCheckedChange = onToggle)
            }
            if (!supported) {
                Text(
                    "Не поддерживается на этом устройстве",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Slider(
                    value = strength / 1000f,
                    onValueChange = { onStrength((it * 1000f).roundToInt()) },
                    valueRange = 0f..1f
                )
                Text(
                    "${strength / 10}% • $hint",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}