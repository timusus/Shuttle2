import com.android.build.gradle.LibraryExtension
import com.simplecityapps.android.configureGradleManagedDevices
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.kotlin

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("simplecityapps.android.library")
                apply("simplecityapps.android.hilt")
            }

            extensions.configure<LibraryExtension> {
                defaultConfig {
                    testInstrumentationRunner = "com.simplecityapps.shuttle.cast.core.testing.CustomTestRunner"
                }
                configureGradleManagedDevices(this)
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

            dependencies {
//                add("implementation", project(":core:ui"))
//                add("implementation", project(":core:domain"))
//                add("implementation", project(":core:navigation"))
//                add("implementation", project(":core:common"))

                add("testImplementation", kotlin("test"))
//                add("testImplementation", project(":core:testing"))
                add("androidTestImplementation", kotlin("test"))
//                add("androidTestImplementation", project(":core:testing"))

                add("implementation", libs.findLibrary("androidx.hilt.navigation.compose").get())
                add("implementation", libs.findLibrary("androidx.lifecycle.runtimeCompose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())

                add("implementation", libs.findLibrary("kotlinx-coroutinesAndroid").get())

                add("implementation", libs.findLibrary("androidx.compose.foundation").get())
                add("implementation", libs.findLibrary("androidx.compose.foundation.layout").get())
                add("implementation", libs.findLibrary("androidx.compose.material.iconsExtended").get())
                add("implementation", libs.findLibrary("androidx.compose.material3").get())
                add("implementation", libs.findLibrary("androidx.compose.ui.tooling.preview").get())
                add("implementation", libs.findLibrary("androidx.compose.ui.util").get())
                add("implementation", libs.findLibrary("androidx.compose.runtime").get())

                add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
