package org.fossify.keyboard

import android.app.Application
import android.graphics.Color
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat
import org.fossify.commons.extensions.checkUseEnglish
import org.fossify.commons.extensions.getSharedPrefs
import org.fossify.keyboard.extensions.config
import org.fossify.keyboard.extensions.isDeviceInDirectBootMode

class App : Application() {
    companion object {
        private const val KEYNOVA_THEME_INITIALIZED = "keynova_theme_initialized_v1"
    }

    override fun onCreate() {
        super.onCreate()
        if (!isDeviceInDirectBootMode) {
            checkUseEnglish()
            applyDefaultKeynovaThemeIfFirstRun()
        }
        setupEmojiCompat()
    }

    private fun applyDefaultKeynovaThemeIfFirstRun() {
        val prefs = getSharedPrefs()
        if (!prefs.getBoolean(KEYNOVA_THEME_INITIALIZED, false)) {
            config.apply {
                isSystemThemeEnabled = false
                backgroundColor = Color.parseColor("#0B1020")
                primaryColor = Color.parseColor("#4F46E5")
                accentColor = Color.parseColor("#00D4FF")
                textColor = Color.parseColor("#F1F5F9")
                appIconColor = Color.parseColor("#3F51B5")
            }
            prefs.edit().putBoolean(KEYNOVA_THEME_INITIALIZED, true).apply()
        }
    }

    private fun setupEmojiCompat() {
        val config = BundledEmojiCompatConfig(this)
        EmojiCompat.init(config)
    }
}
