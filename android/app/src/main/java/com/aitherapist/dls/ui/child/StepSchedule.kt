package com.aitherapist.dls.ui.child

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aitherapist.dls.ui.theme.ChildColors
import com.aitherapist.dls.ui.theme.Spacing

/**
 * One dot per step (DESIGN step-schedule, readable at about 1 m): passed = filled light sage, current = larger sage with
 * a white ring, upcoming = sand with a soft outline. No numbers, no failure marking, no red or orange.
 */
@Composable
fun StepSchedule(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S2),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            when {
                i < current -> Box(Modifier.size(DOT).background(ChildColors.ScheduleDone, CircleShape))
                i == current ->
                    Box(
                        Modifier
                            .size(CURRENT_DOT)
                            .border(RING, ChildColors.Surface, CircleShape)
                            .padding(RING)
                            .background(ChildColors.ScheduleCurrent, CircleShape)
                    )
                else ->
                    Box(
                        Modifier
                            .size(DOT)
                            .background(ChildColors.ScheduleTodo, CircleShape)
                            .border(RING, ChildColors.FramingWait, CircleShape)
                    )
            }
        }
    }
}

private val DOT = 20.dp
private val CURRENT_DOT = 36.dp
private val RING = 3.dp
