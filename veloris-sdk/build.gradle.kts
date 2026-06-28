// veloris-sdk/build.gradle.kts
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "app.veloris.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        targetSdk = 34
        aarMetadata {
            minCompileSdk = 24
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                groupId    = "app.veloris"
                artifactId = "sdk-android"
                version    = "1.0.0"

                pom {
                    name.set("Veloris Sentinel Android SDK")
                    description.set("Behavioural authentication SDK for Android banking apps")
                    url.set("https://github.com/getveloris/veloris-android-sdk")

                    licenses {
                        license {
                            name.set("MIT License")
                            url.set("https://opensource.org/licenses/MIT")
                        }
                    }

                    developers {
                        developer {
                            id.set("veloris")
                            name.set("Veloris")
                            email.set("support@veloris.app")
                        }
                    }

                    scm {
                        connection.set("scm:git:git://github.com/getveloris/veloris-android-sdk.git")
                        developerConnection.set("scm:git:ssh://github.com/getveloris/veloris-android-sdk.git")
                        url.set("https://github.com/getveloris/veloris-android-sdk")
                    }
                }
            }
        }

        repositories {
            maven {
                name = "MavenCentral"
                val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
                url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
                credentials {
                    username = findProperty("sonatypeUsername") as String? ?: ""
                    password = findProperty("sonatypePassword") as String? ?: ""
                }
            }
        }
    }

    signing {
        sign(publishing.publications["release"])
    }
}
