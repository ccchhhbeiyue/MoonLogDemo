package com.example.demo.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.demo.DemoApplication
import com.example.demo.data.repository.SettingRepository
import com.example.demo.di.DemoViewModelFactory
import com.example.demo.ui.theme.IconArrowBack

/**
 * 设置页（P8 二级页面）。
 *
 * 每个控件都是「点一下 → ViewModel → Repository 读-改-写 → Flow 重发 → UI 刷新」的闭环，
 * 没有「保存」按钮：单行配置表的每次改动即是一次完整提交（类比后端的 PATCH 接口）。
 * 末尾的免责声明与隐私说明是方案 §4.5 的合规要求，不可省略。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val factory = remember(context) {
        DemoViewModelFactory((context.applicationContext as DemoApplication).container)
    }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val setting by viewModel.setting.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(IconArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Section(title = "周期默认值（无记录时用于冷启动预测）") {
                StepperRow(
                    label = "默认周期长度",
                    value = setting.defaultCycleLength,
                    unit = "天",
                    range = SettingRepository.MIN_CYCLE..SettingRepository.MAX_CYCLE,
                    onValue = viewModel::setCycleLength,
                )
                StepperRow(
                    label = "默认经期长度",
                    value = setting.defaultPeriodLength,
                    unit = "天",
                    range = SettingRepository.MIN_PERIOD..SettingRepository.MAX_PERIOD,
                    onValue = viewModel::setPeriodLength,
                )
            }

            Section(title = "日历显示") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "显示易孕窗口与排卵日",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = setting.showFertileWindow,
                        onCheckedChange = viewModel::setShowFertileWindow,
                    )
                }
                Text(
                    text = "默认关闭：生育力信息不主动展示。开启后日历才会画易孕窗（浅紫带）与排卵日（浅黄圆）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section(title = "番茄钟") {
                StepperRow(
                    label = "专注时长",
                    value = setting.pomodoroWorkMin,
                    unit = "分钟",
                    range = SettingRepository.MIN_WORK..SettingRepository.MAX_WORK,
                    onValue = viewModel::setWorkMinutes,
                )
                StepperRow(
                    label = "短休息时长",
                    value = setting.pomodoroShortBreakMin,
                    unit = "分钟",
                    range = SettingRepository.MIN_SHORT_BREAK..SettingRepository.MAX_SHORT_BREAK,
                    onValue = viewModel::setShortBreak,
                )
                StepperRow(
                    label = "长休息时长",
                    value = setting.pomodoroLongBreakMin,
                    unit = "分钟",
                    range = SettingRepository.MIN_LONG_BREAK..SettingRepository.MAX_LONG_BREAK,
                    onValue = viewModel::setLongBreak,
                )
                StepperRow(
                    label = "每几个番茄后长休息",
                    value = setting.pomodoroRoundsBeforeLongBreak,
                    unit = "个",
                    range = SettingRepository.MIN_ROUNDS..SettingRepository.MAX_ROUNDS,
                    onValue = viewModel::setRoundsBeforeLongBreak,
                )
            }

            Section(title = "通知") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "番茄结束时发出提醒",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = setting.notificationEnabled,
                        onCheckedChange = viewModel::setNotificationEnabled,
                    )
                }
            }

            Section(title = "免责声明与隐私") {
                Text(
                    text = "预测结果仅供参考，不构成医疗建议。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "本应用的所有数据（经期记录、待办、工作日志、番茄记录）仅存储在您的设备本地，" +
                        "不联网、不上传、不需要账号；卸载应用即清除全部数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 设置分组：一个标题 + 若干设置行 */
@Composable
private fun Section(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

/**
 * 「− 值 +」步进行。到边界时对应按钮禁用，保证提交值永远落在 Repository 的 require 区间内。
 * 用步进器而非输入框：配置项都是小整数范围，点按比键盘输入快且不会输错格式。
 */
@Composable
private fun StepperRow(
    label: String,
    value: Int,
    unit: String,
    range: IntRange,
    onValue: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onValue(value - 1) }, enabled = value > range.first) { Text("−") }
        Text(
            text = "$value $unit",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 72.dp),
        )
        TextButton(onClick = { onValue(value + 1) }, enabled = value < range.last) { Text("+") }
    }
}
