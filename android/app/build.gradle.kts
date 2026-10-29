plugins { id("com.android.application") }
android {
    namespace = "dk.h4xtor.philipsremote"
    compileSdk = 35
    defaultConfig {
        applicationId = "dk.h4xtor.philipsremote"
        minSdk = 26
        targetSdk = 35
        versionCode = 30000
        versionName = "3.0.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
