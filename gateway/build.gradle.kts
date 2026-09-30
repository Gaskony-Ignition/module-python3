plugins {
    `java-library`
    jacoco
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Configure jar name to be version-independent for module upgrade compatibility
tasks.jar {
    archiveBaseName.set("gateway")
    archiveVersion.set("")
}

dependencies {
    // Common scope dependency
    api(projects.common)

    // SDK dependencies (provided by Ignition)
    compileOnly(libs.ignition.common)
    compileOnly(libs.ignition.gateway.api)
    compileOnly(libs.ignition.perspective.gateway)
    compileOnly(libs.ignition.perspective.common)
    // Raised from 5.0.0 to 6.0.0 (30/07/2026 audit) — matches jakarta.servlet-api-6.0.0.jar
    // bundled in the gateway's own lib/core/gateway/, confirmed by build staying green.
    compileOnly(libs.jakarta.servlet)

    // Third-party libraries to bundle in module
    // Updated from 1.24.0 (CVE-2024-25710, CVE-2024-26308)
    modlImplementation(libs.commons.compress)
    // Shipped rather than compileOnly-pinned to the platform's bundled 2.8.9: no raw
    // com.google.gson usage exists anywhere in this module (JSON goes through Ignition's
    // shaded com.inductiveautomation.ignition.common.gson.* via ApiResponse), so there is
    // no module/platform boundary crossing and no classloader collision risk. Shipping our
    // own copy at latest stable avoids permanently dragging the module onto a 2021-era
    // library for no reason (28-30/07/2026 dependency audit).
    modlImplementation(libs.gson)

    // Test dependencies - make compile dependencies available for tests
    testImplementation(libs.ignition.common)
    testImplementation(libs.ignition.gateway.api)
    testImplementation(libs.gson)
    testImplementation(libs.jakarta.servlet)

    // Test framework
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    // Explicit launcher pin: Gradle 8.10.2's own bundled junit-platform-launcher is older
    // than junit-platform-engine 1.14.x (pulled in by junit-jupiter 5.14.4) and fails
    // discovery with "OutputDirectoryCreator not available" when the two are unaligned.
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.awaitility)
    testImplementation(libs.slf4j.simple)
}

// ========== React UI Build Automation (v3.2.0 - Gateway Web UI) ==========

val reactUIDir = file("${project.rootDir}/web-ui")
val reactDistDir = file("${reactUIDir}/build/generated-resources/mounted")
val gatewayResourcesDir = file("${projectDir}/src/main/resources")

/**
 * Check if npm/node is available on the system.
 */
fun isNpmAvailable(): Boolean {
    return try {
        val process = ProcessBuilder("npm", "--version").start()
        process.waitFor() == 0
    } catch (e: Exception) {
        false
    }
}

/**
 * Task: Install npm dependencies for React UI.
 * Only runs if node_modules doesn't exist.
 */
tasks.register<Exec>("npmInstall") {
    group = "build"
    description = "Install npm dependencies for React UI"

    workingDir = reactUIDir
    commandLine = if (System.getProperty("os.name").lowercase().contains("windows")) {
        listOf("cmd", "/c", "npm", "install")
    } else {
        listOf("npm", "install")
    }

    inputs.file(file("web-ui/package.json"))
    inputs.file(file("web-ui/package-lock.json"))
    outputs.dir(file("web-ui/node_modules"))

    onlyIf {
        !file("${reactUIDir}/node_modules").exists() && isNpmAvailable()
    }

    doFirst {
        if (!isNpmAvailable()) {
            throw GradleException("npm is not available. Please install Node.js and npm to build the React UI.")
        }
        logger.lifecycle("Installing npm dependencies in ${reactUIDir}...")
    }
}

/**
 * Task: Build React UI using Webpack.
 * Outputs: web-ui/build/generated-resources/mounted/Python3IDE.js (UMD module)
 */
