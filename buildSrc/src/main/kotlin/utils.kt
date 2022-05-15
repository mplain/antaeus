import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.kotlin

/**
 * Configures the current project as a Kotlin project by adding the Kotlin `stdlib` as a dependency.
 */
fun Project.kotlinProject() {
    dependencies {
        // Kotlin libs
        "implementation"(kotlin("stdlib"))
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1")

        // Logging
        "implementation"("org.slf4j:slf4j-simple:1.7.30")
        "implementation"("io.github.microutils:kotlin-logging:1.7.8")

        // Scheduler
        "implementation"("com.github.kagkarlsson:db-scheduler:11.1")

        // Mockk
        "testImplementation"("io.mockk:mockk:1.12.4")

        // Kotest
        "testImplementation"("io.kotest:kotest-runner-junit5-jvm:5.3.0")
    }
}

/**
 * Configures data layer libs needed for interacting with the DB
 */
fun Project.dataLibs() {
    dependencies {
        "implementation"("org.jetbrains.exposed:exposed:0.17.7")
        "implementation"("org.xerial:sqlite-jdbc:3.30.1")
    }
}