import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Observes changes for a given preference key as a Flow.
 *
 * @param T The type of the default value.
 * @param key The preference key to observe.
 * @param default The default value if the key is not set.
 * @param getter A lambda to retrieve the preference value.
 *               It returns a nullable value, and if null, [default] will be used.
 */
private fun <T> SharedPreferences.observeValue(
    key: String,
    default: T,
    getter: SharedPreferences.(String, T) -> T?
): Flow<T> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, changedKey ->
        if (changedKey == key) {
            // Use the default if the getter returns null.
            trySend(prefs.getter(key, default) ?: default)
        }
    }
    registerOnSharedPreferenceChangeListener(listener)
    // Emit the current value immediately, using default if necessary.
    trySend(getter(key, default) ?: default)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}

// Specialized Flow-based observers
fun SharedPreferences.observeInt(key: String, default: Int = 0): Flow<Int> = observeValue(key, default) { key, default -> getInt(key, default) }

fun SharedPreferences.observeBoolean(key: String, default: Boolean = false): Flow<Boolean> = observeValue(key, default) { key, default -> getBoolean(key, default) }

fun SharedPreferences.observeString(key: String, default: String? = null): Flow<String?> = observeValue(key, default) { key, default -> getString(key, default) }

/**
 * Creates a StateFlow for a preference value by mapping it from the stored type to a desired type.
 *
 * @param T The type of the default value.
 * @param M The mapped type.
 * @param key The preference key to observe.
 * @param default The default value if the key is not set.
 * @param getter A lambda to retrieve the preference value. If it returns null, [default] is used.
 * @param mapper A lambda that converts the retrieved value ([T]) into the desired type ([M]).
 * @param scope The CoroutineScope in which the StateFlow is active.
 * @param started Defines the sharing behavior (default: WhileSubscribed with a 5000ms timeout).
 */
fun <T, M> SharedPreferences.stateFlowForMappedValue(
    key: String,
    default: T,
    getter: SharedPreferences.(String, T) -> T?,
    mapper: (T) -> M,
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(5000L)
): StateFlow<M> {
    // Get the current value, using default if null, and map it.
    val initialValue = mapper(getter(key, default) ?: default)
    return observeValue(key, default, getter)
        .map { value -> mapper(value ?: default) }
        .stateIn(scope, started, initialValue)
}

// Specialized StateFlow-based observers without mapping.
fun SharedPreferences.stateFlowFor(
    key: String,
    default: Int = 0,
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(5000L)
): StateFlow<Int> = stateFlowForMappedValue(key, default, { key, default -> getInt(key, default) }, { it }, scope, started)

fun SharedPreferences.stateFlowForBoolean(
    key: String,
    default: Boolean = false,
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(5000L)
): StateFlow<Boolean> = stateFlowForMappedValue(key, default, { key, default -> getBoolean(key, default) }, { it }, scope, started)

fun SharedPreferences.stateFlowForString(
    key: String,
    default: String? = null,
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.WhileSubscribed(5000L)
): StateFlow<String?> = stateFlowForMappedValue(key, default, { key, default -> getString(key, default) }, { it }, scope, started)
