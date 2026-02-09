plugins {
    id("com.android.application")
}

android {
    namespace = "nisse.whatsmysocken"
    compileSdk = 34

    defaultConfig {
        applicationId = "nisse.whatsmysocken"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    androidResources {
        noCompress.add("bin")
    }
}

dependencies {
    // Standard AndroidX
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.navigation:navigation-fragment:2.7.6")
    implementation("androidx.navigation:navigation-ui:2.7.6")
    implementation("androidx.preference:preference-ktx:1.2.1")

    // --- LIFECYCLE (This is the fix for AndroidViewModel) ---
    val lifecycleVersion = "2.7.0"
    implementation("androidx.lifecycle:lifecycle-viewmodel:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-livedata:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime:$lifecycleVersion")

    // --- PAGING 3 (Standardized to 3.3.0) ---
    val pagingVersion = "3.3.0"
    implementation("androidx.paging:paging-runtime:$pagingVersion")
    implementation("androidx.paging:paging-rxjava3:$pagingVersion")
    implementation("androidx.paging:paging-common:$pagingVersion")

    // --- ROOM ---
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-paging:$roomVersion") // Essential bridge
    annotationProcessor("androidx.room:room-compiler:$roomVersion")

    // --- RXJAVA (Required for Flowable and PagingRx) ---
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("io.reactivex.rxjava3:rxjava:3.1.5")

    // Third Party
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}