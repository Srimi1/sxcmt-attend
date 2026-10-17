plugins {
    id("com.android.application") version "8.10.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    id("org.jetbrains.kotlin.jvm") version "2.1.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.20" apply false
    id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
    id("org.jetbrains.kotlinx.kover") version "0.9.1" apply false
}
val externalBuildRoot = providers.environmentVariable("ATTEND_BUILD_ROOT").orNull
allprojects {
    dependencyLocking { lockAllConfigurations() }
    if (externalBuildRoot != null) layout.buildDirectory.set(file("$externalBuildRoot/${project.name}"))
}
