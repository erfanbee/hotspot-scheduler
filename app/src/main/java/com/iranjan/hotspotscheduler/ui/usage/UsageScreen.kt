package com.iranjan.hotspotscheduler.ui.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.data.db.UsageDayEntity
import com.iranjan.hotspotscheduler.util.Formatters
import java.time.LocalDate

@Composable
fun UsageScreen(viewModel: UsageViewModel = hiltViewModel()) {
    val days by viewModel.days.collectAsState()
    val ascending = days.sortedBy { it.epochDay }
    val today = days.maxByOrNull { it.epochDay }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.usage_title), style = MaterialTheme.typography.headlineSmall)

        if (today != null) {
            Text(
                stringResource(R.string.usage_today, Formatters.formatBytes(today.bytes)),
                style = MaterialTheme.typography.titleLarge
            )
        } else {
            Text(stringResource(R.string.usage_no_data))
        }

        Text(stringResource(R.string.usage_days_7), style = MaterialTheme.typography.titleMedium)
        UsageChart(ascending)
    }
}

@Composable
private fun UsageChart(daysAsc: List<UsageDayEntity>) {
    val barColor = MaterialTheme.colorScheme.primary
    val maxBytes = (daysAsc.maxOfOrNull { it.bytes } ?: 0L).coerceAtLeast(1024L * 1024L)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        if (daysAsc.isEmpty()) return@Canvas
        val n = daysAsc.size
        val gap = 12.dp.toPx()
        val barWidth = (size.width - gap * (n - 1)) / n
        daysAsc.forEachIndexed { index, day ->
            val h = (day.bytes.toFloat() / maxBytes) * size.height
            drawRoundRect(
                color = barColor,
                topLeft = Offset(index * (barWidth + gap), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        daysAsc.forEach { day ->
            Text(
                LocalDate.ofEpochDay(day.epochDay).dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(0.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text(
            if (daysAsc.isNotEmpty()) Formatters.formatBytes(maxBytes) else "",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
