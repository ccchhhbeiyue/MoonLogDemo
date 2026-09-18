package com.example.demo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.demo.DemoApplication
import com.example.demo.MainActivity
import com.example.demo.R
import com.example.demo.domain.model.PomodoroLinkType
import com.example.demo.domain.model.TimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 计时阶段。IDLE=未在计时；RUNNING=倒计时中；PAUSED=暂停（保留剩余时长）；COMPLETED=已完成（已落库，待用户关闭或再来一轮）。 */
enum class TimerPhase { IDLE, RUNNING, PAUSED, COMPLETED }

/**
 * 番茄钟对外暴露的 UI 状态。
 *
 * [remainingMillis] / [totalMillis] 供专注页画圆盘与 mm:ss；
 * [linkType] / [linkId] 记录本次番茄关联的任务，落库时原样写入 pomodoro_session。
 */
data class TimerUiState(
    val phase: TimerPhase = TimerPhase.IDLE,
    val remainingMillis: Long = 0L,
    val totalMillis: Long = 0L,
    val plannedMinutes: Int = 0,
    val linkType: PomodoroLinkType = PomodoroLinkType.NONE,
    val linkId: Long? = null,
    val mode: TimerMode = TimerMode.POMODORO,
    /** 已走过的时长；倒计时=total-remaining，正计时=从 0 累加。专注页按 mode 选用 */
    val elapsedMillis: Long = 0L,
    /** 完成态专属：本次落库的实际分钟数，专注页完成横幅展示用 */
    val completedMinutes: Int = 0,
)

/**
 * 番茄钟前台服务（P7 核心）。
 *
 * ## 为什么计时用「结束时间戳 − 当前时间」而不是每秒累加
 * 累加式（CountDownTimer / Handler.postDelayed 每次 -1s）在进程被冻结
 * （切后台、Doze、锁屏）时回调停摆，回来时间就凭空少了。
 * 时间戳式只存一个 [endTimeMillis]，每次刷新用 `endTime - now` 现算，
 * 即使 UI 被冻结十分钟，恢复后一算照样准确。这与后端定时任务
 * 「比对时间戳而非 sleep 累加」是同一个道理。
 *
 * ## 为什么还要前台服务
 * 光有时间戳只能保证「算得准」，不能保证「进程活着、用户看得到」。
 * startForeground 让系统尽量不杀进程，并在通知栏常驻倒计时；
 * 结束时由系统 NotificationManager 播提示音（后台自播音频会被静默拦截）。
 *
 * ## 与 UI 的通信
 * 不走 bind/IBinder，而是 companion 里一个进程内单例 [uiState] StateFlow：
 * 服务每 tick 推进它，专注页 collect 它。demo 单进程足够，省去绑定的样板。
 */
