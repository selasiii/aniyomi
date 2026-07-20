import mihon.buildlogic.configureAndroid
import mihon.buildlogic.configureTest

plugins {
    id("mihon.code.lint")
}

pluginManager.apply("com.android.library")

configureAndroid()
configureTest()
