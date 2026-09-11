package com.iranjan.hotspotscheduler.ui.setup

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.iranjan.hotspotscheduler.R

@Composable
fun SetupScreen(
    onCalibrate: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val testRunning by viewModel.testRunning.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var diagnostics by remember { mutableStateOf(com.iranjan.hotspotscheduler.accessibility.AttemptLog.snapshot()) }

    LaunchedEffect(testRunning) {
        while (testRunning) {
            diagnostics = com.iranjan.hotspotscheduler.accessibility.AttemptLog.snapshot()
            kotlinx.coroutines.delay(1000)
        }
        diagnostics = com.iranjan.hotspotscheduler.accessibility.AttemptLog.snapshot()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
                diagnostics = com.iranjan.hotspotscheduler.accessibility.AttemptLog.snapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    fun open(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (t: Throwable) {
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)

        SetupCard(
            title = stringResource(R.string.setup_accessibility_title),
            description = stringResource(R.string.setup_accessibility_desc),
            granted = state.accessibility,
            actionLabel = stringResource(R.string.setup_open)
        ) {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        SetupCard(
            title = stringResource(R.string.setup_usage_title),
            description = stringResource(R.string.setup_usage_desc),
            granted = state.usageAccess,
            actionLabel = stringResource(R.string.setup_open)
        ) {
            open(Intent("android.settings.USAGE_ACCESS_SETTINGS"))
        }

        SetupCard(
            title = stringResource(R.string.setup_notifications_title),
            description = stringResource(R.string.setup_notifications_desc),
            granted = state.notifications,
            actionLabel = stringResource(R.string.setup_open)
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        SetupCard(
            title = stringResource(R.string.setup_exact_title),
            description = stringResource(R.string.setup_exact_desc),
            granted = state.exactAlarms,
            actionLabel = stringResource(R.string.setup_open)
        ) {
            open(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }

        SetupCard(
            title = stringResource(R.string.setup_battery_title),
            description = stringResource(R.string.setup_battery_desc),
            granted = state.battery,
            actionLabel = stringResource(R.string.setup_battery_action)
        ) {
            open(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }

        SetupCard(
            title = stringResource(R.string.setup_overlay_title),
            description = stringResource(R.string.setup_overlay_desc),
            granted = state.overlay,
            actionLabel = stringResource(R.string.setup_open)
        ) {
            open(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.setup_sleep_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.setup_sleep_desc), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.test_section_title), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.testHotspot(true) }, enabled = !testRunning, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.test_hotspot_on))
                    }
                    Button(onClick = { viewModel.testHotspot(false) }, enabled = !testRunning, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.test_hotspot_off))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { viewModel.testMobileData(true) }, enabled = !testRunning, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.test_data_on))
                    }
                    Button(onClick = { viewModel.testMobileData(false) }, enabled = !testRunning, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.test_data_off))
                    }
                }
                if (testRunning) {
                    Text(stringResource(R.string.test_running), style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick = {
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                    }
                    try {
                        context.startActivity(Intent.createChooser(share, null))
                    } catch (t: Throwable) {
                    }
                }) {
                    Text(stringResource(R.string.diag_share))
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .padding(14.dp)
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.setup_diag_title), style = MaterialTheme.typography.titleMedium)
                if (diagnostics.isEmpty()) {
                    Text(stringResource(R.string.setup_diag_empty), style = MaterialTheme.typography.bodySmall)
                } else {
                    diagnostics.takeLast(60).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.setup_calibrate_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.setup_calibrate_desc), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onCalibrate) {
                    Text(stringResource(R.string.setup_calibrate_open))
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SetupCard(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            stringResource(if (granted) R.string.setup_granted else R.string.setup_missing),
                            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
