package dev.busung.s25uroot

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.busung.s25uroot.ui.theme.RootMyGalaxyTheme
import kotlinx.coroutines.delay
import java.io.File

class AutoRootActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RootMyGalaxyTheme(
                accentColor = AppPreferences.accentColor(this),
                themeMode = AppPreferences.themeMode(this),
            ) {
                val autoRootViewModel: AutoRootViewModel = viewModel()
                val installState by autoRootViewModel.state.collectAsStateWithLifecycle()
                BackHandler(enabled = installState.busy) {}
                AutoRootScreen(
                    installState = installState,
                    onRetry = { autoRootViewModel.start() },
                    onClose = { finish() },
                )
            }
        }
    }
}

@Composable
private fun AutoRootScreen(
    installState: AutoRootUiState,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val logScrollState = rememberScrollState()
    LaunchedEffect(installState.log) {
        delay(40)
        logScrollState.scrollTo(logScrollState.maxValue)
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.install_title),
                    style = MaterialTheme.typography.headlineLarge,
                )
                Text(
                    text = if (installState.busy) {
                        stringResource(R.string.install_keep_open)
                    } else {
                        installState.message
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InstallerStatusCard(installState)
            InstallerSteps(installState.phase)
            InstallerLog(
                output = installState.log,
                modifier = Modifier.weight(1f),
                scrollState = logScrollState,
            )

            if (!installState.busy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (installState.phase == AutoRootPhase.Failed) {
                        FilledTonalButton(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_close))
                        }
                        Button(
                            onClick = onRetry,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_retry))
                        }
                    } else if (installState.phase == AutoRootPhase.Done) {
                        Button(
                            onClick = onClose,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.action_done))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallerStatusCard(installState: AutoRootUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = when (installState.phase) {
                AutoRootPhase.Failed -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
            contentColor = if (installState.phase == AutoRootPhase.Failed) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (installState.busy) {
                    LoadingIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else if (installState.phase == AutoRootPhase.Done) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                } else {
                    Icon(
                        Icons.Rounded.Error,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = installState.message,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = autoRootPhaseDetail(installState.phase),
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
            }
            LinearProgressIndicator(
                progress = { autoRootProgress(installState.phase) },
                modifier = Modifier.fillMaxWidth(),
                color = LocalContentColor.current,
                trackColor = LocalContentColor.current.copy(alpha = 0.2f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
private fun InstallerSteps(phase: AutoRootPhase) {
    val steps = listOf(
        Triple(R.string.step_support_title, R.string.step_support_detail, Icons.Rounded.Security),
        Triple(R.string.step_download_title, R.string.step_download_detail, Icons.Rounded.CloudDownload),
        Triple(R.string.step_exploit_title, R.string.step_exploit_detail, Icons.Rounded.Memory),
        Triple(R.string.step_ksu_title, R.string.step_ksu_detail, Icons.Rounded.Check),
    )
    val activeIndex = when (phase) {
        AutoRootPhase.Checking, AutoRootPhase.Failed -> 0
        AutoRootPhase.Downloading -> 1
        AutoRootPhase.Exploiting -> 2
        AutoRootPhase.LoadingKernelSu -> 3
        AutoRootPhase.Done -> 4
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            steps.forEachIndexed { index, (titleRes, detailRes, icon) ->
                val stepState = when {
                    index < activeIndex -> 2
                    index == activeIndex && phase != AutoRootPhase.Failed && phase != AutoRootPhase.Done -> 1
                    else -> 0
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = if (stepState >= 1) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = if (stepState >= 1) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (stepState == 2) Icons.Rounded.Check else icon,
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(titleRes),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(detailRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f),
                        )
                    }
                    if (stepState == 1 && phase !in setOf(AutoRootPhase.Failed, AutoRootPhase.Done)) {
                        LoadingIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallerLog(
    output: String,
    modifier: androidx.compose.ui.Modifier,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.install_live_progress),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = output.ifBlank { stringResource(R.string.install_preparing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun autoRootPhaseDetail(phase: AutoRootPhase): String = stringResource(
    when (phase) {
        AutoRootPhase.Checking -> R.string.phase_checking
        AutoRootPhase.Downloading -> R.string.phase_downloading
        AutoRootPhase.Exploiting -> R.string.phase_exploiting
        AutoRootPhase.LoadingKernelSu -> R.string.phase_loading_ksu
        AutoRootPhase.Done -> R.string.phase_installed
        AutoRootPhase.Failed -> R.string.phase_failed
    },
)

private fun autoRootProgress(phase: AutoRootPhase): Float = when (phase) {
    AutoRootPhase.Checking -> 0.1f
    AutoRootPhase.Downloading -> 0.3f
    AutoRootPhase.Exploiting -> 0.6f
    AutoRootPhase.LoadingKernelSu -> 0.85f
    AutoRootPhase.Done -> 1f
    AutoRootPhase.Failed -> 0f
}
