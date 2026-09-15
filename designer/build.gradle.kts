plugins {
    `java-library`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

// Configure jar name to be version-independent for module upgrade compatibility
tasks.jar {
    archiveBaseName.set("designer")
    archiveVersion.set("")
}

dependencies {
    // Common scope dependency
    api(projects.common)

    // Ignition SDK dependencies (provided by platform)
    compileOnly(libs.ignition.common)
    compileOnly(libs.ignition.designer.api)

    // HTTP Client for REST API communication (Java 11+)
    // Note: HttpClient is part of java.net.http since Java 11, no additional dependency needed

    // JSON parsing (Gson - already available via Ignition SDK)
    compileOnly(libs.ignition.common)  // Provides Gson

    // Logging (30/07/2026 audit: pinned to 2.0.12 — the only bare slf4j-api jar reachable
    // by the Designer scope's classpath is lib/core/common/slf4j-api-2.0.12.jar;
    // lib/core/designer/ has none of its own. Confirmed by unzipping that jar and reading
    // Implementation-Version from META-INF/MANIFEST.MF directly. Treated like
    // jakarta.servlet, not like Gson: slf4j is a logging *facade* whose ServiceLoader
    // binding must match the platform's own logging backend, so shipping a newer copy
    // risks silent binding conflicts rather than a clean isolated classloader win.)
    compileOnly(libs.slf4j.api)
    implementation(libs.slf4j.simple)  // For standalone test harness (free to track latest; see libs.versions.toml)

    // RSyntaxTextArea family - Advanced code editor with syntax highlighting.
    // 30/07/2026 audit finding: plain `implementation` is NOT bundled into the .modl by
    // io.ia.sdk.modl 0.5.0 — only the `modlImplementation` configuration is collected
    // (empirically confirmed: `./gradlew :designer:collectModlDependencies --info`
    // resolved zero artifacts while these were still declared as `implementation`, and
    // the built .modl/designer.jar contained no rsyntaxtextarea/autocomplete/rstaui
    // classes at all). That meant our declared versions were fiction at runtime — the
    // Designer process was actually loading the PLATFORM's older bundled copies
    // (rsyntaxtextarea-3.3.2.jar, autocomplete-3.3.1.jar, rstaui-3.3.1.jar in
    // lib/core/designer/), identical risk profile to an unpinned compileOnly dependency.
    // Switched to `modlImplementation` so our own (newer) copies are what's actually
    // shipped and loaded, matching the version bumps below. Verified by rebuilding and
    // finding rsyntaxtextarea-4.0.1.jar/autocomplete-3.3.3.jar/rstaui-3.3.2.jar inside
    // the built .modl.
    modlImplementation(libs.rsyntaxtextarea)   // 30/07/2026: bumped 3.5.2 -> 4.0.1 (latest stable; requires Java 11+, satisfied by our Java 17 toolchain)
    modlImplementation(libs.autocomplete)      // 30/07/2026: bumped 3.3.1 -> 3.3.3 (latest stable; 3.3.4 still does not exist for this artifact)
    modlImplementation(libs.rstaui)            // 30/07/2026: bumped 3.3.1 -> 3.3.2 (latest stable; 3.3.4 still does not exist for this artifact)

    // FlatLaf dependency REMOVED 30/07/2026 (was `implementation(libs.flatlaf)` 3.4.1).
    // Same "plain implementation isn't bundled" gap as above applied here too, except the
    // platform doesn't bundle FlatLaf at all (checked lib/core/designer, /common, /client)
    // — so it was fully absent from both the .modl and the platform. Its only consumer,
    // `FlatLafScope`, was deleted in v4.3.2/v4.3.3 along with the rest of the legacy
    // standalone Python3IDE cluster (see CHANGELOG), and grep confirms zero remaining
    // references anywhere in the module. This packaging gap is almost certainly the root
    // cause of the "Designer revert - Removed FlatLafScope.withFlatLafDark() wrapping
    // that prevented IDE and Script Console windows from opening at runtime" entry
    // earlier in CHANGELOG.md: FlatLaf's classes were never actually reachable at
    // Designer runtime. Removed rather than re-scoped since it is genuinely dead code,
    // not a live dependency needing a packaging fix.

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
    testImplementation(libs.ignition.common)  // Ignition-bundled Gson for JSON-parsing tests
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

