plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.dokka")
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

android {
    namespace = "com.mobilemail"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mobilemail"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
    create("release") {
        storeFile = file("../release/mobilemail.jks")
        storePassword = secret("KEYSTORE_PASSWORD")
        keyAlias = secret("KEY_ALIAS")
        keyPassword = secret("KEY_PASSWORD")
    }
}

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    implementation("io.coil-kt:coil-compose:2.5.0")
    
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    implementation("org.json:json:20230618")
    
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    implementation("androidx.browser:browser:1.8.0")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.material:material-icons-extended")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

tasks.dokkaHtml {
    outputDirectory.set(file("${project.rootDir}/docs/api"))
    moduleName.set("MobileMail")
    
    dokkaSourceSets {
        configureEach {
            includes.from("${project.rootDir}/docs/package.md")
            
            sourceLink {
                localDirectory.set(file("src/main/java"))
                remoteUrl.set(uri("https://github.com/TurtleOld/mobilemail/tree/main/app/src/main/java").toURL())
                remoteLineSuffix.set("#L")
            }
            
            documentedVisibilities.set(
                setOf(
                    org.jetbrains.dokka.DokkaConfiguration.Visibility.PUBLIC,
                    org.jetbrains.dokka.DokkaConfiguration.Visibility.PROTECTED
                )
            )
            
            reportUndocumented.set(true)
            skipEmptyPackages.set(true)
            skipDeprecated.set(false)
        }
    }
}
