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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.components.PageHeader
import com.aitherapist.dls.ui.components.SectionCard
import com.aitherapist.dls.ui.theme.Spacing

/** About: a short product description only (owner decision). Open-source licences live in THIRD_PARTY_NOTICES.md. */
@Composable
fun AboutRoute() {
    AboutScreen()
}

@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.S4),
        verticalArrangement = Arrangement.spacedBy(Spacing.S4)
    ) {
        PageHeader(stringResource(R.string.about_title))
        SectionCard(stringResource(R.string.about_app_name), Modifier.widthIn(max = 760.dp).fillMaxWidth(), icon = Icons.Filled.Info) {
            Text(stringResource(R.string.about_purpose), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
