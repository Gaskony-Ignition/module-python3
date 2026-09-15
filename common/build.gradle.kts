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
    archiveBaseName.set("common")
    archiveVersion.set("")
}

dependencies {
    // Ignition SDK dependencies (provided by platform)
    compileOnly(libs.ignition.common)
}
