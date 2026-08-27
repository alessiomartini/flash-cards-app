// Intentionally empty: every module declares its own plugin versions (see app/build.gradle.kts
// and core/build.gradle.kts) instead of the common root `apply false` pattern. This keeps the
// pure-Kotlin :core module fully independent of AndroidX/Google's Maven repo, so it can be
// built and tested in network environments that only allow Maven Central (like this one) -
// running `./gradlew :app:...` still needs a normal environment with access to Google's Maven.
