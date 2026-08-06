import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application") version "9.2.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.2.20"
    id("com.android.legacy-kapt") version "9.2.1"
}

android {
    namespace = "com.gaurav.avnc"

    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.gaurav.avnc"
        minSdk = 21
        targetSdk = 36
        versionCode = 53
        versionName = "3.3.1"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += "room.schemaLocation" to "$projectDir/roomSchema/"
            }
        }

        externalNativeBuild {
            cmake {
                arguments(
                        "-DANDROID_STL=none", // We are not using STL
                        "-DCMAKE_TOOLCHAIN_FILE=${layout.settingsDirectory}/extern/vcpkg/scripts/buildsystems/vcpkg.cmake",
                        "-DVCPKG_CHAINLOAD_TOOLCHAIN_FILE=\${ndk.moduleNdkDir}/build/cmake/android.toolchain.cmake",
                        "-DVCPKG_MANIFEST_MODE=ON"
                )

                targets("native-vnc", "vncclient")
            }
        }
    }

    sourceSets {
        getByName("androidTest").assets.directories += "$projectDir/roomSchema"
    }

    buildTypes {

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = " (debug)"
        }

        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }

        create("ci") {
            initWith(getByName("release"))
            applicationIdSuffix = ".ci"
            versionNameSuffix = " (CI)"
        }
    }

    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path("CMakeLists.txt")
        }
    }

    bundle {
        density {
            enableSplit = false
        }
        language {
            enableSplit = false
        }
    }

    packaging {
        resources.excludes += "META-INF/LICENSE.md"
        resources.excludes += "META-INF/LICENSE-notice.md"
        resources.excludes += "META-INF/DEPENDENCIES"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_1_8
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.dynamicanimation:dynamicanimation-ktx:1.1.0")
    implementation("androidx.biometric:biometric-ktx:1.2.0-alpha05")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.3")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.3")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    val roomVersion = "2.7.2"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    implementation("com.google.android.material:material:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.connectbot:sshlib:2.2.36")

    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.14")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:3.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-intents:3.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.3.0")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("io.mockk:mockk-android:1.14.5")
    androidTestImplementation("org.apache.sshd:sshd-core:2.16.0")
}