plugins {
    `java-library`
    `java-test-fixtures`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

dependencies {
    implementation(libs.jsqlparser)

    testFixturesApi(platform(libs.testcontainers.bom))
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.postgresql)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.launcher)

    compileOnly(platform(libs.junit.bom))
    compileOnly(libs.junit.jupiter)
    compileOnly(platform(libs.testcontainers.bom))
    compileOnly(libs.testcontainers.postgresql)
    compileOnly(libs.postgresql)
}

tasks.test { useJUnitPlatform() }
