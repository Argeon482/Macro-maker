package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.shizuku.ShizukuConnectionState
import com.example.shizuku.ShizukuStatus
import com.example.ui.theme.AmberLogic
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldRun
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateSurface
import com.example.ui.theme.SlateWell
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class SandboxTouchDot(
    val x: Float,
    val y: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TouchSandboxScreen(
    shizukuStatus: ShizukuStatus,
    onTestTap: (x: Float, y: Float) -> Unit,
    onTestSwipe: (sx: Float, sy: Float, ex: Float, ey: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var targetHits by remember { mutableIntStateOf(0) }
    var lastEventSummary by remember { mutableStateOf("Tap or swipe anywhere on the pad") }
    var padScreenOrigin by remember { mutableStateOf(Offset.Zero) }
    var padSize by remember { mutableStateOf(IntSize.Zero) }
    val recentDots = remember { mutableStateListOf<SandboxTouchDot>() }
    val hostView = LocalView.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ObsidianBg),
                title = {
                    Column {
                        Text("Touch Sandbox", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text("INPUT COLLISION & TRIGGER TEST PAD", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SlateSurface),
                    border = BorderStroke(1.dp, HairlineBorder),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Shizuku Dispatcher",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = if (shizukuStatus.state == ShizukuConnectionState.CONNECTED) "READY" else "OFFLINE",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (shizukuStatus.state == ShizukuConnectionState.CONNECTED) EmeraldRun else AmberLogic
                            )
                        }
                        Text(
                            text = lastEventSummary,
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(SlateWell)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                        .onGloballyPositioned { coords ->
                            val winLoc = IntArray(2)
                            hostView.getLocationOnScreen(winLoc)
                            val posInWin = coords.positionInWindow()
                            padScreenOrigin = Offset(
                                x = winLoc[0] + posInWin.x,
                                y = winLoc[1] + posInWin.y
                            )
                            padSize = coords.size
                        }
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    val pos = change.position
                                    val screenX = padScreenOrigin.x + pos.x
                                    val screenY = padScreenOrigin.y + pos.y
                                    if (event.type == PointerEventType.Press || event.type == PointerEventType.Move) {
                                        lastEventSummary = "Pointer ${event.type}: local(${pos.x.toInt()},${pos.y.toInt()}) screen(${screenX.toInt()},${screenY.toInt()})"
                                        recentDots.add(SandboxTouchDot(pos.x, pos.y))
                                        if (recentDots.size > 24) recentDots.removeAt(0)
                                    }
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = 72f
                        var gx = 0f
                        while (gx < size.width) {
                            drawLine(Color(0x1622D3EE), Offset(gx, 0f), Offset(gx, size.height), 1f)
                            gx += step
                        }
                        var gy = 0f
                        while (gy < size.height) {
                            drawLine(Color(0x1622D3EE), Offset(0f, gy), Offset(size.width, gy), 1f)
                            gy += step
                        }

                        recentDots.forEachIndexed { idx, dot ->
                            val alpha = ((idx + 1).toFloat() / recentDots.size.coerceAtLeast(1)).coerceIn(0.2f, 1f)
                            drawCircle(
                                color = ElectricCyan.copy(alpha = alpha),
                                radius = 16f,
                                center = Offset(dot.x, dot.y),
                                style = Stroke(width = 2.5f)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(136.dp)
                            .clip(CircleShape)
                            .background(if (targetHits % 2 == 0) EmeraldRun.copy(alpha = 0.22f) else AmberLogic.copy(alpha = 0.22f))
                            .border(
                                2.dp,
                                if (targetHits % 2 == 0) EmeraldRun else AmberLogic,
                                CircleShape
                            )
                            .clickable { targetHits++ }
                            .testTag("sandbox_target_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$targetHits",
                                style = MaterialTheme.typography.headlineLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = if (targetHits % 2 == 0) "#10B981" else "#F59E0B",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (targetHits % 2 == 0) EmeraldRun else AmberLogic
                            )
                            Text(
                                text = "TAP TARGET",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val cx = padScreenOrigin.x + padSize.width / 2f
                            val cy = padScreenOrigin.y + padSize.height / 2f
                            onTestTap(cx, cy)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color(0xFF04131A)),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sandbox_test_tap_btn")
                    ) {
                        Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Shizuku Tap", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            val sx = padScreenOrigin.x + padSize.width * 0.25f
                            val ex = padScreenOrigin.x + padSize.width * 0.75f
                            val sy = padScreenOrigin.y + padSize.height * 0.78f
                            onTestSwipe(sx, sy, ex, sy)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("sandbox_test_swipe_btn")
                    ) {
                        Icon(Icons.Default.Gesture, contentDescription = null, modifier = Modifier.size(18.dp), tint = AmberLogic)
                        Spacer(Modifier.width(6.dp))
                        Text("Shizuku Swipe", color = TextPrimary)
                    }
                }
            }
        }
    }
}
