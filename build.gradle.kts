// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// 注意：这里**没有** org.jetbrains.kotlin.android。
// AGP 9.0 起 Kotlin 编译能力已内置于 AGP 并默认启用，再手动声明会因重复配置而构建失败。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
