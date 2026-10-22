package com.example.musicplayer

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Пресет, сохранённый пользователем: название и уровни всех полос. */
data class CustomPreset(val name: String, val levels: List<Int>)

/**
 * Управляет системным эквалайзером, подключённым к плееру.
 * Настройки (включён ли, уровни полос, пресет) запоминаются.
 */
object EqualizerManager {
    private var prefs: SharedPreferences? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null

    var available by mutableStateOf(false)
        private set
    var enabled by mutableStateOf(false)
        private set
    var minLevel by mutableIntStateOf(-1500)
        private set
    var maxLevel by mutableIntStateOf(1500)
        private set
    var currentPreset by mutableIntStateOf(-1)
        private set
    var freqsHz by mutableStateOf<List<Int>>(emptyList())
        private set
    var presetNames by mutableStateOf<List<String>>(emptyList())
        private set
    val levels = mutableStateListOf<Int>()

    /** Свои пресеты, сохранённые пользователем. */
    var customPresets by mutableStateOf<List<CustomPreset>>(emptyList())
        private set

    /** Название своего пресета, с которого начата настройка (его перезаписывает кнопка «Сохранить»). */
    var currentCustomName by mutableStateOf<String?>(null)
        private set

    // Усиление басов и объёмный звук (сила от 0 до 1000)
    var bassSupported by mutableStateOf(false)
        private set
    var bassEnabled by mutableStateOf(false)
        private set
    var bassStrength by mutableIntStateOf(500)
        private set
    var virtSupported by mutableStateOf(false)
        private set
    var virtEnabled by mutableStateOf(false)
        private set
    var virtStrength by mutableIntStateOf(500)
        private set

    /** Текстовый статус для диагностики (показывается на экране эквалайзера). */
    var status by mutableStateOf("attach не вызывался: проверьте, что файл MusicService.kt заменён на новый")
        private set

