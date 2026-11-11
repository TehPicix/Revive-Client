import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

plugins {
    alias(libs.plugins.fabric.loom)
    id("maven-publish")
}

/**
 * Resolve an artifact and write a copy of its jar with the `Fabric-Loom-Version` manifest attribute
 * lowered, so fabric-loom 1.14's ArtifactMetadata.validateLoomVersion accepts a dependency mod that
 * was built with a newer (architectury) Loom. Everything else in the jar is copied byte-for-byte.
 * Runs at configuration time (returns a File) because Loom remaps modCompileOnly deps during
 * afterEvaluate — a task output would be produced too late for that.
 */
fun loomVersionPatchedJar(notation: String, loweredLoomVersion: String): File {
    val source = configurations.detachedConfiguration(dependencies.create(notation))
        .apply { isTransitive = false }
        .resolve()
        .single()
    val outDir = layout.buildDirectory.dir("loom-version-patched").get().asFile
    outDir.mkdirs()
    val out = File(outDir, source.nameWithoutExtension + "-loompatched.jar")
    // Regenerate only when stale (source newer than output), so configuration stays cheap.
    if (!out.exists() || source.lastModified() > out.lastModified()) {
        ZipFile(source).use { zin ->
            ZipOutputStream(out.outputStream().buffered()).use { zout ->
                for (entry in zin.entries()) {
                    val bytes = zin.getInputStream(entry).readBytes()
                    val outBytes = if (entry.name == "META-INF/MANIFEST.MF") {
                        String(bytes, Charsets.UTF_8)
                            .replace(Regex("Fabric-Loom-Version: .*"), "Fabric-Loom-Version: $loweredLoomVersion")
                            .toByteArray(Charsets.UTF_8)
                    } else {
                        bytes
                    }
                    zout.putNextEntry(ZipEntry(entry.name))
                    zout.write(outBytes)
                    zout.closeEntry()
                }
            }
        }
    }
    return out
}

