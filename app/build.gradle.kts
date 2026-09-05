plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.smartlife.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.smartlife.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "2.2.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // ===== Release 签名 =====
    // 读取项目根目录的 keystore.properties（不入库）。
    // 文件不存在时（如刚 clone 的仓库）回退为不签名，仍可正常 assembleRelease。
    val keystorePropsFile = rootProject.file("keystore.properties")
    // 逐行解析 key=value（跳过注释行），避免 java.util.Properties 的类型歧义
    val keystoreProps: Map<String, String>? = if (keystorePropsFile.exists()) {
        keystorePropsFile.readLines()
            .filter { it.isNotBlank() && !it.trim().startsWith("#") && it.contains("=") }
            .associate { line ->
                val (k, v) = line.split("=", limit = 2)
                k.trim() to v.trim()
            }
    } else {
        null
    }

    signingConfigs {
        if (keystoreProps != null) {
            create("release") {
                storeFile = file(keystoreProps.getValue("storeFile"))
                storePassword = keystoreProps.getValue("storePassword")
                keyAlias = keystoreProps.getValue("keyAlias")
                keyPassword = keystoreProps.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Compose BOM：统一约束所有 androidx.compose.* 版本
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    // 核心 KTX
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")

    // Jetpack Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 导航
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // 本地数据库 Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // 偏好设置（主题模式等）持久化
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // 后台任务 / 本地提醒
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // 调试工具
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
