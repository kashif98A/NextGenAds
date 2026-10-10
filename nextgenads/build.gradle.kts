plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.compose)
  `maven-publish`
}
android {
  namespace = "com.admodule.next.genads.adsImp"
  compileSdk { version = release(37) { minorApiLevel = 1 } }

  defaultConfig {
    minSdk = 24
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }

  // android.util.Log etc. return defaults in JVM unit tests instead of throwing "not mocked".
  testOptions {
    unitTests.isReturnDefaultValues = true
  }

  // Expose a single "release" variant for publishing (JitPack / Maven).
  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.process)
  // api so consumers of this library also get the ad + consent SDK types transitively.
  api(libs.ads.mobile.sdk)
  api(libs.user.messaging.platform)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  debugImplementation(libs.androidx.compose.ui.tooling)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}

// JitPack picks up this publication when building the tagged release.
afterEvaluate {
  publishing {
    publications {
      register<MavenPublication>("release") {
        from(components["release"])
        groupId = "com.github.kashif98A"
        artifactId = "nextgenads"
        version = "1.1.2"
      }
    }
  }
}
