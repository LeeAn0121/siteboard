import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp") // KSP 플러그인
}

// ── 버전 관리 ──────────────────────────────────────────────────────────
// BUILD_NUMBER는 빌드할 때마다 자동으로 증가합니다.
// 매 빌드마다 PATCH를 1 올리고, PATCH가 10을 넘으면 MINOR를 1 올립니다.
// MINOR가 10을 넘으면 MAJOR를 1 올립니다.
val versionPropsFile = file("version.properties")
val versionProps = Properties()

if (versionPropsFile.canRead()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    versionProps.setProperty("VERSION_MAJOR", "1")
    versionProps.setProperty("VERSION_MINOR", "0")
    versionProps.setProperty("VERSION_PATCH", "0")
    versionProps.setProperty("BUILD_NUMBER", "0")
}

var vMajor = (versionProps.getProperty("VERSION_MAJOR") ?: "1").trim().toInt()
var vMinor = (versionProps.getProperty("VERSION_MINOR") ?: "0").trim().toInt()
var vPatch = (versionProps.getProperty("VERSION_PATCH") ?: "0").trim().toInt()
val buildNum = (versionProps.getProperty("BUILD_NUMBER") ?: "0").trim().toInt() + 1

vPatch += 1
if (vPatch > 10) {
    vPatch = 0
    vMinor += 1
}
if (vMinor > 10) {
    vMinor = 0
    vMajor += 1
}

versionProps.setProperty("VERSION_MAJOR", vMajor.toString())
versionProps.setProperty("VERSION_MINOR", vMinor.toString())
versionProps.setProperty("VERSION_PATCH", vPatch.toString())
versionProps.setProperty("BUILD_NUMBER", buildNum.toString())
versionProps.store(versionPropsFile.writer(), "Auto-increment build version")

// versionCode: 스토어 업로드용 정수 (계속 증가)
val autoVersionCode = buildNum
// versionName: 사용자에게 보여지는 버전 (예: 1.0.0)
val autoVersionName = "$vMajor.$vMinor.$vPatch"
// ── 버전 관리 끝 ──────────────────────────────────────────────────────


android {
    namespace = "com.jongwook.siteboard"
    compileSdk = 35 // 에러 9번 줄 수정: 블록{}이 아니라 대입(=)입니다.

    defaultConfig {
        applicationId = "com.jongwook.siteboard"
        minSdk = 24
        targetSdk = 35

        versionCode = autoVersionCode
        versionName = autoVersionName

        // BuildConfig 필드 — 앱 내에서 BuildConfig.BUILD_NUMBER / BuildConfig.VERSION_NAME_FULL 로 접근
        buildConfigField("int", "BUILD_NUMBER", "$buildNum")
        buildConfigField("String", "VERSION_NAME_FULL", "\"$autoVersionName (build $buildNum)\"")

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
        buildConfig = true
    }

}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 💡 GPS 위치 정보를 가져오기 위한 구글 플레이 서비스 라이브러리
    implementation("com.google.android.gms:play-services-location:21.2.0")

    implementation("com.google.mlkit:face-detection:16.1.5")

    // 🚀 [추가] Google ML Kit 개인정보 보호용 라이브러리
    implementation("com.google.mlkit:face-detection:16.1.5")
    // 얼굴 감지
    implementation("com.google.mlkit:text-recognition-korean:16.0.0")
    // 한글/숫자 인식 (번호판용)

    // 🚀 [추가] Task를 코루틴에서 await()로 쓸 수 있게 해주는 라이브러리
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")
    implementation(libs.play.services.location)

    // Room DB 설정
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion") // kapt 대신 ksp 사용

    // Activity 확장 (ViewModel, Lifecycle 사용 위함)
    implementation("androidx.activity:activity-ktx:1.8.2")
}
