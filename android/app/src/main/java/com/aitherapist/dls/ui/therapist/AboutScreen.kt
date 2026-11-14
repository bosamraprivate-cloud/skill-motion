package com.aitherapist.dls.ui.therapist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aitherapist.dls.BuildConfig
import com.aitherapist.dls.R
import com.aitherapist.dls.catalog.CatalogRepository
import com.aitherapist.dls.scoring.RulesEngine
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.SectionCard
import com.aitherapist.dls.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One open-source credit: shown as plain text, never opened (no network calls). */
data class Credit(val library: String, val license: String, val link: String)

/**
 * Static credits list. Keep in sync with `android/gradle/libs.versions.toml`, README "Open-source credits" and
 * THIRD_PARTY_NOTICES.md. Licences checked against each library's POM in the Gradle cache on 2026-11-14
 * (SQLCipher's POM only links its licence page; the Community Edition licence there is BSD-3-Clause style).
 */
val OPEN_SOURCE_CREDITS: List<Credit> =
    listOf(
        Credit(
            "AndroidX (Core, Activity, Compose, Material 3, Adaptive, Navigation 3, Lifecycle, CameraX, Room, DataStore, Hilt)",
            "Apache-2.0",
            "developer.android.com/jetpack/androidx"
        ),
        Credit("Kotlin, kotlinx.serialization, kotlinx.coroutines", "Apache-2.0", "github.com/JetBrains/kotlin"),
        Credit("Hilt / Dagger", "Apache-2.0", "github.com/google/dagger"),
        Credit("MediaPipe Tasks Vision and landmarker models", "Apache-2.0", "github.com/google-ai-edge/mediapipe"),
        Credit("mediapipe-samples (adapted code)", "Apache-2.0", "github.com/google-ai-edge/mediapipe-samples"),
        Credit("SQLCipher for Android (Zetetic)", "BSD-3-Clause style (Zetetic)", "zetetic.net/sqlcipher/license"),
        Credit("OkHttp", "Apache-2.0", "github.com/square/okhttp"),
        Credit("Vico", "Apache-2.0", "github.com/patrykandpatrick/vico"),
        Credit("Konfetti", "ISC", "github.com/DanielMartinus/Konfetti"),
        Credit("Coil 3", "Apache-2.0", "github.com/coil-kt/coil"),
        Credit("compose-shimmer", "Apache-2.0", "github.com/valentinilk/compose-shimmer"),
        Credit("Konsist (tests)", "Apache-2.0", "github.com/LemonAppDev/konsist"),
        Credit("Turbine (tests)", "Apache-2.0", "github.com/cashapp/turbine"),
        Credit("JUnit 4 (tests)", "EPL-1.0", "junit.org/junit4"),
        Credit("Kover (build)", "Apache-2.0", "github.com/Kotlin/kotlinx-kover"),
        Credit("ktlint-gradle (build)", "MIT", "github.com/JLLeitschuh/ktlint-gradle")
    )

data class AboutInfo(val catalogVersion: String?)

@HiltViewModel
class AboutViewModel
@Inject
constructor(catalog: CatalogRepository) : ViewModel() {
    private val _info = MutableStateFlow(AboutInfo(null))
    val info: StateFlow<AboutInfo> = _info.asStateFlow()

    init {
        viewModelScope.launch { catalog.load().onSuccess { _info.value = AboutInfo(it.catalogVersion) } }
    }
}

@Composable
fun AboutRoute(viewModel: AboutViewModel = hiltViewModel()) {
    val info by viewModel.info.collectAsStateWithLifecycle()
    AboutScreen(info)
}

@Composable
fun AboutScreen(info: AboutInfo, modifier: Modifier = Modifier) {
    val width = Modifier.widthIn(max = 760.dp).fillMaxWidth()
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.S4),
        verticalArrangement = Arrangement.spacedBy(Spacing.S4)
    ) {
        PageHeader(stringResource(R.string.about_title))
        SectionCard(stringResource(R.string.about_app_name), width, icon = Icons.Filled.Info) {
            Text(stringResource(R.string.about_purpose), style = MaterialTheme.typography.bodyLarge)
            InfoLine(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
            InfoLine(stringResource(R.string.about_catalog_version, info.catalogVersion ?: "-"))
            InfoLine(stringResource(R.string.about_rules_version, RulesEngine.RULES_VERSION))
        }
        SectionCard(stringResource(R.string.about_credits_title), width, icon = Icons.Filled.Star) {
            OPEN_SOURCE_CREDITS.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column {
                    Text(c.library, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${c.license} · ${c.link}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
