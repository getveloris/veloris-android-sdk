plugins {
    id("com.android.library") version "8.2.0"
    id("org.jetbrains.kotlin.android") version "1.9.22"
    id("maven-publish")
    id("signing")
    id("com.gradleup.nmcp") version "0.0.8"
}

android {
    namespace = "app.veloris.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        aarMetadata { minCompileSdk = 24 }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId    = "app.veloris"
            artifactId = "sdk-android"
            version    = "1.0.0"

            afterEvaluate {
                from(components["release"])
            }

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
}

signing {
    sign(publishing.publications["release"])
}

nmcp {
    publish("release") {
        username = findProperty("sonatypeUsername") as String? ?: ""
        password = findProperty("sonatypePassword") as String? ?: ""
        publicationType = "AUTOMATIC"
    }
}
