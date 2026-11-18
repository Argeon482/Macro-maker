package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ExecutionLogEntity
import com.example.data.LogLevel
import com.example.engine.ExecutionStatus
import com.example.engine.RuntimeExecutionState
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextDim
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletVar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConsoleLogsScreen(
    executionState: RuntimeExecutionState,
    logs: List<ExecutionLogEntity>,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }

    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.level == selectedFilter }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg),
                title = {
                    Column {
                        Text("Execution Console", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text("LIVE ENGINE TELEMETRY & LOGS", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                    }
                },
                actions = {
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier.testTag("clear_logs_btn")
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear execution logs", tint = TextSecondary)
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    RuntimeTelemetryCard(
                        state = executionState,
                        onPauseOrResume = onPauseOrResume,
                        onStop = onStop
                    )
                }

                if (executionState.variables.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateSurface),
                            border = BorderStroke(1.dp, HairlineBorder),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("VARIABLE WATCH", style = MaterialTheme.typography.labelMedium, color = VioletVar)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    executionState.variables.forEach { (k, v) ->
                                        Surface(
                                            color = SlateWell,
                                            shape = RoundedCornerShape(8.dp),
                                            border = BorderStroke(1.dp, VioletVar.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "$k = $v",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = TextPrimary,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(null to "ALL", LogLevel.SUCCESS.name to "MATCH", LogLevel.WARN.name to "WARN", LogLevel.ERROR.name to "ERR").forEach { (lvl, label) ->
                            FilterChip(
                                selected = selectedFilter == lvl,
                                onClick = { selectedFilter = lvl },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                if (filteredLogs.isEmpty()) {
                    item {
                        Surface(
                            color = SlateSurface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No execution events recorded yet. Run a macro or trigger a Shizuku test tap in the Sandbox.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(filteredLogs, key = { it.id }) { log ->
                        val levelColor = when (log.level) {
                            LogLevel.SUCCESS.name -> EmeraldRun
                            LogLevel.WARN.name -> AmberLogic
                            LogLevel.ERROR.name -> RoseAlert
                            else -> ElectricCyan
                        }
                        Surface(
                            color = SlateSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HairlineBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(8.dp)
                                        .background(levelColor, RoundedCornerShape(2.dp))
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "${log.macroName} • ${log.stepLabel}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = levelColor
                                        )
                                        Text(
                                            text = timeFormatter.format(Date(log.timestamp)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextDim
                                        )
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = log.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
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
private fun RuntimeTelemetryCard(
    state: RuntimeExecutionState,
    onPauseOrResume: () -> Unit,
    onStop: () -> Unit
) {
    val accent = when (state.status) {
        ExecutionStatus.RUNNING -> EmeraldRun
        ExecutionStatus.PAUSED -> AmberLogic
        ExecutionStatus.ERROR -> RoseAlert
        ExecutionStatus.IDLE -> ElectricCyan
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SlateSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = state.activeMacroName.ifBlank { "Engine Idle" },
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "STATUS: ${state.status.name}  •  PASS ${state.currentLoopIteration}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }

                if (state.status == ExecutionStatus.RUNNING || state.status == ExecutionStatus.PAUSED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onPauseOrResume,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberLogic, contentColor = Color(0xFF1A1100)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(
                                imageVector = if (state.status == ExecutionStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Pause or resume"
                            )
                        }
                        Button(
                            onClick = onStop,
                            colors = ButtonDefaults.buttonColors(containerColor = RoseAlert, contentColor = Color.White),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                        }
                    }
                }
            }

            Text(
                text = state.errorMessage ?: state.lastTelemetry,
                style = MaterialTheme.typography.labelMedium,
                color = if (state.status == ExecutionStatus.ERROR) RoseAlert else TextSecondary
            )
        }
    }
}
