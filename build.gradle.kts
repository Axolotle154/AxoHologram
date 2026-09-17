plugins {
    java
    kotlin("jvm") version "2.3.0"
}

group = "org.axostudio"
version = "3.2.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://repo.fancyinnovations.com/releases")
    maven("https://jitpack.io")
    maven("https://oss.sonatype.org/content/groups/public/")
    maven("https://repo.citizensnpcs.co/")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:[26.1.2.build,)")

    // External hooks (compileOnly)
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:3.2.0")
    compileOnly("de.oliver:FancyNpcs:2.9.2")
    compileOnly("net.citizensnpcs:citizens-main:2.0.35-SNAPSHOT") {
        isTransitive = false
    }

    // Paper resolves this at bootstrap through AxoHologramPluginLoader.
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")

    // Utilities & Media processing
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.jcodec:jcodec:0.2.5")
    implementation("org.jcodec:jcodec-javase:0.2.5")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.12.0")

    // Testing
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.3.0")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    testImplementation("io.papermc.paper:paper-api:[26.1.2.build,)")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("AxoHologram-${project.version}.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from({
        configurations.runtimeClasspath.get().map { dependency ->
            if (dependency.isDirectory) dependency else zipTree(dependency)
        }
    })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("net.bytebuddy.experimental", "true")
}
