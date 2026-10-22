package com.example.musicplayer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { getSharedPreferences("settings", Context.MODE_PRIVATE) }
            var uiScale by remember { mutableFloatStateOf(prefs.getFloat("ui_scale", 1f)) }

            MusicTheme(uiScale = uiScale) {
                MusicApp(
                    uiScale = uiScale,
                    onScaleChange = {
                        uiScale = it
                        prefs.edit().putFloat("ui_scale", it).apply()
                    }
                )
            }
        }
    }
}