import java.nio.file.Files

plugins {
    id("java")
    id("application")
    id("org.openjfx.javafxplugin") version "0.1.0"
}

group = "be.cookies.studio.core"
version = providers.gradleProperty("releaseVersion").getOrElse("1.0.0")

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

javafx {
    version = "25"
    modules = listOf("javafx.controls", "javafx.fxml","javafx.media")
}

application {
    mainClass.set("be.cookies.studio.core.PackagedLauncher")
    applicationDefaultJvmArgs = listOf("--enable-native-access=javafx.graphics,javafx.media"    )
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.github.hervegirod:fxsvgimage:1.2")
    implementation("com.google.code.gson:gson:2.14.0")
}

tasks.test {
    useJUnitPlatform()
    // CI Windows TEMP may use a junction or an 8.3 alias. Fixtures need a
    // canonical physical root; production path/link checks remain unchanged.
    val fixtureTemp = layout.buildDirectory.dir("test-tmp")
    doFirst {
        val directory = fixtureTemp.get().asFile.toPath()
        Files.createDirectories(directory)
        systemProperty("java.io.tmpdir", directory.toRealPath().toString())
    }

    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}
val releaseVersion = project.version.toString()
require(Regex("(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})").matches(releaseVersion))
val generatedVersion = layout.buildDirectory.dir("generated/version")
val generateVersion by tasks.registering {
    inputs.property("version", releaseVersion)
    outputs.dir(generatedVersion)
    doLast { generatedVersion.get().file("version.txt").asFile.apply { parentFile.mkdirs(); writeText(releaseVersion) } }
}
sourceSets.main { resources.srcDir(generatedVersion) }
tasks.processResources { dependsOn(generateVersion) }
tasks.jar { archiveFileName.set("cookie-core.jar") }
val packageInput by tasks.registering(Sync::class) {
    dependsOn(tasks.jar)
    into(layout.buildDirectory.dir("package-input"))
    from(tasks.jar)
    from(configurations.runtimeClasspath)
}
// The fourth version component is reserved for Microsoft Store.
require(releaseVersion.split('.').let { parts ->
    parts.size == 3 && parts.all { it.toIntOrNull() in 0..65535 } && parts[0].toInt() > 0
}) { "Store version must be MAJOR.MINOR.PATCH (major 1..65535, minor/patch 0..65535)" }

fun registerStorePackage(taskName: String, mode: String) = tasks.register<Exec>(taskName) {
    group = "distribution"
    description = "Build Windows x64 $mode with bundled Java 25 and JavaFX"
    dependsOn(packageInput)
    doFirst {
        val jdk = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile
        commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "RemoteSigned", "-File", "tools/store/Package.ps1",
            "-Version", releaseVersion, "-Jdk", jdk.absolutePath, "-Mode", mode,
            "-IdentityFile", providers.gradleProperty("storeIdentityFile").getOrElse("tools/store/identity.json"))
    }
}
registerStorePackage("packageWindowsAppImage", "AppImage")
registerStorePackage("packageMsix", "Msix")
tasks.register<Exec>("testStorePackaging") {
    group = "verification"
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "RemoteSigned", "-File", "tools/store/Test-Packaging.ps1")
}
tasks.check { dependsOn("testStorePackaging") }