package com.aitherapist.dls.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Spacing

/** One dot per step: passed light sage, current larger sage, upcoming sand. No numbers, no failure marking. */
@Composable
fun StepSchedule(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val (size, color) =
                when {
                    i < current -> 14.dp to ChildColors.ScheduleDone
                    i == current -> 22.dp to ChildColors.ScheduleCurrent
                    else -> 14.dp to ChildColors.ScheduleTodo
                }
            Box(Modifier.size(size).background(color, CircleShape))
        }
    }
}
