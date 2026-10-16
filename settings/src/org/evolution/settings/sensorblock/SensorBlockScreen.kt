/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.settings.sensorblock

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.android.settings.R
import com.android.settingslib.spa.framework.theme.SettingsTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SensorBlockScreen(refreshVersion: Int) {
    SettingsTheme {
        val background = colorResource(R.color.sensor_block_background)
        val cardColor = colorResource(R.color.sensor_block_card)
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
            background = background, surface = background, surfaceBright = cardColor,
        )) {
            SensorBlockAppList(refreshVersion)
        }
    }
}

@Composable
private fun SensorBlockAppList(refreshVersion: Int) {
    val context = LocalContext.current
    val repository = remember(context) { SensorBlockRepository(context) }
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<SensorBlockApp>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var savingPackage by remember { mutableStateOf<String?>(null) }
    var retryVersion by remember { mutableStateOf(0) }

    LaunchedEffect(repository, refreshVersion, retryVersion, savingPackage) {
        if (savingPackage != null) return@LaunchedEffect
        loading = true
        loadFailed = false
        try {
            apps = withContext(Dispatchers.IO) { repository.loadApps() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("SensorBlock", "Could not load applications", e)
            loadFailed = true
        } finally {
            loading = false
        }
    }

    val filtered = remember(apps, query) {
        val needle = query.trim()
        apps.filter {
            it.label.contains(needle, ignoreCase = true) ||
                it.packageName.contains(needle, ignoreCase = true)
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.sensor_block_explanation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.sensor_block_search)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close,
                                stringResource(R.string.sensor_block_clear_search))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
            when {
                loading && apps.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                loadFailed -> Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.sensor_block_load_error))
                    TextButton(onClick = { retryVersion++ }) {
                        Text(stringResource(R.string.sensor_block_retry))
                    }
                }
                filtered.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.sensor_block_no_apps)) }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        SensorBlockAppRow(app, enabled = savingPackage == null && !loading) { blocked ->
                            savingPackage = app.packageName
                            scope.launch {
                                try {
                                    val updated = withContext(Dispatchers.IO) {
                                        repository.setBlocked(app, blocked)
                                    }
                                    apps = apps.map {
                                        if (it.packageName == updated.packageName) updated else it
                                    }
                                } catch (e: Exception) {
                                    if (e is CancellationException) throw e
                                    Log.e("SensorBlock", "Could not set clipboard mode", e)
                                    Toast.makeText(context, R.string.sensor_block_save_error,
                                        Toast.LENGTH_LONG).show()
                                } finally {
                                    savingPackage = null
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorBlockAppRow(app: SensorBlockApp, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, app.info) {
        value = withContext(Dispatchers.IO) {
            try {
                app.info.loadIcon(context.packageManager).toBitmap(96, 96).asImageBitmap()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        }
    }
    val description = stringResource(R.string.sensor_block_block_app, app.label)
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .toggleable(app.blocked, enabled = enabled, role = Role.Switch,
                    onValueChange = onChange)
                .semantics { contentDescription = description }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(40.dp)) {
                icon?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(if (app.blocked) R.string.sensor_block_blocked
                    else R.string.sensor_block_normal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The whole row owns the switch semantics and click target.
            Switch(checked = app.blocked, onCheckedChange = null, enabled = enabled)
        }
    }
}
