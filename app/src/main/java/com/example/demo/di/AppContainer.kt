package com.example.demo.di

import android.content.Context
import com.example.demo.data.local.AppDatabase
import com.example.demo.data.repository.PeriodRepository
import com.example.demo.data.repository.PlanRepository
import com.example.demo.data.repository.PomodoroRepository
import com.example.demo.data.repository.SettingRepository
import com.example.demo.data.repository.TodoRepository
import com.example.demo.data.repository.WorkRepository

/**
 * 手写的依赖容器，替代 Hilt/Dagger。
 *
 * 思路与 Spring 的 IoC 容器一致：把"谁依赖谁、什么时候创建"集中到一处管理，
 * 业务代码不自己 new 依赖。区别是这里没有反射、没有注解处理器，
 * 全靠 Kotlin 的 `by lazy` 做懒加载单例——对 demo 规模来说足够，且零学习成本。
 *
 * 获取方式：`(context.applicationContext as DemoApplication).container`
 * ViewModel 里通过 ViewModelProvider.Factory 注入。
 */
class AppContainer(context: Context) {

    /** 数据库整体懒加载：不在 Application.onCreate 里同步建库，避免拖慢冷启动 */
    val database: AppDatabase by lazy { AppDatabase.getInstance(context) }

    // Repository 同样全部懒加载，且构造器参数只传 DAO。
    // lazy 是链式的：Repository 懒 → 它引用的 database 也懒 →
    // 整个对象图在第一次真正被访问前一个对象都不会创建，冷启动零成本。
    // 类比 Spring 的 @Lazy Bean：声明时不实例化，第一次注入/获取时才建。

    val periodRepository: PeriodRepository by lazy { PeriodRepository(database.periodRecordDao()) }

    // TodoRepository / WorkRepository 都要额外收一个 pomodoroSessionDao：
    // 它们的 delete 是跨表事务（删本体 + 解除番茄引用），见各自类注释。
    val todoRepository: TodoRepository by lazy {
        TodoRepository(database.todoDao(), database.pomodoroSessionDao())
    }

    val workRepository: WorkRepository by lazy {
        WorkRepository(database.workLogDao(), database.pomodoroSessionDao())
    }

    // PlanRepository 的 delete 也是跨表事务（删计划 + 解除番茄引用），同 Todo/Work
    val planRepository: PlanRepository by lazy {
        PlanRepository(database.planDao(), database.pomodoroSessionDao())
    }

    val pomodoroRepository: PomodoroRepository by lazy { PomodoroRepository(database.pomodoroSessionDao()) }

    val settingRepository: SettingRepository by lazy { SettingRepository(database.userSettingDao()) }
}
