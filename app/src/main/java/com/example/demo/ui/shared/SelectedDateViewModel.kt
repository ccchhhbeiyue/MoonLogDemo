package com.example.demo.ui.shared

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * 跨 Tab 共享的「当前选中日期」。
 *
 * ## 为什么单独一个 ViewModel，而不塞进 CalendarViewModel
 * 计划里四个模块的连接点是「选中的那一天」：日历选中 9/16，切到待办 Tab 要看到 9/16 的待办，
 * 切到工作 Tab 要看到 9/16 的日志。若选中日期住在 CalendarViewModel 里，别的 Tab 拿不到它——
 * 因为每个 Tab 是独立的 NavBackStackEntry，各自持有各自的 ViewModelStore（navigation-scoped），
 * CalendarViewModel 只在日历页存活。
 *
 * ## 怎么做到共享：把作用域提升到 Activity
 * 获取它时传 viewModelStoreOwner = Activity，四个 Tab 用同一个 owner 去要，拿到同一个实例，
 * 选中日期天然共享。类比后端：navigation-scoped 像每个请求各自的 ThreadLocal，
 * activity-scoped 像整个会话共享的 HttpSession / Redis。
 *
 * ## 为什么它不需要 Factory
 * 无参构造。ViewModel 框架默认就能 new 无参的，不需要我们提供构造参数，
 * 所以不必登记进 DemoViewModelFactory（Factory 只为带依赖的 ViewModel 服务）。
 */
class SelectedDateViewModel : ViewModel() {

    // MutableStateFlow 私有，只暴露只读的 StateFlow——
    // 防止 UI 层绕过 selectDate 直接改值，保证「唯一写入口」（与 Repository 收口写入同理）。
    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }
}
