package com.example.demo

import android.app.Application
import android.util.Log
import com.example.demo.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Application 子类——进程级别的入口，整个应用生命周期只创建一次。
 * 类比后端：它就是 Spring Boot 的启动类 + ApplicationContext，
 * 只不过 Android 把这个容器的生命周期交给系统进程托管。
 *
 * 职责刻意保持最小：构建依赖容器 + 一次异步预热，别的都不做。
 */
class DemoApplication : Application() {

    /** 全局依赖容器，等价于 Spring 的 ApplicationContext */
    lateinit var container: AppContainer
        private set

    /**
     * 应用级协程作用域。
     *
     * - 生命周期与进程一致，所以**不需要**也**不应该**去 cancel 它；
     * - 用 SupervisorJob：预热任务失败不该连累同一作用域里的其他任务
     *   （普通 Job 是"一人犯错全组连坐"，SupervisorJob 是"各自独立"）；
     * - 用 Dispatchers.IO：建库、开文件都是磁盘 I/O。
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        warmUpDatabase()
    }

    /**
     * 异步预热数据库。
     *
     * ## 为什么需要这一步
     * `Room.databaseBuilder(...).build()` 只是**构造对象**，并不会碰磁盘；
     * AppContainer 里又套了一层 `by lazy`。所以如果没有任何代码真正查一次数据，
     * `/data/data/<pkg>/databases/moonlog.db` 这个文件根本不会被创建。
     * P0 的验收标准是"首次启动后数据库文件生成"，必须有东西去捅它一下。
     *
     * ## 为什么放在 IO 线程
     * Application.onCreate 是**串行阻塞**的——它不返回，Activity 就起不来。
     * 在这里同步建库会把耗时直接计入冷启动时间（首次建库要执行 5 张表的 CREATE TABLE，
     * 慢的设备上能有几十毫秒）。扔到 IO 线程后，UI 该多快还多快。
     *
     * ## 顺带的收益
     * `openHelper.writableDatabase` 会真正触发建表与 schema 校验。
     * 如果 Entity 定义与已存在的库结构不匹配（比如改了字段却忘了升 version），
     * 这里就会立刻抛异常并被日志抓住，而不是等到用户点到某个页面才崩——
     * 把故障暴露点前移，等价于后端的启动期自检。
     */
    private fun warmUpDatabase() {
        applicationScope.launch {
            try {
                container.database.openHelper.writableDatabase
                Log.i(TAG, "数据库预热完成")
            } catch (e: Exception) {
                // 只记录不抛出：预热失败不该让应用直接崩，后续真正用到时还会再暴露一次
                Log.e(TAG, "数据库预热失败", e)
            }
        }
    }

    private companion object {
        const val TAG = "DemoApplication"
    }
}