class PomodoroService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 计时三要素：结束时间戳（RUNNING 时有效）+ 暂停时冻结的剩余时长 + 计划时长
    private var endTimeMillis = 0L
    private var pausedRemainingMillis = 0L
    private var plannedMinutes = DEFAULT_PLANNED_MINUTES
    private var startTimeMillis = 0L
    private var linkType = PomodoroLinkType.NONE
    private var linkId: Long? = null
    private var mode = TimerMode.POMODORO
    // 正计时专用：暂停时已累计的时长 + 当前运行段起点（elapsed = accum + now - segmentStart）
    private var countUpAccumMillis = 0L
    private var segmentStartMillis = 0L

    private var ticking = false
    private var lastNotifiedSecond = -1

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    override fun onCreate() {
        super.onCreate()
        createChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> handlePause()
            ACTION_RESUME -> handleResume()
            ACTION_ABANDON -> handleAbandon()
            ACTION_STOP -> handleStop()
            ACTION_COMPLETE -> handleComplete()
            ACTION_DISMISS -> handleDismiss()
        }
        // 被系统杀后不自动重建：番茄计时中断后重建一个「不知道结束时间」的服务没有意义。
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ticking = false
        scope.cancel()
        super.onDestroy()
    }

    // ===== 动作处理 =====

    private fun handleStart(intent: Intent) {
        mode = TimerMode.fromCode(intent.getIntExtra(EXTRA_MODE, TimerMode.POMODORO.code))
        // COUNTUP 无目标调用方传 0；COUNTDOWN/POMODORO 传目标分钟（POMODORO 由调用方读设置页）
        plannedMinutes = intent.getIntExtra(EXTRA_PLANNED_MINUTES, DEFAULT_PLANNED_MINUTES)
        linkType = PomodoroLinkType.fromCode(intent.getIntExtra(EXTRA_LINK_TYPE, PomodoroLinkType.NONE.code))
        linkId = if (linkType == PomodoroLinkType.NONE) {
            null
        } else {
            intent.getLongExtra(EXTRA_LINK_ID, -1L).takeIf { it >= 0 }
        }
        startTimeMillis = System.currentTimeMillis()
        pausedRemainingMillis = 0L
        countUpAccumMillis = 0L

        if (mode == TimerMode.COUNTUP) {
            // 正计时没有结束时间戳：记当前段起点，elapsed 现算
            endTimeMillis = 0L
            segmentStartMillis = startTimeMillis
            enterForeground(buildTimerNotification(0L))
            publish(phase = TimerPhase.RUNNING, remaining = 0L, elapsed = 0L)
        } else {
            endTimeMillis = startTimeMillis + plannedMinutes * MILLIS_PER_MINUTE
            segmentStartMillis = 0L
            enterForeground(buildTimerNotification(plannedMinutes * MILLIS_PER_MINUTE))
            publish(phase = TimerPhase.RUNNING, remaining = plannedMinutes * MILLIS_PER_MINUTE)
        }
        beginTicking()
    }

    private fun handlePause() {
        if (_uiState.value.phase != TimerPhase.RUNNING) return
        ticking = false
        if (mode == TimerMode.COUNTUP) {
            countUpAccumMillis += System.currentTimeMillis() - segmentStartMillis
            publish(phase = TimerPhase.PAUSED, remaining = 0L, elapsed = countUpAccumMillis)
            notificationManager.notify(NOTIF_ID, buildTimerNotification(countUpAccumMillis, paused = true))
        } else {
            pausedRemainingMillis = currentRemaining()
            publish(phase = TimerPhase.PAUSED, remaining = pausedRemainingMillis)
            notificationManager.notify(NOTIF_ID, buildTimerNotification(pausedRemainingMillis, paused = true))
        }
    }

    private fun handleResume() {
        if (_uiState.value.phase != TimerPhase.PAUSED) return
        if (mode == TimerMode.COUNTUP) {
            segmentStartMillis = System.currentTimeMillis()
            publish(phase = TimerPhase.RUNNING, remaining = 0L, elapsed = countUpAccumMillis)
        } else {
            endTimeMillis = System.currentTimeMillis() + pausedRemainingMillis
            publish(phase = TimerPhase.RUNNING, remaining = pausedRemainingMillis)
        }
        beginTicking()
    }

    private fun handleAbandon() {
        // 无活跃计时时不能落库：abandon 走 startService，若服务本不在运行，
        // 系统会新建一个字段全 0 的实例并立刻 finish，写出 start_time=0 的脏行。
        // 与 handlePause/handleResume 一样先按 phase 守卫，空服务直接停掉。
        if (_uiState.value.phase == TimerPhase.IDLE) {
            stopSelf()
            return
        }
        if (mode == TimerMode.COUNTUP) {
            // 正计时「放弃」= 丢弃本次不落库：没有目标，也就没有「未完成」的意义
            resetAndStop()
            return
        }
        // 中途放弃：记一条未完成的番茄，actual = 已走过的整分钟数。
        finish(completed = false)
    }

    /** 正计时手动停止：记一条完成会话（actual = 已走过的整分钟数）。 */
    private fun handleStop() {
        if (_uiState.value.phase == TimerPhase.IDLE) {
            stopSelf()
            return
        }
        if (mode != TimerMode.COUNTUP) {
            // 停止语义只对正计时成立；其它模式退化为放弃
            handleAbandon()
            return
        }
        finish(completed = true)
    }

    /**
     * 手动「完成」：不等倒计时走完，立即按已完成落库（actual = 已走过的整分钟），
     * 界面直接进入完成态。与正计时 stop 的区别：它对三种模式都成立。
     */
    private fun handleComplete() {
        val phase = _uiState.value.phase
        // 只有进行中/暂停中能完成；IDLE 空服务直接停，COMPLETED 重复点击忽略
        if (phase != TimerPhase.RUNNING && phase != TimerPhase.PAUSED) {
            stopSelf()
            return
        }
        finish(completed = true, manualComplete = true)
    }

    /** 关闭完成态：落库在完成时已完成，这里只把状态清回 IDLE，不再写库 */
    private fun handleDismiss() {
        when (_uiState.value.phase) {
            TimerPhase.COMPLETED -> resetAndStop()
            TimerPhase.IDLE -> stopSelf()
            // 计时中的误发 intent：忽略，绝不能把进行中的计时清掉
            else -> Unit
        }
    }

    // ===== 计时循环 =====

    private fun beginTicking() {
        if (ticking) return
        ticking = true
        scope.launch {
            while (ticking) {
                delay(TICK_MILLIS)
                tick()
            }
        }
    }

    private fun tick() {
        // 守卫：循环在 delay 返回后会无条件跑一次 tick，而收尾可能恰在其间
        // 把状态置回 IDLE/COMPLETED；不加守卫这发「迟到的 tick」会 publish(RUNNING) 覆盖收尾状态，
        // 表现为结束后界面仍停在计时中。
        if (!ticking) return
        if (mode == TimerMode.COUNTUP) {
            val elapsed = currentElapsed()
            publish(phase = TimerPhase.RUNNING, remaining = 0L, elapsed = elapsed)
            notifyCountdownThrottled(elapsed)
            return // 正计时不自动结束，等用户手动 stop
        }
        val remaining = currentRemaining()
        publish(phase = TimerPhase.RUNNING, remaining = remaining)
        notifyCountdownThrottled(remaining)
        if (remaining <= 0L) finish(completed = true)
    }

    /** 时间戳式剩余时长：永远以系统时钟为准，冻结恢复后自动校正。 */
    private fun currentRemaining(): Long =
        (endTimeMillis - System.currentTimeMillis()).coerceAtLeast(0L)

    /** 正计时已走时长：运行中=已累计+当前段；暂停时=已累计（段起点已冻结） */
    private fun currentElapsed(): Long =
        if (ticking) countUpAccumMillis + (System.currentTimeMillis() - segmentStartMillis)
        else countUpAccumMillis

    // ===== 结束与落库 =====

    /**
     * 结束并落库。
     * @param completed 是否记为已完成（自然走完 / 正计时停止 / 手动完成）。
     * @param manualComplete 手动「完成」：倒计时没走完就确认完成，
     *   actual 按已走过的整分钟算（与放弃同口径），而不是自然走完的 planned。
     */
    private fun finish(completed: Boolean, manualComplete: Boolean = false) {
        // 先取已走时长再停 ticking：currentElapsed 依赖 ticking 判断是否加上当前运行段，
        // 先置 false 会把运行中的那一段凭空丢掉（正计时停止少算分钟数的根因）。
        val elapsedAtFinish = if (mode == TimerMode.COUNTUP) {
            currentElapsed()
        } else {
            (System.currentTimeMillis() - startTimeMillis).coerceAtMost(plannedMinutes * MILLIS_PER_MINUTE)
        }
        ticking = false
        val actualMinutes: Int
        val plannedForRecord: Int
        if (mode == TimerMode.COUNTUP) {
            // 正计时无目标：planned 记 0，actual = 已走过的整分钟
            actualMinutes = (elapsedAtFinish / MILLIS_PER_MINUTE).toInt()
            plannedForRecord = 0
        } else if (completed && !manualComplete) {
            actualMinutes = plannedMinutes
            plannedForRecord = plannedMinutes
        } else {
            actualMinutes = (elapsedAtFinish / MILLIS_PER_MINUTE).toInt().coerceIn(0, plannedMinutes)
            plannedForRecord = plannedMinutes
        }
        // 落库走 Repository.recordSession：workDate 一致性、link 配对、模式不变量都由它守。
        // 用 companion 级 recordScope：实例 scope 会随 stopSelf→onDestroy 被 cancel，
        // 落库协程挂在其上有可能写到一半被取消（旧代码靠运气赢竞态）。
        recordScope.launch {
            val container = (application as DemoApplication).container
            container.pomodoroRepository.recordSession(
                startTime = startTimeMillis,
                plannedMinutes = plannedForRecord,
                actualMinutes = actualMinutes,
                isCompleted = completed,
                link = linkType,
                linkId = linkId,
                mode = mode,
            )
            // 完成提醒遵循设置页的通知开关（P8）：落库后读最新配置，
            // 关掉开关对正在进行中的这个番茄也立即生效。
            if (completed && container.settingRepository.get().notificationEnabled) {
                postDoneNotification(actualMinutes)
            }
        }

        if (completed) {
            // 完成态在专注页停留展示（满环 + 「完成」），状态住在 companion 不随服务停止丢失；
            // 前台通知与服务的收尾照做，「再来一轮 / 关闭」会再发 intent 唤醒服务收拾状态。
            publish(
                phase = TimerPhase.COMPLETED,
                remaining = 0L,
                elapsed = elapsedAtFinish,
                completedMinutes = actualMinutes,
            )
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            resetAndStop()
        }
    }

    /** 不落库直接停：正计时放弃与结束收尾共用 */
    private fun resetAndStop() {
        ticking = false
        _uiState.value = TimerUiState()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ===== 通知 =====

    private fun createChannels() {
        val timer = NotificationChannel(
            CHANNEL_TIMER,
            "专注计时",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        val done = NotificationChannel(
            CHANNEL_DONE,
            "专注完成提醒",
            NotificationManager.IMPORTANCE_HIGH,
        )
        notificationManager.createNotificationChannel(timer)
        notificationManager.createNotificationChannel(done)
    }

    private fun enterForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 必须显式传 foregroundServiceType，且与 Manifest 声明一致，否则 SecurityException。
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    /** 常驻倒计时通知。IMPORTANCE_LOW：不发声不弹头，只在通知栏安静地走秒。 */
    private fun buildTimerNotification(remaining: Long, paused: Boolean = false): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (paused) {
            "已暂停"
        } else {
            when (mode) {
                TimerMode.COUNTUP -> "计时中"
                TimerMode.COUNTDOWN -> "倒计时中"
                TimerMode.POMODORO -> "专注中"
            }
        }
        // 正计时显示「+已走时长」，倒计时/番茄显示「剩余时长」
        val text = if (mode == TimerMode.COUNTUP) "+${formatClock(remaining)}" else formatClock(remaining)
        return NotificationCompat.Builder(this, CHANNEL_TIMER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent)
            .build()
    }

    /** 整秒变化才刷新通知，避免每 250ms tick 都打一次 NotificationManager。 */
    private fun notifyCountdownThrottled(remaining: Long) {
        val second = (remaining / 1000L).toInt()
        if (second == lastNotifiedSecond) return
        lastNotifiedSecond = second
        notificationManager.notify(NOTIF_ID, buildTimerNotification(remaining))
    }

    /**
     * 结束通知。提示音挂在 Notification 上由系统播放：
     * targetSdk 37 下后台自播 AudioTrack/MediaPlayer 会被静默拦截，此路不通。
     */
    private fun postDoneNotification(actualMinutes: Int) {
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("专注完成")
            .setContentText(doneMessage(actualMinutes))
            .setAutoCancel(true)
            .setSound(sound)
            .setContentIntent(contentIntent)
            .build()
        notificationManager.notify(DONE_NOTIF_ID, notification)
    }

    /** 完成通知文案按模式区分，避免把正计时说成「番茄」；手动完成时 actual 可能小于 planned，故统一报实际分钟 */
    private fun doneMessage(actualMinutes: Int): String = when (mode) {
        TimerMode.COUNTUP -> "本次计时 $actualMinutes 分钟已记录。"
        TimerMode.COUNTDOWN -> "本次倒计时 $actualMinutes 分钟已记录。"
        TimerMode.POMODORO -> "本次番茄 $actualMinutes 分钟已记录。"
    }

    // ===== 状态发布 =====

    private fun publish(
        phase: TimerPhase,
        remaining: Long,
        elapsed: Long = (plannedMinutes * MILLIS_PER_MINUTE) - remaining,
        completedMinutes: Int = 0,
    ) {
        _uiState.value = TimerUiState(
            phase = phase,
            remainingMillis = remaining,
            // 正计时没有目标总长，total 记 0，专注页据此不画满环
            totalMillis = if (mode == TimerMode.COUNTUP) 0L else plannedMinutes * MILLIS_PER_MINUTE,
            plannedMinutes = plannedMinutes,
            linkType = linkType,
            linkId = linkId,
            mode = mode,
            elapsedMillis = elapsed,
            completedMinutes = completedMinutes,
        )
    }

    companion object {
        private const val ACTION_START = "com.example.demo.action.POMO_START"
        private const val ACTION_PAUSE = "com.example.demo.action.POMO_PAUSE"
        private const val ACTION_RESUME = "com.example.demo.action.POMO_RESUME"
        private const val ACTION_ABANDON = "com.example.demo.action.POMO_ABANDON"
        private const val ACTION_STOP = "com.example.demo.action.POMO_STOP"
        private const val ACTION_COMPLETE = "com.example.demo.action.POMO_COMPLETE"
        private const val ACTION_DISMISS = "com.example.demo.action.POMO_DISMISS"

        private const val EXTRA_PLANNED_MINUTES = "extra_planned_minutes"
        private const val EXTRA_MODE = "extra_mode"
        private const val EXTRA_LINK_TYPE = "extra_link_type"
        private const val EXTRA_LINK_ID = "extra_link_id"

        private const val CHANNEL_TIMER = "pomodoro_timer"
        private const val CHANNEL_DONE = "pomodoro_done"
        private const val NOTIF_ID = 1001
        private const val DONE_NOTIF_ID = 1002

        private const val TICK_MILLIS = 250L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val DEFAULT_PLANNED_MINUTES = 25

        private val _uiState = MutableStateFlow(TimerUiState())

        /** 进程内单例计时状态，专注页 collect 它渲染圆盘与按钮。 */
        val uiState: StateFlow<TimerUiState> = _uiState.asStateFlow()

        /**
         * 落库专用 scope（companion 级 = 进程级）。
         * 服务实例 scope 在 onDestroy 里被 cancel，而 finish 落库后紧接着 stopSelf，
         * 协程若挂在实例 scope 上可能被取消在半路；进程级 scope 保证写完。
         */
        private val recordScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun start(
            context: Context,
            plannedMinutes: Int,
            link: PomodoroLinkType,
            linkId: Long?,
            mode: TimerMode = TimerMode.POMODORO,
        ) {
            val intent = Intent(context, PomodoroService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PLANNED_MINUTES, plannedMinutes)
                putExtra(EXTRA_MODE, mode.code)
                putExtra(EXTRA_LINK_TYPE, link.code)
                if (linkId != null) putExtra(EXTRA_LINK_ID, linkId)
            }
            context.startForegroundService(intent)
        }

        fun pause(context: Context) = send(context, ACTION_PAUSE)
        fun resume(context: Context) = send(context, ACTION_RESUME)
        fun abandon(context: Context) = send(context, ACTION_ABANDON)

        /** 正计时手动停止 */
        fun stop(context: Context) = send(context, ACTION_STOP)

        /** 手动完成：立即按已完成落库并进入完成态 */
        fun complete(context: Context) = send(context, ACTION_COMPLETE)

        /** 关闭完成态回 IDLE（不写库） */
        fun dismiss(context: Context) = send(context, ACTION_DISMISS)

        private fun send(context: Context, action: String) {
            context.startService(Intent(context, PomodoroService::class.java).setAction(action))
        }

        /** mm:ss 展示。 */
        fun formatClock(millis: Long): String {
            val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }
    }
}
