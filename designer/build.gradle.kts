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

    // RSyntaxTextArea family (code editor, autocomplete, find/replace UI).
    // The Designer ships its own copies in lib/core/designer (8.3.10: rsyntaxtextarea
    // 3.3.2, autocomplete 3.3.1, rstaui 3.3.1) and they load ahead of anything this
    // module bundles: on 25/11/2026 a 4.6.5 build that shipped autocomplete 4.0.0 and
    // called its new setDescWindowVisibility() threw NoSuchMethodError opening the
    // Script Console, because the class actually loaded was the platform's 3.3.1.
    // So compile against exactly what the Designer runs and ship nothing; these
    // versions follow Ignition's, not the library's latest release.
    compileOnly(libs.rsyntaxtextarea)
    compileOnly(libs.autocomplete)
    compileOnly(libs.rstaui)
    testImplementation(libs.rsyntaxtextarea)

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
    // Explicit launcher pin: Gradle 9 no longer puts a junit-platform-launcher on the
    // test runtime classpath for us, and the launcher must stay aligned with
    // junit-platform-engine 1.14.x (pulled in by junit-jupiter 5.14.x) or discovery fails
    // with "OutputDirectoryCreator not available".
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

