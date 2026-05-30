import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    id("kotlin-parcelize")
    alias(libs.plugins.ksp)
    alias(libs.plugins.dokka)
    alias(libs.plugins.baselineprofile)
}

fun secret(name: String): String {
    System.getenv(name)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val dotenv = rootProject.file(".env")
    if (dotenv.exists()) {
        dotenv.readLines()
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
            .map {
                val k = it.substringBefore("=").trim()
                val v = it.substringAfter("=").trim().trim('"', '\'')
                k to v
            }
            .firstOrNull { it.first == name }
            ?.second
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }

    error("Missing secret $name (env or .env)")
}

fun optionalProp(name: String): String? =
    (findProperty(name) as String?)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.mobilemail"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.mobilemail"
        minSdk = 26
        targetSdk {
            version = release(36)
        }
        versionCode = optionalProp("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = optionalProp("VERSION_NAME") ?: "1.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "NTFY_URL", "\"${secret("NTFY_URL")}\"")
        buildConfigField("String", "NTFY_TOKEN", "\"${secret("NTFY_TOKEN")}\"")
        buildConfigField("String", "NTFY_TOPIC_PATTERN", "\"${secret("NTFY_TOPIC_PATTERN")}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }

    lint {
        // Workaround for Compose lint crashes in CI:
        // these detectors can throw NPE in Kotlin UAST for some AGP/Kotlin combos.
        disable += setOf(
            "MutableCollectionMutableState",
            "AutoboxingStateCreation"
        )
    }

    signingConfigs {
    create("release") {
        storeFile = file("../.keystore/release.jks")
        storePassword = secret("KEYSTORE_PASSWORD")
        keyAlias = secret("KEY_ALIAS")
        keyPassword = secret("KEY_PASSWORD")
    }
}

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        getByName("debug") {
            signingConfig = signingConfigs.getByName("release")
        }

        create("internal") {
            initWith(getByName("debug"))
            versionNameSuffix = "-internal"
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

baselineProfile {
    warnings {
        maxAgpVersion = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.json)
    implementation(libs.gson)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.paging.runtime.ktx)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.androidx.profileinstaller)

    baselineProfile(project(":baseline-profile"))

    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(rootProject.layout.projectDirectory.dir("docs/api"))
        moduleName.set("MobileMail")
    }

    dokkaSourceSets {
        configureEach {
            includes.from("${project.rootDir}/docs/package.md")
            
            sourceLink {
                localDirectory.set(file("src/main/java"))
                remoteUrl.set(uri("https://github.com/TurtleOld/mobilemail/tree/main/app/src/main/java"))
                remoteLineSuffix.set("#L")
            }
            
            documentedVisibilities.set(setOf(VisibilityModifier.Public, VisibilityModifier.Protected))
            
            reportUndocumented.set(true)
            skipEmptyPackages.set(true)
            skipDeprecated.set(false)
        }
    }
}
