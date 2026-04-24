plugins {
    id("com.gradleup.shadow") version "9.3.1"
    java
}

group = "me.katze"
version = "1.5.1"

base {
    archivesName.set("PowerAC")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(8)
    options.compilerArgs.add("-parameters")
}

tasks.processResources {
    val props = mapOf("version" to version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.codemc.io/repository/maven-releases/")
    maven("https://repo.aikar.co/content/groups/aikar/")
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    implementation("com.github.retrooper:packetevents-spigot:2.12.0")
    implementation("co.aikar:acf-bukkit:0.5.1-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:3.14.9")
    implementation("org.bstats:bstats-bukkit:3.2.1")
}

tasks.shadowJar {
    configurations = project.configurations.runtimeClasspath.map { setOf(it) }
    archiveClassifier.set("")
    archiveVersion.set(version.toString())
    relocate("co.aikar.commands", "me.katze.powerac.shaded.acf")
    relocate("co.aikar.locales", "me.katze.powerac.shaded.locales")
    relocate("com.github.retrooper.packetevents", "me.katze.powerac.shaded.packetevents")
    relocate("io.github.retrooper.packetevents", "me.katze.powerac.shaded.packetevents")
    relocate("okhttp3", "me.katze.powerac.shaded.okhttp3")
    relocate("okio", "me.katze.powerac.shaded.okio")
    relocate("org.bstats", "me.katze.powerac.shaded.bstats")
    relocate("net.kyori", "me.katze.powerac.shaded.kyori")
    relocate("com.google.gson", "me.katze.powerac.shaded.gson")
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register("prepareServerArtifacts") {
    dependsOn(tasks.shadowJar)
}
