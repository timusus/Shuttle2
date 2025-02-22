plugins {
    id("simplecityapps.android.feature")
    id("simplecityapps.android.library.compose")
}

android {
    namespace = "com.simplecityapps.shuttle.composeui"
}

dependencies {

    implementation(libs.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.glide.compose)
}
