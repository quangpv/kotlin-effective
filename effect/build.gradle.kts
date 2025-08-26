plugins {
    id("java-library")
    kotlin("jvm")
}

java {
}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation(kotlin("test"))
    testImplementation(libs.testng)
}