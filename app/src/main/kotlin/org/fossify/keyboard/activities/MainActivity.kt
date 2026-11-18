package org.fossify.keyboard.activities

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdView
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_GSON
import org.fossify.commons.models.FAQItem
import org.fossify.keyboard.BuildConfig
import org.fossify.keyboard.R
import org.fossify.keyboard.databinding.ActivityMainBinding
import org.fossify.keyboard.dialogs.ManageKeyboardLanguagesDialog
import org.fossify.keyboard.extensions.inputMethodManager
import org.fossify.keyboard.helpers.AdManager

class MainActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityMainBinding::inflate)
    private var bannerAdView: AdView? = null
    private var bannerRequested = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()

        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(adBannerContainer, mainNestedScrollview))
            setupMaterialScrollListener(binding.mainNestedScrollview, binding.mainAppbar)

            stepEnableKeyboard.setOnClickListener {
                Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(this)
                }
            }

            changeKeyboardHolder.setOnClickListener {
                inputMethodManager.showInputMethodPicker()
            }

            actionTheme.setOnClickListener {
                hideKeyboard()
                startCustomizationActivity()
            }

            actionLanguages.setOnClickListener {
                ManageKeyboardLanguagesDialog(this@MainActivity) {}
            }

            actionClipboard.setOnClickListener {
                hideKeyboard()
                AdManager.maybeShowInterstitial(this@MainActivity) {
                    startActivity(Intent(applicationContext, ManageClipboardItemsActivity::class.java))
                }
            }

            actionSettings.setOnClickListener {
                launchSettings()
            }
        }

        AdManager.gatherConsentAndInitialize(this) {
            runOnUiThread {
                if (!isFinishing && !isDestroyed && !bannerRequested) {
                    bannerRequested = true
                    binding.adBannerContainer.post {
                        bannerAdView = AdManager.loadAdaptiveBanner(this, binding.adBannerContainer)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.mainAppbar)
        updateSetupStatus()
        bannerAdView?.resume()
    }

    override fun onPause() {
        bannerAdView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        bannerAdView?.destroy()
        bannerAdView = null
        super.onDestroy()
    }

    private fun setupOptionsMenu() {
        binding.mainToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun launchSettings() {
        hideKeyboard()
        AdManager.maybeShowInterstitial(this) {
            startActivity(Intent(applicationContext, SettingsActivity::class.java))
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GSON
        val faqItems = ArrayList<FAQItem>()
        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun updateSetupStatus() {
        val enabled = isKeyboardEnabled()
        val selected = isKeyboardSelected()

        val readyBg = ContextCompat.getDrawable(this, R.drawable.keynova_badge_ready)
        val pendingBg = ContextCompat.getDrawable(this, R.drawable.keynova_badge_pending)
        val readyColor = ContextCompat.getColor(this, R.color.keynova_success)
        val pendingColor = ContextCompat.getColor(this, R.color.keynova_accent)

        binding.apply {
            step1Badge.text = getString(if (enabled) R.string.keynova_status_done else R.string.keynova_status_tap)
            step1Badge.background = if (enabled) readyBg else pendingBg
            step1Badge.setTextColor(if (enabled) readyColor else pendingColor)

            step2Badge.text = getString(if (selected) R.string.keynova_status_done else R.string.keynova_status_tap)
            step2Badge.background = if (selected) readyBg else pendingBg
            step2Badge.setTextColor(if (selected) readyColor else pendingColor)

            val fullyReady = enabled && selected
            heroStatusBadge.text = getString(
                if (fullyReady) R.string.keynova_status_ready else R.string.keynova_status_setup_needed
            )
            heroStatusBadge.background = if (fullyReady) readyBg else pendingBg
            heroStatusBadge.setTextColor(if (fullyReady) readyColor else pendingColor)
        }
    }

    private fun isKeyboardEnabled(): Boolean {
        return inputMethodManager.enabledInputMethodList.any {
            it.packageName == packageName
        }
    }

    private fun isKeyboardSelected(): Boolean {
        val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return defaultIme?.startsWith("$packageName/") == true
    }
}
