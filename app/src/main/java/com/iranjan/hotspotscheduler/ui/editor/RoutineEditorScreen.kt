@file:OptIn(ExperimentalMaterial3Api::class)

package com.iranjan.hotspotscheduler.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.iranjan.hotspotscheduler.R
import com.iranjan.hotspotscheduler.core.RoutineEvaluator
import com.iranjan.hotspotscheduler.ui.common.dayLabels

@Composable
fun RoutineEditorScreen(
    routineId: Long,
    onDone: () -> Unit,
    viewModel: RoutineEditorViewModel = hiltViewModel()
) {
    LaunchedEffect(routineId) { viewModel.load(routineId) }

    val draft by viewModel.draft.collectAsState()
    val overlapNames by viewModel.overlapNames.collectAsState()
    val labels = dayLabels()
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    val passwordInvalid = draft.hotspotPassword.isNotEmpty() && draft.hotspotPassword.length < 8

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (draft.id > 0) stringResource(R.string.editor_edit) else stringResource(R.string.editor_new),
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = draft.name,
            onValueChange = { value -> viewModel.update { it.copy(name = value) } },
            label = { Text(stringResource(R.string.editor_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text(stringResource(R.string.editor_days), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 2, 3, 4, 5, 6, 7).forEach { iso ->
                FilterChip(
                    selected = iso in draft.days,
                    onClick = { viewModel.toggleDay(iso) },
                    label = { Text(labels[iso] ?: "") }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_start) + "  " + RoutineEvaluator.formatMinutes(draft.startMinutes))
            }
            OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.editor_end) + "  " + RoutineEvaluator.formatMinutes(draft.endMinutes))
            }
        }

        Text(stringResource(R.string.editor_cap), style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.capText,
                onValueChange = { value -> viewModel.update { it.copy(capText = value.filter { ch -> ch.isDigit() || ch == '.' }) } },
                label = { Text(stringResource(R.string.editor_cap_hint)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )
            FilterChip(
                selected = draft.capIsGb,
                onClick = { viewModel.update { it.copy(capIsGb = true) } },
                label = { Text(stringResource(R.string.unit_gb)) }
            )
            FilterChip(
                selected = !draft.capIsGb,
                onClick = { viewModel.update { it.copy(capIsGb = false) } },
                label = { Text(stringResource(R.string.unit_mb)) }
            )
        }

        OutlinedTextField(
            value = draft.hotspotPassword,
            onValueChange = { value -> viewModel.update { it.copy(hotspotPassword = value.filter { ch -> !ch.isWhitespace() }) } },
            label = { Text(stringResource(R.string.editor_password)) },
            supportingText = {
                Text(
                    stringResource(
                        if (passwordInvalid) R.string.editor_password_error
                        else R.string.editor_password_hint
                    ),
                    color = if (passwordInvalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                }
            },
            isError = passwordInvalid,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.editor_mobile_data))
            Switch(checked = draft.mobileData, onCheckedChange = { value -> viewModel.update { it.copy(mobileData = value) } })
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.editor_enabled))
            Switch(checked = draft.enabled, onCheckedChange = { value -> viewModel.update { it.copy(enabled = value) } })
        }

        if (overlapNames.isNotEmpty()) {
            Text(
                stringResource(R.string.editor_overlap_warning, overlapNames.joinToString(", ")),
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { viewModel.save(onDone) }, enabled = !passwordInvalid, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.save))
            }
            if (draft.id > 0) {
                OutlinedButton(onClick = { viewModel.delete(onDone) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
        Row(Modifier.height(24.dp)) {}
    }

    if (showStartPicker) {
        TimePickerDialog(
            initialMinutes = draft.startMinutes,
            onConfirm = { minutes -> viewModel.update { it.copy(startMinutes = minutes) }; showStartPicker = false },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        TimePickerDialog(
            initialMinutes = draft.endMinutes,
            onConfirm = { minutes -> viewModel.update { it.copy(endMinutes = minutes) }; showEndPicker = false },
            onDismiss = { showEndPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = { TimePicker(state) }
    )
}
