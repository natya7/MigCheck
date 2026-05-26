plugins {
    `java-gradle-plugin`
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

dependencies {
    implementation(project(":core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.launcher)
}

gradlePlugin {
    plugins {
        create("migrationSafety") {
            id = "io.migcheck.migration-safety"
            implementationClass = "io.migcheck.gradle.MigrationSafetyPlugin"
        }
    }
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "128m"
    jvmArgs("-Xms32m")
}
