package com.iranjan.hotspotscheduler.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.iranjan.hotspotscheduler.R

@Composable
fun dayLabels(): Map<Int, String> = mapOf(
    1 to stringResource(R.string.day_mon),
    2 to stringResource(R.string.day_tue),
    3 to stringResource(R.string.day_wed),
    4 to stringResource(R.string.day_thu),
    5 to stringResource(R.string.day_fri),
    6 to stringResource(R.string.day_sat),
    7 to stringResource(R.string.day_sun)
)
