package com.tingxia.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.BuildConfig
import com.tingxia.app.R
import com.tingxia.app.data.repo.PlaybackErrorPolicy
import com.tingxia.app.data.repo.ThemeMode
import com.tingxia.app.player.PlaybackSpeeds
import com.tingxia.app.ui.components.SectionCard
import com.tingxia.app.ui.theme.dynamicColorSupported

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenStats: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val speed by viewModel.defaultSpeed.collectAsStateWithLifecycle()
    val errorPolicy by viewModel.playbackErrorPolicy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var speedExpanded by remember { mutableStateOf(false) }

    val exportBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? -> uri?.let(viewModel::exportBackup) }
    val importBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> uri?.let(viewModel::importBackup) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Appearance
            SettingsGroup(icon = Icons.Default.Palette, title = stringResource(R.string.appearance)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val themes = listOf(
                        Triple(ThemeMode.SYSTEM, stringResource(R.string.theme_system), Icons.Default.PhoneAndroid),
                        Triple(ThemeMode.LIGHT, stringResource(R.string.theme_light), Icons.Default.LightMode),
                        Triple(ThemeMode.DARK, stringResource(R.string.theme_dark), Icons.Default.DarkMode),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        themes.forEachIndexed { index, (mode, label, icon) ->
                            SegmentedButton(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, themes.size),
                                icon = {
                                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                    if (dynamicColorSupported) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setDynamicColor(!dynamicColor) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.dynamic_color_summary),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) },
                            )
                        }
                    }
                }
            }

            // Playback
            SettingsGroup(icon = Icons.Default.PlayCircle, title = stringResource(R.string.playback)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.default_speed),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box {
                        OutlinedButton(
                            onClick = { speedExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(PlaybackSpeeds.label(speed))
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = speedExpanded,
                            onDismissRequest = { speedExpanded = false },
                        ) {
                            PlaybackSpeeds.ALL.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(PlaybackSpeeds.label(option)) },
                                    onClick = {
                                        viewModel.setDefaultSpeed(option)
                                        speedExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.playback_error_policy), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.playback_error_policy_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    val policies = listOf(
                        PlaybackErrorPolicy.STOP to stringResource(R.string.playback_error_stop),
                        PlaybackErrorPolicy.SKIP to stringResource(R.string.playback_error_skip),
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        policies.forEachIndexed { index, (policy, label) ->
                            SegmentedButton(
                                selected = errorPolicy == policy,
                                onClick = { viewModel.setPlaybackErrorPolicy(policy) },
                                shape = SegmentedButtonDefaults.itemShape(index, policies.size),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            // Backup
            SettingsGroup(icon = Icons.Default.Storage, title = stringResource(R.string.backup_restore)) {
                Column {
                    SettingsActionRow(
                        icon = Icons.Default.FileUpload,
                        title = stringResource(R.string.export_backup),
                        subtitle = stringResource(R.string.backup_restore_summary),
                        onClick = { exportBackup.launch("tingxia-backup.json") },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 52.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    SettingsActionRow(
                        icon = Icons.Default.FileDownload,
                        title = stringResource(R.string.import_backup),
                        subtitle = null,
                        onClick = { importBackup.launch(arrayOf("application/json", "text/json", "text/plain")) },
                    )
                }
            }

            // Offline cache (online audiobooks)
            val cacheBytes by viewModel.cacheBytes.collectAsStateWithLifecycle()
            LaunchedEffect(Unit) { viewModel.refreshCacheUsage() }
            SettingsGroup(icon = Icons.Default.CloudDownload, title = stringResource(R.string.cache_menu)) {
                Column {
                    SettingsActionRow(
                        icon = Icons.Default.CloudDownload,
                        title = stringResource(
                            R.string.cache_usage,
                            android.text.format.Formatter.formatShortFileSize(
                                androidx.compose.ui.platform.LocalContext.current, cacheBytes,
                            ),
                        ),
                        subtitle = stringResource(R.string.cache_usage_summary),
                        onClick = { viewModel.refreshCacheUsage() },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 52.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    SettingsActionRow(
                        icon = Icons.Default.DeleteSweep,
                        title = stringResource(R.string.clear_cache),
                        subtitle = null,
                        onClick = { viewModel.clearCache() },
                    )
                }
            }

            // Listening stats
            SettingsGroup(icon = Icons.Default.EmojiEvents, title = stringResource(R.string.stats_title)) {
                Column {
                    SettingsActionRow(
                        icon = Icons.Default.EmojiEvents,
                        title = stringResource(R.string.stats_open),
                        subtitle = stringResource(R.string.stats_open_summary),
                        onClick = onOpenStats,
                    )
                }
            }

            // About
            SettingsGroup(icon = Icons.Default.Info, title = stringResource(R.string.about)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_description), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.version_format, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.about_battery_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        SectionCard(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
