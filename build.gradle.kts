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
    mainClass.set("be.cookies.studio.core.CookieCoreApp")
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
tasks.register<Exec>("packageWindows") {
    dependsOn(packageInput)
    val jdk = javaToolchains.launcherFor(java.toolchain).get().metadata.installationPath.asFile
    commandLine("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "tools/update/Package.ps1",
        "-Version", releaseVersion, "-Jdk", jdk.absolutePath)
}

// Opt-in smoke suite starts real packaged Windows apps in an isolated user-data root.
tasks.test { if (System.getenv("COOKIE_CORE_WINDOWS_SMOKE") == "true") { maxParallelForks = 1 } }
