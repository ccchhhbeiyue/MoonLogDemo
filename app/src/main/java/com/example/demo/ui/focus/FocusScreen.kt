package com.example.demo.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.DemoApplication
import com.example.demo.di.DemoViewModelFactory
import com.example.demo.domain.model.PomodoroLinkType
import com.example.demo.domain.model.TimerMode
import com.example.demo.service.PomodoroService
import com.example.demo.service.TimerPhase
import com.example.demo.ui.theme.LocalPeriodColors

/**
 * 专注页（P7 番茄钟）。
 *
 * ## 计时的真相在服务，不在这里
 * 本页只 collect [PomodoroService.uiState] 渲染圆盘与按钮，所有开始/暂停/放弃
 * 都通过 [PomodoroService] 的 companion 静态方法发 Intent 给前台服务。
 * 因此切 Tab、切后台、甚至 Activity 重建，倒计时都不受影响。
 *
 * ## 通知权限
 * Android 13+ 通知需运行时申请。点「开始专注」时若未授予 POST_NOTIFICATIONS
 * 就顺带发起申请；无论授予与否都启动服务（未授予只是通知栏不显示，计时照跑）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val factory = remember(context) {
        DemoViewModelFactory((context.applicationContext as DemoApplication).container)
    }
    val viewModel: FocusViewModel = viewModel(factory = factory)

    val timer by viewModel.timerState.collectAsStateWithLifecycle()
    val workMinutes by viewModel.workMinutes.collectAsStateWithLifecycle()
    val todayCount by viewModel.todayCount.collectAsStateWithLifecycle()
    val todayMinutes by viewModel.todayMinutes.collectAsStateWithLifecycle()
    val candidates by viewModel.linkCandidates.collectAsStateWithLifecycle()
    val selectedLink by viewModel.selectedLink.collectAsStateWithLifecycle()

    var pickingLink by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 授予与否都继续：未授予仅通知栏不显示，计时不受影响 */ }

    fun beginFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        PomodoroService.start(
            context = context,
            plannedMinutes = workMinutes,
            link = selectedLink?.type ?: PomodoroLinkType.NONE,
            linkId = selectedLink?.id,
        )
    }

    // IDLE 时圆盘显示「即将开始的完整时长」，而非 00:00 的空盘
    val idleMillis = workMinutes * 60_000L
    val dialRemaining = if (timer.phase == TimerPhase.IDLE) idleMillis else timer.remainingMillis
    val dialTotal = if (timer.phase == TimerPhase.IDLE) idleMillis else timer.totalMillis

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("专注") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PomodoroDial(
                remainingMillis = dialRemaining,
                totalMillis = dialTotal,
                phase = timer.phase,
                mode = timer.mode,
                elapsedMillis = timer.elapsedMillis,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = when (timer.phase) {
                    TimerPhase.IDLE -> "准备开始 · $workMinutes 分钟"
                    TimerPhase.RUNNING -> if (timer.mode == TimerMode.COUNTUP) "计时中" else "专注中"
                    TimerPhase.PAUSED -> "已暂停"
                    TimerPhase.COMPLETED -> "已完成 · 记录 ${timer.completedMinutes} 分钟"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "关联：${selectedLink?.label ?: "无"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { pickingLink = true },
                    enabled = timer.phase == TimerPhase.IDLE || timer.phase == TimerPhase.COMPLETED,
                ) {
                    Text("选择")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            // 结束语义三分：正计时「停止」（落库已走时长）；倒计时/番茄「完成」（提前确认完成）
            // 与「放弃」（记未完成）；完成态提供「再来一轮 / 关闭」。
            val isCountUp = timer.mode == TimerMode.COUNTUP
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (timer.phase) {
                    TimerPhase.IDLE -> Button(onClick = { beginFocus() }) { Text("开始专注") }
                    TimerPhase.RUNNING -> {
                        OutlinedButton(onClick = { PomodoroService.pause(context) }) { Text("暂停") }
                        if (isCountUp) {
                            Button(onClick = { PomodoroService.stop(context) }) { Text("停止") }
                        } else {
                            Button(onClick = { PomodoroService.complete(context) }) { Text("完成") }
                            OutlinedButton(onClick = { PomodoroService.abandon(context) }) { Text("放弃") }
                        }
                    }
                    TimerPhase.PAUSED -> {
                        Button(onClick = { PomodoroService.resume(context) }) { Text("继续") }
                        if (isCountUp) {
                            OutlinedButton(onClick = { PomodoroService.stop(context) }) { Text("停止") }
                        } else {
                            Button(onClick = { PomodoroService.complete(context) }) { Text("完成") }
                            OutlinedButton(onClick = { PomodoroService.abandon(context) }) { Text("放弃") }
                        }
                    }
                    TimerPhase.COMPLETED -> {
                        Button(onClick = { beginFocus() }) { Text("再来一轮") }
                        OutlinedButton(onClick = { PomodoroService.dismiss(context) }) { Text("关闭") }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "今日 $todayCount 个番茄 · 共 $todayMinutes 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalPeriodColors.current.periodConfirmed,
            )
        }
    }

    if (pickingLink) {
        AlertDialog(
            onDismissRequest = { pickingLink = false },
            title = { Text("关联到任务") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            viewModel.selectLink(null)
                            pickingLink = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("无关联", modifier = Modifier.fillMaxWidth()) }
                    candidates.forEach { candidate ->
                        TextButton(
                            onClick = {
                                viewModel.selectLink(candidate)
                                pickingLink = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(candidate.label, modifier = Modifier.fillMaxWidth()) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingLink = false }) { Text("关闭") } },
        )
    }
}
