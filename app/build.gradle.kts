import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // KSP 플러그인
}

// 💡 [자동 버전업 로직 시작]
val versionPropsFile = file("version.properties")
val versionProps = Properties()

// 파일이 있으면 읽어오고, 없으면 새로 만듭니다.
if (versionPropsFile.canRead()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    versionProps.setProperty("VERSION_CODE", "0")
}

// 현재 숫자를 가져와서 +1 해줍니다.
val currentCode = (versionProps.getProperty("VERSION_CODE") ?: "0").toInt()
val autoVersionCode = currentCode + 1
versionProps.setProperty("VERSION_CODE", autoVersionCode.toString())

// 증가된 숫자를 다시 파일에 저장합니다.
versionProps.store(versionPropsFile.writer(), "Auto-increment build version")

// 사용자에게 보여질 버전 이름 (예: 1.0.1, 1.0.2 ...)
val autoVersionName = "1.0.$autoVersionCode"
// 💡 [자동 버전업 로직 끝]


android {
    namespace = "com.jongwook.siteboard"
    compileSdk = 35 // 에러 9번 줄 수정: 블록{}이 아니라 대입(=)입니다.

    defaultConfig {
        applicationId = "com.jongwook.siteboard"
        minSdk = 24
        targetSdk = 35

        // 💡 [적용 완료!] 고정된 숫자 대신 위에서 만든 자동 변수를 넣습니다.
        versionCode = autoVersionCode
        versionName = autoVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 💡 GPS 위치 정보를 가져오기 위한 구글 플레이 서비스 라이브러리
    implementation("com.google.android.gms:play-services-location:21.2.0")

    // Room DB 설정
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion") // kapt 대신 ksp 사용

    // Activity 확장 (ViewModel, Lifecycle 사용 위함)
    implementation("androidx.activity:activity-ktx:1.8.2")
}