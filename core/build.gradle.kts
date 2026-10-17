plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlinx.kover")
}
kotlin { jvmToolchain(17) }
dependencies {
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
}
tasks.test { useJUnitPlatform() }
kover { reports { total { verify { rule { minBound(80) } } } } }
