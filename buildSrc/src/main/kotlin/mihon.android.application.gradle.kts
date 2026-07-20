import mihon.buildlogic.AndroidConfig
import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest
import com.android.build.gradle.AppExtension

plugins {
    id("mihon.code.lint")
}

pluginManager.apply("com.android.application")
pluginManager.apply("org.jetbrains.kotlin.android")

configure<AppExtension> {
    defaultConfig {
        targetSdk = AndroidConfig.TARGET_SDK
    }
}

configureAndroid()
configureTest()
