import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Copies the files both apps share into generated assets (ADR 0007): the replica migrations as
 * assets/replica/NNNN_name.sql, byte for byte, and the synced-table contract as
 * assets/synced-tables.json. The repository copies stay the only ones anyone edits.
 */
abstract class SharedReplicaAssets : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val migrations: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val syncedTables: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        val replica = output.resolve("replica").apply { mkdirs() }
        migrations.get().asFile.listFiles { file -> file.extension == "sql" }.orEmpty().forEach { file ->
            file.copyTo(replica.resolve(file.name))
        }
        syncedTables.get().asFile.copyTo(output.resolve("synced-tables.json"))
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val repositoryRoot: File = rootDir.parentFile

// One version for every part of GoalMaker, read from the repository root (M0-01).
val versionProperties = Properties().apply {
    val file = repositoryRoot.resolve("version.properties")
    require(file.isFile) { "version.properties is missing at the repository root" }
    file.inputStream().use(::load)
}
val appVersionName: String = checkNotNull(versionProperties.getProperty("versionName")?.trim()) {
    "versionName missing from version.properties"
}
val appVersionCode: Int = checkNotNull(versionProperties.getProperty("versionCode")?.trim()?.toInt()) {
    "versionCode missing from version.properties"
}

// Untracked machine settings (android/local.properties) or CI environment variables.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use(::load)
}
fun setting(property: String, environment: String): String =
    localProperties.getProperty(property)?.trim()?.takeIf(String::isNotEmpty)
        ?: providers.gradleProperty(property).orNull?.trim()?.takeIf(String::isNotEmpty)
        ?: System.getenv(environment)?.trim()?.takeIf(String::isNotEmpty)
        ?: ""

// Release signing (docs/setup/signing-and-releases.md). Without it, release builds stay unsigned.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use(::load)
}
fun signingValue(property: String, environment: String): String? =
    keystoreProperties.getProperty(property)?.takeIf(String::isNotBlank)
        ?: System.getenv(environment)?.takeIf(String::isNotBlank)

val releaseStoreFile = signingValue("storeFile", "GOALMAKER_KEYSTORE_FILE")
val releaseStorePassword = signingValue("storePassword", "GOALMAKER_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "GOALMAKER_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "GOALMAKER_KEY_PASSWORD")
val releaseSigningReady = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it != null }

// The cloud project for release builds. Debug builds default to the local stack (ADR 0001).
val cloudSupabaseUrl = setting("goalmaker.supabaseUrl", "GOALMAKER_SUPABASE_URL")
val cloudSupabaseKey = setting("goalmaker.supabaseKey", "GOALMAKER_SUPABASE_KEY")

// The update channel's public key (ADR 0004). Committed once created; empty means "not configured".
val manifestPublicKeyFile = repositoryRoot.resolve("contracts/keys/release-manifest-public.b64")
val manifestPublicKey: String = setting("goalmaker.manifestPublicKey", "GOALMAKER_MANIFEST_PUBLIC_KEY")
    .ifEmpty { if (manifestPublicKeyFile.isFile) manifestPublicKeyFile.readText().trim() else "" }

val sharedReplicaAssets = tasks.register<SharedReplicaAssets>("sharedReplicaAssets") {
    migrations.set(repositoryRoot.resolve("replica/migrations"))
    syncedTables.set(repositoryRoot.resolve("contracts/schemas/synced-tables.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/shared-replica-assets"))
}

fun quoted(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.goalmaker.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.goalmaker.app"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "RELEASE_MANIFEST_PUBLIC_KEY", quoted(manifestPublicKey))
    }

    signingConfigs {
        create("release") {
            if (releaseSigningReady) {
                storeFile = rootProject.file(checkNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            // The emulator reaches the PC's local stack at 10.0.2.2 (ports in supabase/config.toml).
            // The publishable key is the fixed, public key every local Supabase stack uses.
            buildConfigField("String", "DEFAULT_SUPABASE_URL", quoted("http://10.0.2.2:55321"))
            buildConfigField("String", "DEFAULT_SUPABASE_KEY", quoted("sb_publishable_ACJWlzQHlZjBrEguHvfOxg_3BJgxAaH"))
            buildConfigField("boolean", "IS_DEV_BUILD", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "DEFAULT_SUPABASE_URL", quoted(cloudSupabaseUrl))
            buildConfigField("String", "DEFAULT_SUPABASE_KEY", quoted(cloudSupabaseKey))
            buildConfigField("boolean", "IS_DEV_BUILD", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Contract vectors shared with the Windows app (contracts/README.md).
            test.systemProperty("goalmaker.contracts", repositoryRoot.resolve("contracts").absolutePath)
            // Declared as an input so a changed vector file reruns the tests instead of hitting the cache.
            test.inputs.dir(repositoryRoot.resolve("contracts")).withPathSensitivity(PathSensitivity.RELATIVE)
            test.inputs.dir(repositoryRoot.resolve("replica/migrations")).withPathSensitivity(PathSensitivity.RELATIVE)
            // Robolectric's Android 16 runtime reaches into JDK internals (ApplicationSharedMemory).
            test.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
            )
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
        // The Expressive alpha is pinned on purpose (ADR 0005); newer-version nags are noise.
        // targetSdk stays 36 until Android 17's behavior changes are reviewed (M0-04).
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
    }
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(sharedReplicaAssets, SharedReplicaAssets::outputDirectory)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.add("androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

// A release build without the cloud project would ship pointing at nothing.
val releaseSupabaseConfigured = cloudSupabaseUrl.isNotEmpty() && cloudSupabaseKey.isNotEmpty()
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        check(releaseSupabaseConfigured) {
            "Release builds need goalmaker.supabaseUrl and goalmaker.supabaseKey in android/local.properties " +
                "(or GOALMAKER_SUPABASE_URL / GOALMAKER_SUPABASE_KEY). See docs/setup/cloud-supabase.md."
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // The replica (ADR 0007): the bundled SQLite, so every Android version runs the same SQLite as Windows.
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.realtime)
    implementation(libs.ktor.client.okhttp)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    // Unit tests drive the replica through the framework driver under Robolectric; the app ships the bundled one.
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
