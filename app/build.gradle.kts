plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // AGP 9 内置 Kotlin，kapt 与之不兼容，注解处理统一走 KSP
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.example.demo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.demo"
        // API 26 (Android 8.0)：原生支持 java.time，无需 desugaring
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // Room Schema 导出目录，作用等同 Flyway 的迁移基线，需提交进 Git
    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Lifecycle：ViewModel 接入 Compose + collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation：底部导航与页面路由
    implementation(libs.androidx.navigation.compose)

    // Room：本地持久化（compiler 必须用 ksp，不能用 kapt）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 协程：Application 级的数据库预热、Repository 的 Flow 收集、ViewModel 的 stateIn 都要用
    implementation(libs.kotlinx.coroutines.android)

    // 月历组件
    implementation(libs.calendar.compose)

    // 测试
    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
