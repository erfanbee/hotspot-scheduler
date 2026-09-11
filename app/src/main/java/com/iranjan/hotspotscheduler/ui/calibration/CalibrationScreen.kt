package com.iranjan.hotspotscheduler.ui.calibration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.data.model.NodeDump

@Composable
fun CalibrationScreen(viewModel: CalibrationViewModel = hiltViewModel()) {
    val dumps by viewModel.dumps.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val saved by viewModel.saved.collectAsState()
    var pendingPick by remember { mutableStateOf<NodeDump?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.calib_title), style = MaterialTheme.typography.headlineSmall)
        Text(
            if (mode) stringResource(R.string.calib_mode_on) else stringResource(R.string.calib_mode_off),
            color = if (mode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        saved?.let {
            Text(stringResource(R.string.calib_current, "${it.type}=${it.value}"))
        } ?: Text(stringResource(R.string.calib_none_saved))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.start() }) { Text(stringResource(R.string.calib_start)) }
            OutlinedButton(onClick = { viewModel.rescan() }) { Text(stringResource(R.string.calib_rescan)) }
            if (mode) {
                OutlinedButton(onClick = { viewModel.stop() }) { Text(stringResource(R.string.calib_stop)) }
            }
            if (saved != null) {
                OutlinedButton(onClick = { viewModel.clear() }) { Text(stringResource(R.string.calib_clear)) }
            }
        }

        Text(stringResource(R.string.calib_tap_hint), style = MaterialTheme.typography.bodySmall)

        if (dumps.isEmpty()) {
            Text(stringResource(R.string.calib_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(dumps) { dump ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pendingPick = dump }
                    ) {
                        Text(
                            dump.display(),
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    pendingPick?.let { dump ->
        AlertDialog(
            onDismissRequest = { pendingPick = null },
            title = { Text(stringResource(R.string.calib_confirm_title)) },
            text = {
                Text(dump.display(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            },
            confirmButton = {
                TextButton(onClick = { viewModel.persist(dump); pendingPick = null }) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPick = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
