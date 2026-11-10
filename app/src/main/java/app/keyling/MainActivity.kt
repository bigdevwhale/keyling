package app.keyling

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import app.keyling.data.Settings
import app.keyling.domain.Lang
import app.keyling.ui.Root
import app.keyling.ui.theme.KeylingTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEMO && BuildConfig.DEMO_LANG.isNotEmpty() && AppCompatDelegate.getApplicationLocales().toLanguageTags() != BuildConfig.DEMO_LANG) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(BuildConfig.DEMO_LANG)); return } // the activity restarts in that language
        Lang.ru = resources.configuration.locales[0].language == "ru"
        // Demo builds fill a year of writing in the language they show; screenshots can open on a given screen.
        if (BuildConfig.DEMO) {
            container.scope.launch { app.keyling.data.DemoSeed.seedIfEmpty(container) }
            app.keyling.ui.Start.tab = intent.getIntExtra("tab", 0); app.keyling.ui.Start.sub = intent.getIntExtra("sub", 0)
            app.keyling.ui.Start.resident = intent.getStringExtra("res"); app.keyling.ui.Start.shop = intent.getIntExtra("shop", 0)
        }
        val t = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(t), navigationBarStyle = SystemBarStyle.light(t, t))
        setContent {
            val settings: Settings? by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val system = isSystemInDarkTheme()
            settings?.let { s ->
                val dark = s.theme == "dark" || (s.theme == "system" && system)
                container.sound.on = s.sound
                LaunchedEffect(dark) {
                    enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(t), navigationBarStyle = if (dark) SystemBarStyle.dark(t) else SystemBarStyle.light(t, t))
                }
                KeylingTheme(dark) { Root(s) }
            }
        }
    }
}

/** Switches the app language: "" follows the phone, "ru" or "en" pins it. The activity restarts in the new language. */
fun setLanguage(tag: String) = AppCompatDelegate.setApplicationLocales(if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
fun currentLanguage(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')
