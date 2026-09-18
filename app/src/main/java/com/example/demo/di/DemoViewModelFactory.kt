package com.example.demo.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.demo.ui.calendar.CalendarViewModel
import com.example.demo.ui.focus.FocusViewModel
import com.example.demo.ui.plan.PlanViewModel
import com.example.demo.ui.settings.SettingsViewModel
import com.example.demo.ui.stats.StatsViewModel
import com.example.demo.ui.todo.TodoViewModel

/**
 * 手动的 ViewModel 工厂，替代 Hilt 的 @Inject 构造器。
 *
 * ## 为什么需要 Factory
 * `ViewModel` 的默认创建方式是无参构造。但 [CalendarViewModel] 需要 [com.example.demo.data.repository.PeriodRepository]，
 * 而 Repository 住在 [AppContainer] 里——Android 框架不知道它的存在，没法替我们 new。
 * Factory 就是把这个「怎么 new」的知识交给框架的钩子：框架负责管 ViewModel 的生命周期
 * （配置变更/进程重建时复用实例），我们负责提供构造参数。
 *
 * 类比后端：框架（Spring）管 Bean 生命周期，但带参构造的 Bean 需要一个 @Bean 方法
 * 告诉它参数从哪来。Factory 就是那个 @Bean 方法。
 *
 * ## 为什么用 when(modelClass) 而不是反射
 * Hilt/Dagger 靠注解处理器在编译期生成注入代码；这里demo 规模只有几个 ViewModel，
 * 一个 when 分支表就够，且**新增 ViewModel 漏加分支会在运行时立刻抛异常**，不会静默出错。
 * 代价是每加一个 ViewModel 要来这里登记一行——可接受的显式成本。
 */
class DemoViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return when (modelClass) {
            CalendarViewModel::class.java -> CalendarViewModel(
                container.periodRepository,
                container.settingRepository,
                container.todoRepository,
                container.planRepository,
                container.pomodoroRepository,
            )
            TodoViewModel::class.java -> TodoViewModel(container.todoRepository)
            PlanViewModel::class.java -> PlanViewModel(
                container.planRepository,
                container.pomodoroRepository,
                container.settingRepository,
            )
            FocusViewModel::class.java -> FocusViewModel(
                container.pomodoroRepository,
                container.settingRepository,
                container.todoRepository,
            )
            StatsViewModel::class.java -> StatsViewModel(
                container.periodRepository,
                container.pomodoroRepository,
                container.todoRepository,
            )
            SettingsViewModel::class.java -> SettingsViewModel(container.settingRepository)
            else -> throw IllegalArgumentException("未登记的 ViewModel：${modelClass.name}")
        } as T
    }
}
