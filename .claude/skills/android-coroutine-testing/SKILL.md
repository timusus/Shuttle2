---
name: android-coroutine-testing
description: Comprehensive guidance for writing deterministic tests for Android code using Kotlin coroutines. Use when testing suspending functions, coroutines, flows, ViewModels with coroutines, or when needing to handle test dispatchers, runTest, backgroundScope, testScope, or virtual time control.
---

# Android Coroutine Testing

Write deterministic tests for coroutine-based Android code using `runTest`, test dispatchers,
and proper scope management.

## Quick Start

### Basic Setup

The `kotlinx-coroutines-test` dependency (version 1.10.2, see `gradle/libs.versions.toml`) is
already on the test classpath via `libs.kotlinx.coroutinesTest`.

### Essential Testing Pattern

```kotlin
@Test
fun myCoroutineTest() = runTest {
    // Arrange
    val testDispatcher = StandardTestDispatcher(testScheduler)
    val repository = MyRepository(testDispatcher)

    // Act
    val result = repository.fetchData()

    // Assert
    assertEquals("expected", result)
}
```

## Key Concepts

### runTest
- Wraps test code that calls suspending functions
- Automatically skips delays (uses virtual time)
- Waits for coroutines on test dispatchers to complete
- Use expression body syntax: `= runTest { ... }`

### Test Dispatchers

**StandardTestDispatcher** (default)
- Queues new coroutines
- Requires manual time advancement
- Use `advanceUntilIdle()` to run pending work
- Best for testing precise execution order

**UnconfinedTestDispatcher**
- Executes coroutines eagerly/immediately
- Simpler tests but different from production behavior
- Use when execution order doesn't matter

### Dispatcher Injection

Always inject dispatchers for testability:

```kotlin
// Bad - Hardcoded dispatcher
class Repository {
    suspend fun fetchData() = withContext(Dispatchers.IO) { ... }
}

// Good - Injectable dispatcher
class Repository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun fetchData() = withContext(ioDispatcher) { ... }
}
```

### Main Dispatcher

Replace the Main dispatcher using the repo's existing rule at
`android/app/src/test/java/com/simplecityapps/testing/MainDispatcherRule.kt`:

```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()

@Test
fun viewModelTest() = runTest {
    val viewModel = MyViewModel()
    viewModel.loadData() // Uses test Main dispatcher
    assertEquals(expected, viewModel.state.value)
}
```

It defaults to `UnconfinedTestDispatcher`; pass a `StandardTestDispatcher(testScheduler)` if
a test needs precise execution order instead.

## Working with Scopes

### TestScope Usage
```kotlin
@Test
fun testWithBackgroundWork() = runTest {
    // Use backgroundScope for work that outlives the test body
    backgroundScope.launch {
        // Long-running collection
        repository.dataFlow.collect { ... }
    }

    // Test body completes, but waits for backgroundScope
    repository.updateData("new")
    advanceUntilIdle()
}
```

### Injecting Scopes
```kotlin
class MyClass(private val scope: CoroutineScope) {
    fun startWork() {
        scope.launch { ... }
    }
}

@Test
fun scopeInjectionTest() = runTest {
    val myClass = MyClass(scope = this) // Pass TestScope
    myClass.startWork()
    advanceUntilIdle()
}
```

## Time Control

Control virtual time in tests:

- `advanceUntilIdle()` - Run all pending coroutines
- `advanceTimeBy(millis)` - Advance time and run scheduled work
- `runCurrent()` - Run coroutines at current time only
- `testScheduler.currentTime` - Check current virtual time

## Exception Handling

`kotlinx-coroutines-test` surfaces uncaught exceptions from `backgroundScope` and other test
dispatchers by failing the `runTest` block directly — no separate exception-handler utility
is needed. Assert with a standard `assertThrows` / `try`/`catch` around the failing call
instead.

## Best Practices

1. **One scheduler per test** - Share scheduler between all test dispatchers
2. **Inject dispatchers** - Never use `Dispatchers.IO` or `Dispatchers.Default` directly
3. **Use runTest** - One invocation per test method
4. **Clean up flows** - Cancel infinite flow collections
5. **Avoid real delays** - Use virtual time instead

## Flow Testing

This repo does not depend on Turbine. Test `Flow` emissions by collecting into a list inside
`runTest` (e.g. via `backgroundScope.launch { flow.toList(results) }` or
`flow.first()`/`flow.take(n).toList()`) and asserting on the collected values.

## Mocking

MockK (`libs.mockk`) is on the test classpath if a mock is genuinely needed. Prefer a real
fake implementation over a mock where the collaborator is simple (e.g. an in-memory
repository).