base {
    archivesName = properties["archives_base_name"] as String
    group = properties["maven_group"] as String

    val suffix = providers.gradleProperty("build_number").getOrElse("local")
    version = "${libs.versions.minecraft.get()}-$suffix"
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    maven {
        name = "Terraformers"
        url = uri("https://maven.terraformersmc.com")
    }
    maven {
        name = "ViaVersion"
        url = uri("https://repo.viaversion.com")
    }
    mavenCentral()

    exclusiveContent {
        forRepository {
            maven {
                name = "modrinth"
                url = uri("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }

    // Babbaj's maven hosts dev.babbaj:nether-pathfinder (Baritone's native elytra pathfinder).
    // Scoped to its own group so it is never consulted for anything else.
    exclusiveContent {
        forRepository {
            maven {
                name = "babbaj"
                url = uri("https://babbaj.github.io/maven/")
            }
        }
        filter {
            includeGroup("dev.babbaj")
        }
    }
}

val modInclude: Configuration by configurations.creating
val jij: Configuration by configurations.creating

configurations {
    // include mods
    modImplementation.configure {
        extendsFrom(modInclude)
    }
    include.configure {
        extendsFrom(modInclude)
    }

    // include libraries (jar-in-jar)
    implementation.configure {
        extendsFrom(jij)
    }
    // NOTE: `include` deliberately does NOT extendsFrom(jij). The afterEvaluate block below is the
    // single populator of `include` for jar-in-jar libraries: it resolves the whole jij graph
    // (direct + transitive) and adds each artifact once, non-transitively. Having `include` also
    // extend jij put every jij library into the nest set twice and failed remapJar with
    // "Cannot nest 2 jars at the same path".
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)

    val fapiVersion = libs.versions.fabric.api.get()
    // Non-transitive: we bundle exactly the two Fabric API modules we need. Without this,
    // fabric-resource-loader-v1 pulls fabric-api-base transitively too, so base reaches the
    // `include` configuration twice and remapJar fails ("Cannot nest 2 jars at the same path").
    // Bundle the two Fabric API modules we use as jar-in-jar. These are declared with Loom's
    // standard include(modImplementation(...)) idiom rather than the project's custom `modInclude`
    // config: modInclude feeds BOTH modImplementation (which Loom remaps) AND `include` (raw), so
    // Loom's JarNester ended up with the raw and the remapped copy of the same artifact and failed
    // with "Cannot nest 2 jars at the same path". Declaring each dependency object once and handing
    // that same object to modImplementation and include makes Loom nest exactly one (remapped) copy.
    // Non-transitive, plus an explicit base-exclude on resource-loader, so base is bundled once.
    val fabricApiBase = (fabricApi.module("fabric-api-base", fapiVersion) as ModuleDependency).apply {
        isTransitive = false
    }
    val fabricResourceLoader = (fabricApi.module("fabric-resource-loader-v1", fapiVersion) as ModuleDependency).apply {
        isTransitive = false
    }
    modImplementation(fabricApiBase)
    modImplementation(fabricResourceLoader)
    // Bundle both as jar-in-jar. Each is handed to `include` exactly once, and (now that the manual
    // "jars" block has been removed from fabric.mod.json) Loom auto-generates the nested-jar
    // manifest entries itself. resource-loader is non-transitive so it doesn't pull a second base.
    include(fabricApiBase)
    include(fabricResourceLoader)

    // Compat fixes
    modCompileOnly(fabricApi.module("fabric-renderer-indigo", fapiVersion))
    modCompileOnly(libs.sodium) { isTransitive = false }
    modCompileOnly(libs.lithium) { isTransitive = false }
    modCompileOnly(libs.iris) { isTransitive = false }
    modCompileOnly(libs.viafabricplus) { isTransitive = false }
    modCompileOnly(libs.viafabricplus.api) { isTransitive = false }

    modCompileOnly(libs.baritone)
    modCompileOnly(libs.modmenu)

    // Optional integrations (source reconstruction). The decompiled source has compile-time
    // references to these; at runtime they are soft/optional (guarded behind mod-presence checks),
    // so compileOnly / modCompileOnly is correct — they are neither bundled nor hard-required.
    // nether-pathfinder is a plain JNI library (no fabric.mod.json), so plain compileOnly; the Xaero
    // mods are Fabric mods that Loom must remap intermediary->named, so modCompileOnly, non-transitive
    // to avoid dragging their own dependency trees (e.g. a second baritone) onto our classpath.
    compileOnly(libs.nether.pathfinder)
    compileOnly(libs.lambdaevents)
    modCompileOnly(libs.xaeros.minimap) { isTransitive = false }
    modCompileOnly(libs.xaeros.worldmap) { isTransitive = false }
    // XaeroPlus 2.37.0's modrinth jar was built with architectury-loom 1.17 and its manifest
    // declares Fabric-Loom-Version 1.17.493 + Mixin-Remap-Type: static, which fabric-loom 1.14.10
    // rejects outright (ArtifactMetadata.validateLoomVersion; the loom.ignoreDependencyLoomVersion-
    // Validation property only exists in the architectury fork, not here). Since this is a
    // compile-only reference, loomVersionPatchedJar() resolves the real jar and writes a copy under
    // build/ with only its Fabric-Loom-Version lowered (byte-identical otherwise) — no committed
    // artifact needed.
    modCompileOnly(
        files(
            loomVersionPatchedJar("maven.modrinth:xaeroplus:${libs.versions.xaeroplus.get()}", "1.14.0")
        )
    )

    // Libraries (JAR-in-JAR)
    jij(libs.orbit)
    jij(libs.starscript)
    jij(libs.discord.ipc)
    jij(libs.reflections)
    jij(libs.netty.handler.proxy) { isTransitive = false }
    jij(libs.netty.codec.socks) { isTransitive = false }
    jij(libs.waybackauthlib)
    jij(libs.minecraft.auth)
    // Bundled in the shipped jar's META-INF/jars/ (reconstructed; the original build.gradle.kts
    // for this project is not available, so these were added back from what the compiled jar
    // actually embeds and what the decompiled source imports).
    jij(libs.javassist)
    jij(libs.jorbis)
    jij(libs.tritonus.share)
    jij(libs.vorbisspi)

    // NOTE (source reconstruction): Mixin's annotation processor is what lets a @Mixin class cast
    // "this" to its target type at compile time (Mixin's bytecode transformer makes that true only
    // at runtime). On this Loom version it isn't being auto-wired despite fabric.mod.json declaring
    // mixins (confirmed: compileJava's annotationProcessorPath was empty). sponge-mixin and
    // mixinextras-fabric are already pulled in transitively via fabric-loader; this just also
    // registers them as annotation processors, which Gradle does not do automatically.
    annotationProcessor("net.fabricmc:sponge-mixin:0.16.5+mixin.0.8.7")
    annotationProcessor("io.github.llamalad7:mixinextras-fabric:0.5.0")
    // Registers ObfuscationServiceFabric (ServiceLoader, META-INF/services) with the Mixin AP so
    // it understands the "named:intermediary" obfuscation environment used below, instead of only
    // its built-in Forge-era "searge"/"notch" services.
    annotationProcessor("net.fabricmc:fabric-mixin-compile-extensions:0.6.0")
    // NOTE: do NOT add the intermediary-namespace Minecraft jar to this annotationProcessor
    // classpath. It makes the @Accessor/@Invoker literal-name lookups resolve, but it also leaks
    // intermediary class_XXXX names into the processing round, which then abort type-resolution on
    // any class absent from the pinned yarn build (e.g. net.minecraft.class_11515, reached via
    // Fabric API's QuadView) and cascade into ~200 spurious "Mixin has no targets". The correct
    // handling for a source-reconstruction build is instead to downgrade the AP's target-validation
    // diagnostics to warnings (see the -AMSG_* args on compileJava below), since the authoritative
    // refmap already ships in the original jar and does not need to be re-derived here.

    // NOTE: the afterEvaluate block below promotes `jij` deps onto compileOnlyApi/implementation
    // by re-querying the resolved dependency graph, but on this Loom/Gradle version that runs too
    // late relative to Loom's own classpath setup for compileJava to see them (e.g. starscript
    // resolves via Gradle but never reaches javac). Declaring them directly here too is a no-op for
    // jar-in-jar bundling but guarantees javac actually has them, regardless of that ordering.
    compileOnly(libs.orbit)
    compileOnly(libs.starscript)
    compileOnly(libs.discord.ipc)
    compileOnly(libs.reflections)
    compileOnly(libs.waybackauthlib)
    compileOnly(libs.minecraft.auth)
    compileOnly(libs.javassist)
    compileOnly(libs.jorbis)
    compileOnly(libs.tritonus.share)
    compileOnly(libs.vorbisspi)
}

sourceSets {
    val launcher by creating {
        java {
            srcDir("src/launcher/java")
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    if (System.getenv("CI")?.toBoolean() == true) {
        withSourcesJar()
        withJavadocJar()
    }
}

// Handle transitive dependencies for jar-in-jar
// Based on implementation from BaseProject by FlorianMichael/EnZaXD
// Source: https://github.com/FlorianMichael/BaseProject/blob/main/src/main/kotlin/de/florianmichael/baseproject/Fabric.kt
// Licensed under Apache License 2.0
afterEvaluate {
    val jijConfig = configurations.findByName("jij") ?: return@afterEvaluate

    // Dependencies to exclude from jar-in-jar
    val excluded = setOf(
        "org.slf4j",    // Logging provided by Minecraft
        "jsr305"        // Compile time annotations only
    )

    // Dedupe by module group:name. A module can appear in the resolved graph more than once (e.g.
    // javassist is both a direct `jij` lib and a transitive dependency of reflections); adding it to
    // `include` twice makes Loom's JarNester fail with "Cannot nest 2 jars at the same path".
    val seenModules = HashSet<String>()

    jijConfig.incoming.resolutionResult.allDependencies.forEach { dep ->
        val requested = dep.requested.displayName

        if (excluded.any { requested.contains(it) }) return@forEach

        val moduleKey = (dep.requested as? ModuleComponentSelector)?.let { "${it.group}:${it.module}" } ?: requested
        if (!seenModules.add(moduleKey)) return@forEach

        val compileOnlyDep = dependencies.create(requested) {
            isTransitive = false
        }

        val implDep = dependencies.create(compileOnlyDep)

        dependencies.add("compileOnlyApi", compileOnlyDep)
        dependencies.add("implementation", implDep)
        dependencies.add("include", compileOnlyDep)
    }
}

loom {
    accessWidenerPath = file("src/main/resources/revive-client.accesswidener")
}

tasks {
    processResources {
        val buildNumber = providers.gradleProperty("build_number").getOrElse("")
        val commit = providers.gradleProperty("commit").getOrElse("")

        val propertyMap = mapOf(
            "version" to project.version,
            "build_number" to buildNumber,
            "commit" to commit,
            "minecraft_version" to libs.versions.minecraft.get(),
            "loader_version" to libs.versions.fabric.loader.get()
        )

        inputs.properties(propertyMap)
        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    // Compile launcher with Java 8 for backwards compatibility
    named<JavaCompile>("compileLauncherJava").configure {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
        options.compilerArgs.add("-Xlint:-options")
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }

        // Include launcher classes
        from(sourceSets["launcher"].output)

        manifest {
            attributes["Main-Class"] = "meteordevelopment.meteorclient.Main"
        }
    }

    withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(
            listOf(
                "-Xlint:deprecation",
                "-Xlint:unchecked"
            )
        )
    }

    // Mixin annotation-processor arguments (source reconstruction): the AP needs to know where to
    // write its generated refmap, which namespace pair the source/runtime use, and (since Fabric's
    // "intermediary" namespace isn't one of Mixin's built-in obfuscation services) a mapping file
    // it can read that pair from. fabric-mixin-compile-extensions supplies the "named:intermediary"
    // obfuscation service; its inMapFile option just wants a tiny mappings file containing both
    // namespaces, so this points at the same Yarn v2 mappings.tiny already used for the bytecode
    // remap earlier. NOTE: this is a machine-local Gradle-cache path (matches this project's pinned
    // yarn-mappings version, 1.21.11+build.3) — on another machine it would need to point at
    // whatever path that version resolves to there, e.g. via `./gradlew :genSourcesWithLoom` first
    // or by resolving the `mappings` configuration's own artifact directly.
    named<JavaCompile>("compileJava").configure {
        val refMapFile = layout.buildDirectory.file("tmp/compileJava/revive-client-refmap.json")
        val yarnMappingsTiny = System.getProperty("user.home") +
            "/.gradle/caches/fabric-loom/1.21.11/net.fabricmc.yarn.1_21_11.1.21.11+build.3-v2/mappings.tiny"
        options.compilerArgs.addAll(
            listOf(
                "-AoutRefMapFile=${refMapFile.get().asFile.absolutePath}",
                "-AdefaultObfuscationEnv=named:intermediary",
                "-AinMapFileNamedIntermediary=$yarnMappingsTiny",
                // @Accessor/@Invoker/@At(INVOKE...) annotation values are written in intermediary
                // form; resolving them against our named-namespace compile target needs this
                // direction's map too (the above direction alone left every one unresolved).
                "-AinMapFileIntermediaryNamed=$yarnMappingsTiny",

                // Source-reconstruction build: downgrade the Mixin AP's TARGET-VALIDATION
                // diagnostics from errors to warnings. These validators exist only to re-derive a
                // refmap at compile time — but the authoritative refmap already ships inside the
                // original jar (revive-client-refmap.json), so we neither need nor want the AP to
                // reconstruct it. The @Mixin casts already compile (the target type resolves for
                // javac), and @Shadow/@Accessor members are abstract methods that compile
                // regardless; only the AP's internal target REGISTRATION complains. Downgrading
                // these keeps the diagnostics visible without failing the build. Option names are
                // "MSG_<MessageType>" per IMessagerEx.MessageType.getSupportedOptions()/applyOptions.
                "-AMSG_MIXIN_NO_TARGETS=warning",            // @Mixin target not registered by AP
                "-AMSG_ACCESSOR_TARGET_NOT_FOUND=warning",   // @Accessor/@Invoker literal name lookup
                "-AMSG_ACCESSOR_ATTACH_ERROR=warning",       // accessor attach to imaginary target
                "-AMSG_MIXIN_SOFT_TARGET_NOT_FOUND=warning", // @Mixin(targets="...") string targets
                "-AMSG_NO_OBFDATA_FOR_TARGET=warning",       // missing obf mapping for a target
                "-AMSG_NO_OBFDATA_FOR_OVERWRITE=warning"     // missing obf mapping for an @Overwrite
            )
        )
    }

    javadoc {
        with(options as StandardJavadocDocletOptions) {
            addStringOption("Xdoclint:none", "-quiet")
            addStringOption("encoding", "UTF-8")
            addStringOption("charSet", "UTF-8")
        }
    }

    build {
        if (System.getenv("CI")?.toBoolean() == true) {
            dependsOn("javadocJar")
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "revive-client"

            version = "${libs.versions.minecraft.get()}-SNAPSHOT"
        }
    }

    repositories {
        maven("https://maven.meteordev.org/snapshots") {
            name = "meteor-maven"

            credentials {
                username = System.getenv("MAVEN_METEOR_ALIAS")
                password = System.getenv("MAVEN_METEOR_TOKEN")
            }

            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
