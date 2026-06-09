package com.smartview.glassai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.smartview.glassai.R
import com.smartview.glassai.managers.LocalServerPreset
import com.smartview.glassai.viewmodels.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * Full-screen dialog (rendered inside an Android Dialog wrapper) for configuring
 * a local OpenAI-compatible AI server (Ollama, llama.cpp, LM Studio, vLLM...).
 */
@Composable
fun CustomServerSettingsContent(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val preset by viewModel.customPreset.collectAsStateValue()
    val savedBaseURL by viewModel.customBaseURL.collectAsStateValue()
    val savedModel by viewModel.customModel.collectAsStateValue()

    var baseURL by remember { mutableStateOf(savedBaseURL) }
    var modelName by remember { mutableStateOf(savedModel) }
    var apiKey by remember { mutableStateOf("") }
    var selectedPreset by remember { mutableStateOf(preset) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<SettingsViewModel.TestConnectionResult?>(null) }
    val scope = rememberCoroutineScope()

    // Keep local state in sync if the underlying values change (e.g. on first open)
    LaunchedEffect(savedBaseURL, savedModel) {
        if (baseURL.isBlank()) baseURL = savedBaseURL
        if (modelName.isBlank()) modelName = savedModel
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.settings_custom_title),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_custom_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        // 1) Preset
        Text(
            text = stringResource(R.string.settings_custom_preset),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_custom_preset_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))

        LocalServerPreset.entries.forEach { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPreset == p,
                    onClick = {
                        selectedPreset = p
                        if (p != LocalServerPreset.CUSTOM) {
                            // Auto-fill defaults, but only if the user hasn't typed something custom
                            if (baseURL.isBlank() || baseURL == LocalServerPreset.OLLAMA.defaultBaseURL || baseURL == savedBaseURL) {
                                baseURL = p.defaultBaseURL
                            }
                            if (modelName.isBlank() || modelName == LocalServerPreset.OLLAMA.defaultModel || modelName == savedModel) {
                                modelName = p.defaultModel
                            }
                        }
                        testResult = null
                    }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = p.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    val sub = presetSubtitle(p)
                    if (sub.isNotBlank()) {
                        Text(
                            text = sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // 2) URL & model
        Text(
            text = stringResource(R.string.settings_custom_connection),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = baseURL,
            onValueChange = {
                baseURL = it
                testResult = null
            },
            label = { Text(stringResource(R.string.settings_custom_url_hint)) },
            placeholder = { Text("http://192.168.1.10:11434/v1", fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Filled.Dns, contentDescription = null) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = modelName,
            onValueChange = {
                modelName = it
                testResult = null
            },
            label = { Text(stringResource(R.string.settings_custom_model_hint)) },
            placeholder = { Text("llava, llama3.2-vision, …", fontFamily = FontFamily.Monospace) },
            leadingIcon = { Icon(Icons.Filled.Memory, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3) API key (optional)
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text(stringResource(R.string.settings_custom_apikey_hint)) },
            placeholder = { Text(stringResource(R.string.settings_custom_apikey_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.VpnKey, contentDescription = null) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // 4) Test + save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isTesting = true
                        testResult = viewModel.testCustomServerConnection(baseURL, apiKey)
                        isTesting = false
                    }
                },
                enabled = !isTesting && baseURL.isNotBlank()
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(0.dp))
                } else {
                    Icon(Icons.Filled.Wifi, contentDescription = null)
                    Spacer(modifier = Modifier.height(0.dp))
                }
                Text(
                    text = if (isTesting)
                        stringResource(R.string.settings_custom_testing)
                    else
                        stringResource(R.string.settings_custom_test)
                )
            }

            TextButton(
                onClick = {
                    viewModel.saveCustomServerConfig(selectedPreset, baseURL, modelName, apiKey)
                    onDismiss()
                },
                enabled = baseURL.isNotBlank() && modelName.isNotBlank()
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Text(text = stringResource(R.string.save))
            }
        }

        // 5) Test result
        when (val r = testResult) {
            is SettingsViewModel.TestConnectionResult.Success -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_custom_test_success),
                    color = Color(0xFF1B873F),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (r.models.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.settings_custom_test_models),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    r.models.take(10).forEach { m ->
                        Text(
                            text = "• $m",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (r.models.size > 10) {
                        Text(
                            text = "+${r.models.size - 10} ${stringResource(R.string.settings_custom_test_more)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            is SettingsViewModel.TestConnectionResult.Failure -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.settings_custom_test_failure),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = r.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            null -> Unit
        }
    }
}

private fun presetSubtitle(p: LocalServerPreset): String = when (p) {
    LocalServerPreset.OLLAMA -> "http://localhost:11434/v1 — model: llava"
    LocalServerPreset.LLAMACPP -> "http://localhost:8080/v1 — model: local"
    LocalServerPreset.LMSTUDIO -> "http://localhost:1234/v1 — model: local-model"
    LocalServerPreset.VLLM -> "http://localhost:8000/v1 — model: local-model"
    LocalServerPreset.CUSTOM -> ""
}

/**
 * Lightweight helper that returns a [androidx.compose.runtime.State] for a
 * StateFlow. Composable-only. Top-level so other screens can use it.
 */
@Composable
fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateValue(): androidx.compose.runtime.State<T> {
    return this.collectAsState()
}
