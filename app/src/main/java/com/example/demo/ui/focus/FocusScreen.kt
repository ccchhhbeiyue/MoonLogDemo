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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
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
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val customMinutes by viewModel.customMinutes.collectAsStateWithLifecycle()

    var pickingLink by remember { mutableStateOf(false) }
    var showingMinutesInput by remember { mutableStateOf(false) }

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
        // 计划时长按模式取：番茄钟=设置页工作时长；倒计时=自选分钟；正计时不限时传 0
        val plannedMinutes = when (selectedMode) {
            TimerMode.POMODORO -> workMinutes
            TimerMode.COUNTDOWN -> customMinutes
            TimerMode.COUNTUP -> 0
        }
        PomodoroService.start(
            context = context,
            plannedMinutes = plannedMinutes,
            mode = selectedMode,
            link = selectedLink?.type ?: PomodoroLinkType.NONE,
            linkId = selectedLink?.id,
        )
    }

    // IDLE 时圆盘显示「即将开始的完整时长」，而非 00:00 的空盘；正计时无目标时长，空盘即可
    val idleMinutes = when (selectedMode) {
        TimerMode.POMODORO -> workMinutes
        TimerMode.COUNTDOWN -> customMinutes
        TimerMode.COUNTUP -> 0
    }
    val idleMillis = idleMinutes * 60_000L
    val dialRemaining = if (timer.phase == TimerPhase.IDLE) idleMillis else timer.remainingMillis
    val dialTotal = if (timer.phase == TimerPhase.IDLE) idleMillis else timer.totalMillis

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("专注") }) },
    ) { innerPadding ->
        Column(
            // 不套 verticalScroll：整页内容已收进一屏，套了反而和手势/自动滚动打架（实机反馈仍能上下滑）
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PomodoroDial(
                remainingMillis = dialRemaining,
                totalMillis = dialTotal,
                phase = timer.phase,
                mode = timer.mode,
                elapsedMillis = timer.elapsedMillis,
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (timer.phase) {
                    TimerPhase.IDLE -> when (selectedMode) {
                        TimerMode.POMODORO -> "准备开始 · 番茄钟 $workMinutes 分钟"
                        TimerMode.COUNTDOWN -> "准备开始 · 倒计时 $customMinutes 分钟"
                        TimerMode.COUNTUP -> "准备开始 · 正计时"
                    }
                    TimerPhase.RUNNING -> if (timer.mode == TimerMode.COUNTUP) "计时中" else "专注中"
                    TimerPhase.PAUSED -> "已暂停"
                    TimerPhase.COMPLETED -> "已完成 · 记录 ${timer.completedMinutes} 分钟"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // 模式与时长选择：只在 IDLE/COMPLETED 可改（计时中锁定，同关联选择的 enabled 语义）
            if (timer.phase == TimerPhase.IDLE || timer.phase == TimerPhase.COMPLETED) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { viewModel.selectMode(mode) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                if (selectedMode == TimerMode.COUNTDOWN) {
                    Spacer(modifier = Modifier.height(4.dp))
                    // 自定义时长：点中间分钟数弹数字输入（任意分钟），两侧 ±5 微调，下方预设一键直达
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(onClick = { viewModel.adjustCustomMinutes(-5) }) { Text("-5") }
                        Button(onClick = { showingMinutesInput = true }) { Text("$customMinutes 分钟") }
                        OutlinedButton(onClick = { viewModel.adjustCustomMinutes(5) }) { Text("+5") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CUSTOM_PRESETS.forEach { preset ->
                            FilterChip(
                                selected = customMinutes == preset,
                                onClick = { viewModel.setCustomMinutes(preset) },
                                label = { Text("$preset") },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
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

            Spacer(modifier = Modifier.height(4.dp))
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

            Spacer(modifier = Modifier.height(4.dp))
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

    // 滚轮中心点击 → 手动输入任意分钟（覆盖滚轮 120 上限之外的时长）
    if (showingMinutesInput) {
        var input by remember { mutableStateOf(customMinutes.toString()) }
        AlertDialog(
            onDismissRequest = { showingMinutesInput = false },
            title = { Text("输入倒计时分钟") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { text -> input = text.filter { it.isDigit() }.take(3) },
                    label = { Text("1-240 分钟") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    input.toIntOrNull()?.let { viewModel.setCustomMinutes(it) }
                    showingMinutesInput = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showingMinutesInput = false }) { Text("取消") }
            },
        )
    }
}

/** 自定义倒计时的常用预设（分钟），一键选中 */
private val CUSTOM_PRESETS = listOf(15, 25, 45, 60, 90)
