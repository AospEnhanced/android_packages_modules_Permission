/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.permission.data

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.annotation.GuardedBy
import androidx.annotation.MainThread
import com.android.modules.utils.build.SdkLevel
import com.android.permissioncontroller.PermissionControllerApplication
import com.android.permissioncontroller.permission.utils.ContextCompat
import com.android.permissioncontroller.permission.utils.KotlinUtils
import java.util.concurrent.TimeUnit

/**
 * A generalize data repository, which carries a component callback which trims its data in response
 * to memory pressure
 */
abstract class DataRepository<K, V : DataRepository.InactiveTimekeeper> : ComponentCallbacks2 {

    /**
     * Deadlines for removal based on memory pressure. Live Data objects which have been inactive
     * for longer than the deadline will be removed.
     */
    private val TIME_THRESHOLD_LAX_NANOS: Long = TimeUnit.NANOSECONDS.convert(5, TimeUnit.MINUTES)
    private val TIME_THRESHOLD_TIGHT_NANOS: Long = TimeUnit.NANOSECONDS.convert(1, TimeUnit.MINUTES)
    private val TIME_THRESHOLD_ALL_NANOS: Long = 0

    protected val lock = Any()
    @GuardedBy("lock") protected val data = mutableMapOf<K, V>()

    /** Whether or not this data repository has been registered as a component callback yet */
    private var registered = false
    /** Whether or not this device is a low-RAM device. */
    private var isLowMemoryDevice =
        PermissionControllerApplication.get()
            .getSystemService(ActivityManager::class.java)
            ?.isLowRamDevice
            ?: false

    init {
        PermissionControllerApplication.get().registerComponentCallbacks(this)
    }

    /**
     * Get a value from this repository, creating it if needed
     *
     * @param key The key associated with the desired Value
     * @return The cached or newly created Value for the given Key
     */
    operator fun get(key: K): V {
        synchronized(lock) {
            return data.getOrPut(key) { newValue(key) }
        }
    }

    /**
     * Generate a new value type from the given data
     *
     * @param key Information about this value object, used to instantiate it
     * @return The generated Value
     */
    @MainThread protected abstract fun newValue(key: K): V

    /**
     * Remove LiveData objects with no observer.
     */
    override fun onTrimMemory(level: Int) {
        if (isLowMemoryDevice) {
            trimInactiveData(TIME_THRESHOLD_ALL_NANOS)
            return
        }

        if (SdkLevel.isAtLeastU() && level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // On UDC+ TRIM_MEMORY_BACKGROUND may be the last callback an app will receive
            // before it's frozen.
            trimInactiveData(TIME_THRESHOLD_ALL_NANOS)
            return
        }

        trimInactiveData(
            threshold =
                when (level) {
                    ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> TIME_THRESHOLD_LAX_NANOS
                    // Allow handling for trim levels that are deprecated in newer API versions
                    // but are still supported on older devices that this code ships to.
                    @Suppress("DEPRECATION") ComponentCallbacks2.TRIM_MEMORY_MODERATE -> TIME_THRESHOLD_TIGHT_NANOS
                    @Suppress("DEPRECATION") ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> TIME_THRESHOLD_ALL_NANOS
                    @Suppress("DEPRECATION") ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> TIME_THRESHOLD_LAX_NANOS
                    @Suppress("DEPRECATION") ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> TIME_THRESHOLD_TIGHT_NANOS
                    @Suppress("DEPRECATION") ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> TIME_THRESHOLD_ALL_NANOS
                    else -> return
                }
        )
    }

