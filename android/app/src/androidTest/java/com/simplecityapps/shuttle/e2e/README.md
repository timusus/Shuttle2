# Shuttle E2E Tests

This directory contains end-to-end (E2E) instrumented tests for the Shuttle music player app.

## Overview

The E2E tests cover **7 critical user journeys** and are designed to run on real devices or emulators using Gradle Managed Devices for consistent, deterministic test execution.

**Total Test Coverage**: 70+ comprehensive tests across all major app features.

## Test Suites

### 1. AppLaunchAndNavigationE2ETest
**Critical Journey**: App Launch & Main Navigation
**Tests**: 5 comprehensive scenarios

Tests covered:
- ✅ App launches successfully without crashes
- ✅ Bottom navigation functions correctly
- ✅ Navigation between all main sections (Home, Library, Search)
- ✅ Configuration changes (rotation) handling
- ✅ Rapid navigation stress testing

**Why it matters**: These are the most fundamental flows - if users can't launch the app or navigate, nothing else works.

### 2. LibraryBrowseE2ETest
**Critical Journey**: Music Library Browsing
**Tests**: 6 comprehensive scenarios

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
**Tests**: 7 comprehensive scenarios

Tests covered:
- ✅ Navigate to Search section
- ✅ Display empty state before search
- ✅ Interact with search input
- ✅ Handle rapid tab switching
- ✅ Configuration change handling
- ✅ Multiple search operations
- ✅ Search results interaction

**Why it matters**: Search is critical for quickly finding specific music in large libraries.

### 4. PlaybackControlsE2ETest
**Critical Journey**: Playback Controls & Music Playback
**Tests**: 9 comprehensive scenarios

Tests covered:
- ✅ Playback sheet structure and UI elements
- ✅ Can interact with playback sheet (expand/collapse)
- ✅ Peek view (mini player) accessibility
- ✅ Multi-sheet interaction stability
- ✅ Rapid playback control interactions
- ✅ Sheet functionality during navigation
- ✅ Bottom sheet behavior initialization
- ✅ Configuration change handling
- ✅ Playback infrastructure survives backgrounding

**Why it matters**: Playback is the core function of a music player - users must be able to control music playback reliably.

### 5. HomeScreenE2ETest
**Critical Journey**: Home Screen & Personalized Content
**Tests**: 11 comprehensive scenarios

Tests covered:
- ✅ Navigate to Home screen successfully
- ✅ Display personalized content or appropriate states
- ✅ Home content scrolling
- ✅ Configuration change handling
- ✅ Rapid navigation to/from Home
- ✅ Load personalized content sections
- ✅ Refresh gestures handling
- ✅ Quick actions accessibility
- ✅ Empty library handling
- ✅ Content items interactivity
- ✅ Load performance validation

**Why it matters**: Home is the landing experience for returning users - it must be fast, personalized, and functional.

### 6. QueueManagementE2ETest
**Critical Journey**: Playback Queue Management
**Tests**: 12 comprehensive scenarios

Tests covered:
- ✅ Queue sheet structure exists
- ✅ Can interact with queue sheet (expand/collapse)
- ✅ Queue peek view accessibility
- ✅ Queue container initialization
- ✅ Configuration change handling
- ✅ Queue persists across navigation
- ✅ Queue accessible from all sections
- ✅ Rapid queue interaction handling
- ✅ Empty queue state handling
- ✅ Queue sheet layering/z-order
- ✅ Queue independence from playback controls
- ✅ Queue survives backgrounding

**Why it matters**: Queue management is essential for controlling what plays next and curating listening sessions.

### 7. SettingsAndPreferencesE2ETest
**Critical Journey**: Settings & App Configuration
**Tests**: 9 comprehensive scenarios

Tests covered:
- ✅ Access settings/menu section
- ✅ Settings UI accessibility
- ✅ Menu drawer opens and closes properly
- ✅ Access equalizer/DSP settings
- ✅ Configuration change handling
- ✅ Menu options functionality
- ✅ Back navigation from settings
- ✅ Rapid menu open/close handling
- ✅ Settings accessible from all screens

**Why it matters**: Users need to customize their experience and access app features like equalizer, themes, and preferences.

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

## Test Coverage Summary

| Test Suite | # of Tests | Focus Area | Critical? |
|------------|-----------|------------|-----------|
| AppLaunchAndNavigation | 5 | App startup & navigation | ⭐⭐⭐ |
| LibraryBrowse | 6 | Music library browsing | ⭐⭐⭐ |
| Search | 7 | Music search functionality | ⭐⭐ |
| PlaybackControls | 9 | Music playback controls | ⭐⭐⭐ |
| HomeScreen | 11 | Home screen & personalization | ⭐⭐ |
| QueueManagement | 12 | Playback queue management | ⭐⭐⭐ |
| SettingsAndPreferences | 9 | App settings & configuration | ⭐⭐ |
| **TOTAL** | **70+** | **All major features** | |

## CI/CD Integration

### GitHub Actions Example

```yaml
name: E2E Tests

on: [push, pull_request]

jobs:
  e2e:
    runs-on: ubuntu-latest
    timeout-minutes: 45
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x gradlew

      - name: Run E2E Tests on CI Group
        run: ./gradlew :android:app:ciGroupDebugAndroidTest

      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: android/app/build/reports/androidTests/

      - name: Upload Test Results
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-results
          path: android/app/build/outputs/androidTest-results/
```

### Running Specific Test Suites in CI

```bash
# Run only critical tests (app launch, playback, queue)
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=\
com.simplecityapps.shuttle.e2e.AppLaunchAndNavigationE2ETest,\
com.simplecityapps.shuttle.e2e.PlaybackControlsE2ETest,\
com.simplecityapps.shuttle.e2e.QueueManagementE2ETest

# Run all E2E tests in the e2e package
./gradlew :android:app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package=com.simplecityapps.shuttle.e2e
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

## Performance Expectations

Based on test design, expected run times on managed devices:

| Test Suite | Estimated Time | Complexity |
|------------|---------------|------------|
| AppLaunchAndNavigation | 2-3 min | Low |
| LibraryBrowse | 2-3 min | Low-Medium |
| Search | 3-4 min | Medium |
| PlaybackControls | 3-4 min | Medium |
| HomeScreen | 4-5 min | Medium-High |
| QueueManagement | 4-5 min | Medium |
| SettingsAndPreferences | 3-4 min | Medium |
| **Total (all suites)** | **20-30 min** | |
| **CI Group (critical only)** | **10-15 min** | |

*Times may vary based on device performance and network conditions*

## Test Maintenance

### When to Update Tests

1. **Major UI changes**: Update view IDs and interaction patterns
2. **New features**: Add new test methods to existing suites or create new suites
3. **Bug fixes**: Add regression tests to prevent recurrence
4. **Navigation changes**: Update navigation flow tests
5. **API changes**: Update tests if device/OS behavior changes

### Test Health Indicators

**Healthy tests**:
- ✅ Pass consistently across all managed devices
- ✅ Complete within expected time ranges
- ✅ Provide clear failure messages
- ✅ Handle both success and failure scenarios

**Tests needing attention**:
- ⚠️ Flaky (intermittent failures)
- ⚠️ Taking significantly longer than expected
- ⚠️ Failing on specific devices only
- ⚠️ Cryptic failure messages

## License

These tests are part of the Shuttle project and follow the same license.
