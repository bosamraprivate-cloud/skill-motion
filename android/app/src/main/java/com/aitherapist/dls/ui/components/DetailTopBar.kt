package com.aitherapist.dls.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.DlsTheme
import com.aitherapist.dls.ui.theme.Spacing

/**
 * Top bar of a detail page shown on its own (portrait list-detail, Settings sub-pages): a 48 dp back icon button
 * (auto-mirrored for RTL) and the title of the current child or page, with a quiet divider below.
 */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = Spacing.S2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = Spacing.S2, end = Spacing.S3).semantics { heading() }
            )
            Row(
                Modifier.padding(end = Spacing.S2),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S2),
                verticalAlignment = Alignment.CenterVertically,
                content = actions
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Actions slot of the [DetailTopBar] above a portrait detail page: the page (for example the child profile) puts its
 * own actions there instead of repeating the title in a second heading. Null when no top bar is shown (two panes).
 */
class DetailBarActions {
    var content: (@Composable RowScope.() -> Unit)? by mutableStateOf(null)
}

val LocalDetailBarActions = staticCompositionLocalOf<DetailBarActions?> { null }

@Preview(widthDp = 704, heightDp = 120, locale = "ar")
@Composable
private fun DetailTopBarPreview() {
    DlsTheme { DetailTopBar(title = "ملف الطفل", onBack = {}) }
}
