plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":protocol-core"))
    implementation("openrq:openrq:3.3.2@jar")
    testImplementation(kotlin("test"))
}