tasks.register<Exec>("buildReactUI") {
    group = "build"
    description = "Build React UI with Webpack (TypeScript + UMD bundling)"

    dependsOn("npmInstall")

    workingDir = reactUIDir
    commandLine = if (System.getProperty("os.name").lowercase().contains("windows")) {
        listOf("cmd", "/c", "npm", "run", "build")
    } else {
        listOf("npm", "run", "build")
    }

    inputs.dir("${reactUIDir}/src")
    inputs.file("${reactUIDir}/package.json")
    inputs.file("${reactUIDir}/tsconfig.json")
    inputs.file("${reactUIDir}/webpack.config.js")

    outputs.dir(reactDistDir)

    onlyIf {
        isNpmAvailable()
    }

    doFirst {
        logger.lifecycle("Building React UI with Webpack (UMD module)...")
        if (reactDistDir.exists()) {
            delete(reactDistDir)
        }
    }

    doLast {
        if (reactDistDir.exists()) {
            logger.lifecycle("React UI built successfully: ${reactDistDir.absolutePath}")
        } else {
            throw GradleException("React build failed: build output directory not created")
        }
    }
}

/**
 * Task: Copy React build output to gateway resources.
 */
tasks.register<Task>("copyReactUIResources") {
    group = "build"
    description = "Copy webpack UMD module to gateway resources"

    dependsOn("buildReactUI")

    onlyIf {
        reactDistDir.exists() && isNpmAvailable()
    }

    doFirst {
        logger.lifecycle("Copying React UI webpack bundle to gateway...")

        // Clean old UI resources in mounted folder
        file("${gatewayResourcesDir}/mounted").deleteRecursively()
        file("${gatewayResourcesDir}/mounted").mkdirs()

        // Copy webpack output (Python3IDE.js and source map)
        copy {
            from(reactDistDir) {
                include("Python3IDE.js")
                include("Python3IDE.js.map")
            }
            into("${gatewayResourcesDir}/mounted")
        }

        // Copy standalone HTML page from static resources
        copy {
            from("${gatewayResourcesDir}/static") {
                include("standalone.html")
            }
            into("${gatewayResourcesDir}/mounted")
        }

        // Copy React UMD files for standalone page
        val reactUmdDir = file("${reactUIDir}/node_modules/react/umd")
        val reactDomUmdDir = file("${reactUIDir}/node_modules/react-dom/umd")

        if (reactUmdDir.exists() && reactDomUmdDir.exists()) {
            copy {
                from(reactUmdDir) {
                    include("react.production.min.js")
                }
                from(reactDomUmdDir) {
                    include("react-dom.production.min.js")
                }
                into("${gatewayResourcesDir}/mounted")
            }
            logger.lifecycle("Copied React UMD files for standalone page")
        } else {
            logger.warn("React UMD files not found - standalone page will not work")
        }
    }

    doLast {
        val jsFile = file("${gatewayResourcesDir}/mounted/Python3IDE.js")
        if (jsFile.exists()) {
            val sizeKb = jsFile.length() / 1024
            logger.lifecycle("Copied UMD module: Python3IDE.js (${sizeKb} KB)")
        } else {
            throw GradleException("Failed to copy Python3IDE.js - file not found")
        }
    }
}

/**
 * Task: Clean React build outputs.
 */
tasks.register<Delete>("cleanReactUI") {
    group = "build"
    description = "Clean React UI build outputs"

    delete("${reactUIDir}/build")
    delete("${gatewayResourcesDir}/mounted")

    doLast {
        logger.lifecycle("Cleaned React UI build outputs")
    }
}

// Integrate React build into standard Gradle lifecycle
tasks.named("processResources") {
    dependsOn("copyReactUIResources")
}

tasks.named("clean") {
    dependsOn("cleanReactUI")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    maxHeapSize = "1g"
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.50".toBigDecimal()  // 50% coverage threshold (51.7% at v3.8.0)
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
