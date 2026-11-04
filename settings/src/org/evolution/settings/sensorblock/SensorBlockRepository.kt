/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.settings.sensorblock

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import java.text.Collator

/** One installed app and whether its sensors are blocked. */
internal data class SensorBlockApp(
    val info: ApplicationInfo,
    val label: String,
    val blocked: Boolean,
) {
    val packageName: String get() = info.packageName
    val isSystem: Boolean get() = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
}

/**
 * Reads and writes the blocked packages.
 *
 * The list lives in Settings.Secure as "pkg1;pkg2". The system SensorService watches that key
 * and hands the list to the native sensor service, which then stops delivering motion sensor
 * data to those packages. The sensors stay listed, so apps don't notice the block.
 */
internal class SensorBlockRepository(private val context: Context) {
    companion object {
        /** Same key as Settings.Secure.SENSOR_BLOCKED_PACKAGES, which is hidden API. */
        const val SENSOR_BLOCKED_PACKAGES = "sensor_blocked_packages"
    }

    private val packageManager = context.packageManager
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    private fun getBlockedSet(): MutableSet<String> {
        val raw = Settings.Secure.getString(context.contentResolver, SENSOR_BLOCKED_PACKAGES)
        return raw.orEmpty().split(";").filter { it.isNotEmpty() }.toMutableSet()
    }

    private fun saveBlockedSet(set: Set<String>) {
        val serialized = set.joinToString(";")
        Settings.Secure.putString(context.contentResolver, SENSOR_BLOCKED_PACKAGES, serialized)
    }

    /** Apps that run code, sorted by label. Call off the main thread. */
    fun loadApps(): List<SensorBlockApp> {
        val collator = Collator.getInstance()
        val blockedSet = getBlockedSet()
        return packageManager.getInstalledApplications(0)
            // Overlays and other resource-only packages run no code, so there is nothing
            // to restrict.
            .filter { it.flags and ApplicationInfo.FLAG_HAS_CODE != 0 && !it.isResourceOverlay }
            .map { info ->
                SensorBlockApp(
                    info = info,
                    label = info.loadLabel(packageManager).toString(),
                    blocked = blockedSet.contains(info.packageName)
                )
            }
            .sortedWith { first, second ->
                val result = collator.compare(first.label, second.label)
                if (result != 0) result else first.packageName.compareTo(second.packageName)
            }
    }

    /**
     * Blocks or unblocks an app and force-stops it. Fails if the package was reinstalled as a
     * different app since the list was loaded. Call off the main thread.
     */
    fun setBlocked(app: SensorBlockApp, blocked: Boolean): SensorBlockApp {
        val current = packageManager.getApplicationInfo(app.packageName, 0)
        check(current.uid == app.info.uid) { "Application UID changed" }
        val blockedSet = getBlockedSet()
        if (blocked) {
            blockedSet.add(app.packageName)
        } else {
            blockedSet.remove(app.packageName)
        }
        saveBlockedSet(blockedSet)
        // The block applies when a sensor listener is registered; listeners the app already
        // has keep working, so restart the app.
        activityManager.forceStopPackage(app.packageName)
        return app.copy(info = current, blocked = blocked)
    }
}
