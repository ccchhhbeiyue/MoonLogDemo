package com.example.demo.ui.common

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 占位页脚手架：TopAppBar + 居中占位内容。
 *
 * 抽成公共组件是为了让各阶段的占位页只写 5 行，
 * 同时把 innerPadding 的处理集中在一处——忘记应用 innerPadding 是 Compose 新手
 * 最常见的 bug（内容会被顶部栏遮住），统一收口就不会漏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderScaffold(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = navigationIcon,
                actions = actions
            )
        },
        content = { innerPadding ->
            PlaceholderScreen(
                title = title,
                description = description,
                modifier = Modifier.padding(innerPadding)
            )
        }
    )
}
