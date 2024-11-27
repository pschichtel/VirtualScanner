plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinKotlinxSerialization)
    alias(libs.plugins.shadow)
    application
}

group = "tel.schich"
version = "1.1.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.javaToOsNotify)
    implementation(libs.systemTray)
    implementation(libs.zxing)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.slf4jSimple)
}

tasks.test {
    useJUnitPlatform()
}

val jvmTarget = 11

application {
    mainClass.set("tel.schich.virtualscanner.MainKt")
}

kotlin {
    jvmToolchain(jvmTarget)
}
