plugins {
    base
    id("io.ia.sdk.modl") version "0.5.0"
    id("com.github.spotbugs") version "6.4.8" apply false
    id("org.owasp.dependencycheck") version "12.2.0" apply false
}

// ── OWASP Dependency Check ──────────────────────────────────────────────────
apply(plugin = "org.owasp.dependencycheck")
configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
    failBuildOnCVSS = 7.0f
    formats = listOf("HTML", "JSON")
    analyzers.assemblyEnabled = false
}

version = "4.6.2"
group = "com.gaskony"

allprojects {
    version = rootProject.version
    group = "com.gaskony"
}

ignitionModule {
    // Include version in filename for version control
    fileName.set("Python3-${project.version}")

    name.set("Python 3 Integration")
    id.set("com.gaskony.python3")
    moduleVersion.set(project.version.toString())

    moduleDescription.set("Python 3 Integration for Ignition - Gateway Web UI (React) + Designer Script Console (Java Swing). Developed by Gaskony.")

    requiredIgnitionVersion.set("8.3.0")
    requiredFrameworkVersion.set("8")

    // Free module - no license required
    freeModule.set(true)

    projectScopes.putAll(
        mapOf(
            ":common" to "GD",
            ":gateway" to "G",
            ":designer" to "D"  // Designer scope for Python 3 IDE (v1.7.0+, REST API communication)
        )
    )

    hooks.putAll(
        mapOf(
            "com.gaskony.python3.gateway.GatewayHook" to "G",
            "com.gaskony.python3.designer.DesignerHook" to "D"  // Designer hook for IDE UI
        )
    )

    // Module signing configuration
    // Auto-skips signing when the keystore file does not exist (e.g. in CI without secrets).
    // To sign locally, set ignition.signing.keystoreFile in gradle.properties.
    val keystoreFilePath = (findProperty("ignition.signing.keystoreFile") as? String) ?: ""
    skipModlSigning.set(keystoreFilePath.isBlank() || !file(keystoreFilePath).exists())
}

// Note: OWASP dependency check configured at top of file via apply(plugin) + configure<> block

// Sync version across all files that reference it
tasks.register("syncVersion") {
    group = "versioning"
    description = "Syncs project.version to all files that embed it"
    doLast {
        val ver = project.version.toString()
        fun sync(f: File, pattern: Regex, replacement: String) {
            if (!f.exists()) return
            val text = f.readText()
            val updated = text.replace(pattern, replacement)
            if (updated != text) { f.writeText(updated); logger.lifecycle("  synced ${f.name} → $ver") }
        }
        sync(file("web-ui/package.json"),
            Regex(""""version":\s*"[^"]+""""), """"version": "$ver"""")
        sync(file("README.md"),
            Regex("""version-[\d.]+-blue"""), "version-${ver}-blue")
        sync(file("README.md"),
            Regex("""Python3-[\d.]+\.modl"""), "Python3-${ver}.modl")
        sync(file("CLAUDE.md"),
            Regex("""Production-ready v[\d.]+"""), "Production-ready v${ver}")
        sync(file("CLAUDE.md"),
            Regex("""(?m)\*\*Current Version: v[\d.]+\*\*"""), "**Current Version: v${ver}**")
        sync(file("designer/src/main/java/com/gaskony/python3/designer/DesignerHook.java"),
            Regex("""return "[\d.]+"; // MODULE_VERSION_FALLBACK"""), """return "$ver"; // MODULE_VERSION_FALLBACK""")

        // Common-scope version.properties is on the GD classpath, so it is loaded at
        // runtime by BOTH the gateway (MonitoringHandlers.getModuleVersion, which feeds
        // the web UI /version endpoint) and the designer (DesignerHook).
        // Keeping it in common is the single source of truth — a designer-only copy left
        // the gateway endpoint stuck on its hardcoded fallback (see v3.8.1 web-UI bug).
        val parts = ver.split(".")
        if (parts.size == 3) {
            val vp = file("common/src/main/resources/version.properties")
            val content = "version.major=${parts[0]}\nversion.minor=${parts[1]}\nversion.patch=${parts[2]}\n"
            if (!vp.exists() || vp.readText() != content) {
                vp.parentFile.mkdirs()
                vp.writeText(content)
                logger.lifecycle("  synced ${vp.name} → $ver")
            }
        } else {
            logger.warn("syncVersion: cannot decompose version '$ver' into major.minor.patch — version.properties not written")
        }

        logger.lifecycle("syncVersion: all files set to $ver")
    }
}

tasks.named("assembleModlStructure") {
    dependsOn("syncVersion")
}

// ── Static analysis ──────────────────────────────────────────────────────────
subprojects {
    plugins.withType<JavaPlugin> {
        apply(plugin = "checkstyle")
        apply(plugin = "com.github.spotbugs")

        configure<CheckstyleExtension> {
            toolVersion = "10.26.1"
            configFile = rootProject.file("config/checkstyle/checkstyle.xml")
            isIgnoreFailures = true
        }

        configure<com.github.spotbugs.snom.SpotBugsExtension> {
            ignoreFailures.set(false)
            effort.set(com.github.spotbugs.snom.Effort.MAX)
            reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
            excludeFilter.set(rootProject.file("config/spotbugs/exclude.xml"))
        }

        // Disable SpotBugs on test code — enforce only on production sources
        tasks.matching { it.name == "spotbugsTest" }.configureEach {
            enabled = false
        }
    }
}