    // Allow handling for trim levels that are deprecated in newer API versions
    // but are still supported on older devices that this code ships to.
    override fun onLowMemory() {
        onTrimMemory(@Suppress("DEPRECATION") ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Do nothing, but required to override by interface
    }

    fun invalidateSingle(key: K) {
        synchronized(lock) { data.remove(key) }
    }

    private fun trimInactiveData(threshold: Long) {
        synchronized(lock) {
            data.keys.toList().forEach { key ->
                if (data[key]?.timeInactive?.let { it >= threshold } == true) {
                    data.remove(key)
                }
            }
        }
    }

    /**
     * Interface which describes an object which can track how long it has been inactive, and if it
     * has any observers.
     */
    interface InactiveTimekeeper {

        /**
         * Long value representing the time this object went inactive, which is read only on the
         * main thread, so does not cause race conditions.
         */
        var timeWentInactive: Long?

        /**
         * Calculates the time since this object went inactive.
         *
         * @return The time since this object went inactive, or null if it is not inactive
         */
        val timeInactive: Long?
            get() {
                val time = timeWentInactive ?: return null
                return System.nanoTime() - time
            }
    }
}

/**
 * A DataRepository where all values are contingent on the existence of a package. Supports
 * invalidating all values tied to a package. Expects key to be a pair or triple, with the package
 * name as the first value of the key.
 */
abstract class DataRepositoryForPackage<K, V : DataRepository.InactiveTimekeeper> :
    DataRepository<K, V>() {

    /**
     * Invalidates every value with the packageName in the key.
     *
     * @param packageName The package to be invalidated
     */
    fun invalidateAllForPackage(packageName: String) {
        synchronized(lock) {
            for (key in data.keys.toSet()) {
                if (
                    key is Pair<*, *> ||
                        key is Triple<*, *, *> ||
                        key is KotlinUtils.Quadruple<*, *, *, *> && key.first == packageName
                ) {
                    data.remove(key)
                }
            }
        }
    }
}

/**
 * A DataRepository to cache LiveData for a device. The device can be a primary device with default
 * deviceId in the key, or a remote device with virtual device Id in the key. It uses deviceId to
 * initialize a new LiveData instance. Note: the virtual device Id should always be the last element
 * in the composite key.
 */
abstract class DataRepositoryForDevice<K, V : DataRepository.InactiveTimekeeper> :
    DataRepositoryForPackage<K, V>() {

    @MainThread protected abstract fun newValue(key: K, deviceId: Int): V

    override fun newValue(key: K): V {
        return newValue(key, ContextCompat.DEVICE_ID_DEFAULT)
    }

    fun getWithDeviceId(key: K, deviceId: Int): V {
        synchronized(lock) {
            return data.getOrPut(key) { newValue(key, deviceId) }
        }
    }
}

/** A convenience to retrieve data from a repository with a composite key */
operator fun <K1, K2, V : DataRepository.InactiveTimekeeper> DataRepository<Pair<K1, K2>, V>.get(
    k1: K1,
    k2: K2
): V {
    return get(k1 to k2)
}

/** A convenience to retrieve data from a repository with a composite key */
operator fun <K1, K2, K3, V : DataRepository.InactiveTimekeeper> DataRepository<
    Triple<K1, K2, K3>, V
>
    .get(k1: K1, k2: K2, k3: K3): V {
    return get(Triple(k1, k2, k3))
}

/** A getter on DataRepositoryForDevice to retrieve a LiveData for a device. */
operator fun <K1, K2, V : DataRepository.InactiveTimekeeper> DataRepositoryForDevice<
    Triple<K1, K2, Int>, V
>
    .get(k1: K1, k2: K2, deviceId: Int): V {
    return getWithDeviceId(Triple(k1, k2, deviceId), deviceId)
}

/**
 * A collection of getters on DataRepositoryForDevice to conveniently retrieve a LiveData for tbe
 * primary device. The param can be in the format of Pair<K1, K2> or [K1, K2]
 */
operator fun <K1, K2, V : DataRepository.InactiveTimekeeper> DataRepositoryForDevice<
    Triple<K1, K2, Int>, V
>
    .get(
    k1: K1,
    k2: K2,
): V {
    return getWithDeviceId(
        Triple(k1, k2, ContextCompat.DEVICE_ID_DEFAULT),
        ContextCompat.DEVICE_ID_DEFAULT
    )
}

operator fun <K1, K2, V : DataRepository.InactiveTimekeeper> DataRepositoryForDevice<
    Triple<K1, K2, Int>, V
>
    .get(key: Pair<K1, K2>): V {
    return getWithDeviceId(
        Triple(key.first, key.second, ContextCompat.DEVICE_ID_DEFAULT),
        ContextCompat.DEVICE_ID_DEFAULT
    )
}

/** A getter on DataRepositoryForDevice to retrieve a LiveData for a device. */
operator fun <K1, K2, K3, V : DataRepository.InactiveTimekeeper> DataRepositoryForDevice<
    KotlinUtils.Quadruple<K1, K2, K3, Int>, V
>
    .get(k1: K1, k2: K2, k3: K3, deviceId: Int): V {
    return getWithDeviceId(KotlinUtils.Quadruple(k1, k2, k3, deviceId), deviceId)
}

/**
 * A collection of getters on DataRepositoryForDevice to conveniently retrieve a LiveData for tbe
 * primary device. The param can be in the format of Triple<K1, K2, K3> or [K1, K2, K3]
 */
operator fun <K1, K2, K3, V : DataRepository.InactiveTimekeeper> DataRepositoryForDevice<
    KotlinUtils.Quadruple<K1, K2, K3, Int>, V
>
    .get(k1: K1, k2: K2, k3: K3): V {
    return getWithDeviceId(
        KotlinUtils.Quadruple(k1, k2, k3, ContextCompat.DEVICE_ID_DEFAULT),
        ContextCompat.DEVICE_ID_DEFAULT
    )
}

operator fun <K1, K2, K3, V : DataRepository.InactiveTimekeeper> DataRepositoryForDevice<
    KotlinUtils.Quadruple<K1, K2, K3, Int>, V
>
    .get(key: Triple<K1, K2, K3>): V {
    return getWithDeviceId(
        KotlinUtils.Quadruple(key.first, key.second, key.third, ContextCompat.DEVICE_ID_DEFAULT),
        ContextCompat.DEVICE_ID_DEFAULT
    )
}
