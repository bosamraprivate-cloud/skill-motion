package com.aitherapist.dls.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.R
import com.aitherapist.dls.ui.theme.DlsTheme
import com.valentinilk.shimmer.shimmer
import kotlinx.coroutines.delay

/** Delay before a skeleton appears, so fast loads never flash (AD-13). */
const val SKELETON_DELAY_MS = 150L

/** Shimmering placeholder rows for therapist lists and cards. Never used in child mode. */
@Composable
fun Skeleton(rows: Int, modifier: Modifier = Modifier, delayMs: Long = SKELETON_DELAY_MS) {
    var visible by remember { mutableStateOf(delayMs <= 0L) }
    LaunchedEffect(delayMs) {
        delay(delayMs)
        visible = true
    }
    if (!visible) {
        Spacer(modifier)
        return
    }
    val description = stringResource(R.string.loading_description)
    Column(
        modifier = modifier.fillMaxWidth().shimmer().semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(rows) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            )
        }
    }
}

@Preview
@Composable
private fun SkeletonPreview() {
    DlsTheme { Skeleton(rows = 3, delayMs = 0L) }
}
