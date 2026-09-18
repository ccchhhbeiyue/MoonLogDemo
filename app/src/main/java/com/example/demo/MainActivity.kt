package com.example.demo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.demo.navigation.DemoApp
import com.example.demo.ui.theme.DemoTheme

/**
 * 全应用唯一的 Activity。
 *
 * Compose 项目的惯例是"单 Activity + 内部路由"：页面切换不再依赖 Android 的 Activity 栈，
 * 而是走 Navigation 组件的回退栈。对照后端，Activity 类似一个常驻的 Servlet 容器，
 * 里面的各个 Screen 才是被路由分发的 handler。
 *
 * 这样做的好处：页面间共享状态（如"当前选中的日期"）可以直接放在同一个 ViewModel 里，
 * 不用像多 Activity 那样通过 Intent 序列化传参。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让内容延伸到状态栏/导航栏下方，由 Compose 的 Scaffold 通过 innerPadding 自行避让。
        // 不调这行的话，Android 15+ 上会看到顶部一条突兀的黑边。
        enableEdgeToEdge()
        setContent {
            DemoTheme {
                DemoApp()
            }
        }
    }
}
