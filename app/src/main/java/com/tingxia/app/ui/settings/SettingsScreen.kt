package com.tingxia.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tingxia.app.BuildConfig
import com.tingxia.app.player.PlaybackSpeeds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val dark by viewModel.darkTheme.collectAsStateWithLifecycle()
    val speed by viewModel.defaultSpeed.collectAsStateWithLifecycle()
    var speedExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("深色主题", modifier = Modifier.weight(1f))
                Switch(
                    checked = dark != false,
                    onCheckedChange = { viewModel.setDarkTheme(it) },
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("播放", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("默认倍速", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            ExposedDropdownMenuBox(
                expanded = speedExpanded,
                onExpandedChange = { speedExpanded = it },
            ) {
                TextField(
                    value = PlaybackSpeeds.label(speed),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = speedExpanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = speedExpanded,
                    onDismissRequest = { speedExpanded = false },
                ) {
                    PlaybackSpeeds.ALL.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(PlaybackSpeeds.label(s)) },
                            onClick = {
                                viewModel.setDefaultSpeed(s)
                                speedExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("后台播放提示", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "部分国产系统会限制后台。若锁屏后中断，请在系统设置中为听匣关闭电池优化，并允许自启动/关联启动（按需）。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))
            Text("关于", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "听匣 · 本地有声书播放器",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
