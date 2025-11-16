# Shuttle E2E Tests

This directory contains end-to-end (E2E) instrumented tests for the Shuttle music player app.

## Overview

The E2E tests cover critical user journeys and are designed to run on real devices or emulators using Gradle Managed Devices for consistent, deterministic test execution.

## Test Suites

### 1. AppLaunchAndNavigationE2ETest
**Critical Journey**: App Launch & Main Navigation

Tests covered:
- ✅ App launches successfully without crashes
- ✅ Bottom navigation functions correctly
- ✅ Navigation between all main sections (Home, Library, Search)
- ✅ Configuration changes (rotation) handling
- ✅ Rapid navigation stress testing

**Why it matters**: These are the most fundamental flows - if users can't launch the app or navigate, nothing else works.

### 2. LibraryBrowseE2ETest
**Critical Journey**: Music Library Browsing

Tests covered:
- ✅ Navigate to Library section
- ✅ Display content or appropriate empty states
- ✅ Switch between library categories (Albums, Artists, Songs, etc.)
- ✅ Scroll through library content
- ✅ Configuration change handling in library
- ✅ Rapid navigation in/out of library

**Why it matters**: Browsing the music library is a core feature - users need to find and explore their music collection.

### 3. SearchE2ETest
**Critical Journey**: Music Search

Tests covered:
- ✅ Navigate to Search section
- ✅ Display empty state before search
- ✅ Interact with search input
- ✅ Handle rapid tab switching
- ✅ Configuration change handling
- ✅ Multiple search operations
- ✅ Search results interaction

**Why it matters**: Search is critical for quickly finding specific music in large libraries.

## Test Architecture

### Utilities (util package)

#### TestUtils.kt
Provides robust, deterministic test helpers:
- `waitForView()` - Waits for views with configurable timeout and retry
- `safeClick()` - Clicks views with automatic retry logic
- `isViewDisplayed()` - Non-throwing view visibility check
- `viewExists()` - Checks if view exists in hierarchy
- `waitForViewToDisappear()` - Waits for view to be removed
- `withRetry()` - Generic retry mechanism for any operation

**Extension functions** for more idiomatic Kotlin:
```kotlin
R.id.someView.waitForDisplay()
R.id.button.safeClick()
R.id.view.isDisplayed()
```

#### PermissionGranter.kt
Handles runtime permissions automatically:
- `grantStoragePermission()` - Grants storage/media permissions programmatically
- `handlePermissionDialogsIfPresent()` - Dismisses permission dialogs via UI automation
- `dismissSystemDialogs()` - Cleans up interfering system dialogs

### Design Principles

1. **Deterministic**: Tests use waiting strategies instead of fixed sleeps where possible
2. **Resilient**: Retry logic handles timing issues and temporary failures
3. **Isolated**: Each test can run independently
4. **Realistic**: Tests simulate real user interactions
5. **Clear**: Each test has a clear purpose documented in KDoc
6. **Graceful**: Tests handle both empty and populated states

## Running the Tests

### Option 1: Gradle Managed Devices (Recommended)

Run tests on all configured managed devices:
```bash
./gradlew :android:app:pixel4api30DebugAndroidTest
./gradlew :android:app:pixel6api31DebugAndroidTest
./gradlew :android:app:pixel6api34DebugAndroidTest
```

Run tests on the CI device group (Pixel 4 API 30 + Pixel 6 API 34):
```bash
./gradlew :android:app:ciGroupDebugAndroidTest
```

### Option 2: Connected Device

Run on a physical device or running emulator:
```bash
./gradlew :android:app:connectedDebugAndroidTest
```

### Option 3: Run Specific Test Class

Run a single test class:
```bash
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.simplecityapps.shuttle.e2e.AppLaunchAndNavigationE2ETest
```

Run a single test method:
```bash
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.simplecityapps.shuttle.e2e.AppLaunchAndNavigationE2ETest#appLaunchesSuccessfully
```

### Option 4: Android Studio

1. Open the test file
2. Click the green play button next to the test class or method
3. Select target device
4. View results in the test runner panel

