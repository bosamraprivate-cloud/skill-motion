package com.aitherapist.dls

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aitherapist.dls.ui.AppNavigation
import com.aitherapist.dls.ui.theme.DlsTheme
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // The app is Egyptian Arabic (RTL) whatever the device language is.
    override fun attachBaseContext(newBase: Context) {
        val config = Configuration(newBase.resources.configuration).apply { setLocale(APP_LOCALE) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DlsTheme {
                AppNavigation()
            }
        }
    }

    private companion object {
        val APP_LOCALE: Locale = Locale.forLanguageTag("ar-EG")
    }
}
