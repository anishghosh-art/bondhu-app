// Top-level build file. Only declares plugin versions; the real work is in app/build.gradle.kts.
// Kotlin 2.2.x is needed because the LiteRT-LM library is compiled with a newer Kotlin than 2.0.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