## Gradle Managed Devices Configuration

The project is configured with these managed devices (see `android/app/build.gradle.kts`):

| Device | API Level | System Image | Use Case |
|--------|-----------|--------------|----------|
| Pixel 4 | 30 | aosp-atd | Baseline Android 11 testing |
| Pixel 6 | 31 | aosp | Android 12 testing |
| Pixel 6 | 34 | aosp | Modern Android 14 testing |

**Device Group "ci"**: Pixel 4 API 30 + Pixel 6 API 34 (optimized for CI pipelines)

**Benefits**:
- ✅ Consistent test environment
- ✅ No manual emulator setup
- ✅ Parallel test execution
- ✅ Automated device provisioning
- ✅ CI/CD friendly

## Test Configuration

### Animations Disabled
```kotlin
testOptions {
    animationsDisabled = true
}
```
This makes tests faster and more deterministic by disabling window animations.

### Custom Test Runner
The app uses a custom test runner (`CustomTestRunner`) with Hilt support for proper dependency injection during tests.

### Permissions
Tests automatically grant necessary permissions:
- `READ_EXTERNAL_STORAGE` (API < 33)
- `READ_MEDIA_AUDIO` (API >= 33)

Both via `GrantPermissionRule` and programmatic shell commands for robustness.

## CI/CD Integration

### GitHub Actions Example

```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  e2e:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run E2E Tests on CI Group
        run: ./gradlew :android:app:ciGroupDebugAndroidTest

      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: android/app/build/reports/androidTests/
```

## Troubleshooting

### Tests fail with "No connected devices"
- Use gradle managed devices: `./gradlew pixel4api30DebugAndroidTest`
- Or start an emulator before running `connectedDebugAndroidTest`

### Permission denied errors
- Tests should auto-grant permissions
- If issues persist, manually grant via: `adb shell pm grant com.simplecityapps.shuttle.dev android.permission.READ_MEDIA_AUDIO`

### Tests are flaky
- The utilities include retry logic, but you can increase timeouts in `TestUtils.waitForView(timeoutMs = 10000)`
- Check logcat for timing issues: `adb logcat | grep Shuttle`

### "Activity not found" errors
- Ensure the app builds successfully first: `./gradlew :android:app:assembleDebug`
- Clean and rebuild: `./gradlew clean :android:app:assembleDebug`

### Managed device setup takes long
- First run downloads and caches system images (one-time cost)
- Subsequent runs are much faster
- Use ATD (Automated Test Device) images for faster startup

## Best Practices for Adding New Tests

1. **Use the utilities**: Leverage `TestUtils` and `PermissionGranter` for robust tests
2. **Document intent**: Add clear KDoc explaining what and why you're testing
3. **Handle both states**: Consider empty state and populated state scenarios
4. **Avoid hardcoded waits**: Use `waitForView()` instead of `Thread.sleep()` where possible
5. **Test real journeys**: Focus on complete user workflows, not individual units
6. **Make it resilient**: Use retry logic for operations that might be timing-sensitive
7. **Clean up**: Always close scenarios in `@After` methods

## Example: Adding a New Test

```kotlin
@Test
fun myNewUserJourney() {
    scenario = ActivityScenario.launch(MainActivity::class.java)
    PermissionGranter.handlePermissionDialogsIfPresent()

    // Navigate to section
    TestUtils.waitForView(R.id.bottomNavigationView, timeoutMs = 10000)
    R.id.mySection.safeClick()

    // Interact with UI
    TestUtils.withRetry {
        R.id.myButton.safeClick()
        Thread.sleep(300)
    }

    // Verify expected state
    onView(withId(R.id.expectedResult))
        .check(matches(isDisplayed()))
}
```

## Resources

- [Android Testing Guide](https://developer.android.com/training/testing)
- [Espresso Documentation](https://developer.android.com/training/testing/espresso)
- [Gradle Managed Devices](https://developer.android.com/studio/test/gradle-managed-devices)
- [Now in Android Reference](https://github.com/android/nowinandroid)

## License

These tests are part of the Shuttle project and follow the same license.