    /** Вызывается сервисом, когда у плеера появился (или сменился) аудио-сеанс. */
    fun attach(context: Context, sessionId: Int) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
        }
        customPresets = loadCustom()
        currentCustomName = prefs?.getString("custom_name", null)
        bassEnabled = prefs?.getBoolean("bass_on", false) ?: false
        bassStrength = prefs?.getInt("bass", 500) ?: 500
        virtEnabled = prefs?.getBoolean("virt_on", false) ?: false
        virtStrength = prefs?.getInt("virt", 500) ?: 500
        release()
        if (sessionId == 0) {
            status = "у плеера нет аудио-сеанса (id = 0)"
            return
        }
        try {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()
            freqsHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 }
            presetNames = (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()) }

            val saved = prefs?.getString("levels", null)
                ?.split(",")
                ?.mapNotNull { it.toIntOrNull() }
            val wantEnabled = prefs?.getBoolean("enabled", false) ?: false

            levels.clear()
            for (i in 0 until bands) {
                val level = if (saved != null && saved.size == bands) {
                    saved[i].coerceIn(minLevel, maxLevel)
                } else {
                    eq.getBandLevel(i.toShort()).toInt()
                }
                eq.setBandLevel(i.toShort(), level.toShort())
                levels.add(level)
            }
            currentPreset = prefs?.getInt("preset", -1) ?: -1
            eq.setEnabled(wantEnabled)
            enabled = wantEnabled
            available = true
            status = "ок (сеанс $sessionId, полос: $bands)"
        } catch (e: Throwable) {
            e.printStackTrace()
            equalizer = null
            available = false
            status = "ошибка: ${e.javaClass.simpleName}: ${e.message}"
        }
        attachEffects(sessionId)
    }

    private fun attachEffects(sessionId: Int) {
        try {
            val b = BassBoost(0, sessionId)
            bassSupported = b.strengthSupported
            if (bassSupported) {
                b.setStrength(bassStrength.toShort())
                b.setEnabled(bassEnabled)
            }
            bassBoost = b
        } catch (e: Throwable) {
            bassSupported = false
            bassBoost = null
        }
        try {
            val v = Virtualizer(0, sessionId)
            virtSupported = v.strengthSupported
            if (virtSupported) {
                v.setStrength(virtStrength.toShort())
                v.setEnabled(virtEnabled)
            }
            virtualizer = v
        } catch (e: Throwable) {
            virtSupported = false
            virtualizer = null
        }
    }

    fun turnBass(on: Boolean) {
        try {
            bassBoost?.setEnabled(on)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bassEnabled = on
        prefs?.edit()?.putBoolean("bass_on", on)?.apply()
    }

    fun changeBass(strength: Int) {
        val value = strength.coerceIn(0, 1000)
        bassStrength = value
        try {
            bassBoost?.setStrength(value.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs?.edit()?.putInt("bass", value)?.apply()
        if (!bassEnabled && value > 0) turnBass(true)
    }

    fun turnVirtualizer(on: Boolean) {
        try {
            virtualizer?.setEnabled(on)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        virtEnabled = on
        prefs?.edit()?.putBoolean("virt_on", on)?.apply()
    }

    fun changeVirtualizer(strength: Int) {
        val value = strength.coerceIn(0, 1000)
        virtStrength = value
        try {
            virtualizer?.setStrength(value.toShort())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        prefs?.edit()?.putInt("virt", value)?.apply()
        if (!virtEnabled && value > 0) turnVirtualizer(true)
    }

    fun release() {
        try {
            equalizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        equalizer = null
        try {
            bassBoost?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        bassBoost = null
        try {
            virtualizer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        virtualizer = null
        available = false
    }

    /** Копии эквалайзера, усиления басов и объёмного звука для второго плеера (плавный переход). */
    fun createMirrorEffects(sessionId: Int): List<AudioEffect> {
        val list = ArrayList<AudioEffect>()
        createMirror(sessionId)?.let { list.add(it) }
        if (sessionId != 0) {
            if (bassSupported && bassEnabled) {
                try {
                    val b = BassBoost(0, sessionId)
                    b.setStrength(bassStrength.toShort())
                    b.setEnabled(true)
                    list.add(b)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
            if (virtSupported && virtEnabled) {
                try {
                    val v = Virtualizer(0, sessionId)
                    v.setStrength(virtStrength.toShort())
                    v.setEnabled(true)
                    list.add(v)
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
        return list
    }

    /** Копия текущих настроек для второго плеера (нужна для плавного перехода между песнями). */
    fun createMirror(sessionId: Int): Equalizer? =
        try {
            if (!enabled || sessionId == 0 || levels.isEmpty()) {
                null
            } else {
                val mirror = Equalizer(0, sessionId)
                for (i in levels.indices) {
                    if (i < mirror.numberOfBands) {
                        mirror.setBandLevel(i.toShort(), levels[i].toShort())
                    }
                }
                mirror.setEnabled(true)
                mirror
            }
        } catch (e: Exception) {
            null
        }
    fun turn(on: Boolean) {
        equalizer?.setEnabled(on)
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
    }

    fun changeBand(index: Int, level: Int) {
        val eq = equalizer ?: return
        if (index !in levels.indices) return
        val value = level.coerceIn(minLevel, maxLevel)
        eq.setBandLevel(index.toShort(), value.toShort())
        levels[index] = value
        currentPreset = -1
        save()
    }

    fun applyPreset(index: Int) {
        val eq = equalizer ?: return
        if (index !in presetNames.indices) return
        eq.usePreset(index.toShort())
        for (i in levels.indices) {
            levels[i] = eq.getBandLevel(i.toShort()).toInt()
        }
        currentPreset = index
        currentCustomName = null
        save()
    }

    fun resetFlat() {
        val eq = equalizer ?: return
        for (i in levels.indices) {
            eq.setBandLevel(i.toShort(), 0)
            levels[i] = 0
        }
        currentPreset = -1
        currentCustomName = null
        save()
    }

    private fun save() {
        prefs?.edit()
            ?.putString("levels", levels.joinToString(","))
            ?.putInt("preset", currentPreset)
            ?.putString("custom_name", currentCustomName)
            ?.apply()
    }

    // ---------- Свои пресеты ----------

    private fun loadCustom(): List<CustomPreset> {
        val raw = prefs?.getString("custom_presets", null) ?: return emptyList()
        return try {
            val obj = JSONObject(raw)
            obj.keys().asSequence().map { name ->
                val arr = obj.getJSONArray(name)
                CustomPreset(name, (0 until arr.length()).map { arr.getInt(it) })
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistCustom() {
        val obj = JSONObject()
        customPresets.forEach { p ->
            val arr = JSONArray()
            p.levels.forEach { arr.put(it) }
            obj.put(p.name, arr)
        }
        prefs?.edit()?.putString("custom_presets", obj.toString())?.apply()
    }

    /** Сохраняет текущую кривую как свой пресет (с тем же названием — перезаписывает). */
    fun saveCustom(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        customPresets = customPresets.filterNot { it.name.equals(clean, ignoreCase = true) } +
                CustomPreset(clean, levels.toList())
        currentCustomName = clean
        currentPreset = -1
        persistCustom()
        save()
    }

    fun applyCustom(preset: CustomPreset) {
        val eq = equalizer ?: return
        for (i in levels.indices) {
            val value = (preset.levels.getOrNull(i) ?: 0).coerceIn(minLevel, maxLevel)
            eq.setBandLevel(i.toShort(), value.toShort())
            levels[i] = value
        }
        currentPreset = -1
        currentCustomName = preset.name
        save()
    }

    fun deleteCustom(name: String) {
        customPresets = customPresets.filterNot { it.name == name }
        if (currentCustomName == name) currentCustomName = null
        persistCustom()
        save()
    }
}