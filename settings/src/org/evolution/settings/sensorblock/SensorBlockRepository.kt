/* Copyright (C) 2026 Evolution X
 * SPDX-License-Identifier: Apache-2.0
 */
package org.evolution.settings.sensorblock

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Settings
import java.text.Collator

internal data class SensorBlockApp(
    val info: ApplicationInfo,
    val label: String,
    val blocked: Boolean,
) {
    val packageName: String get() = info.packageName
    val isSystem: Boolean get() = info.flags and ApplicationInfo.FLAG_SYSTEM != 0
}

internal class SensorBlockRepository(private val context: Context) {
    companion object {
        const val SENSOR_BLOCKED_PACKAGES = "sensor_blocked_packages"
    }

    private val packageManager = context.packageManager
    private val activityManager = context.getSystemService(ActivityManager::class.java)

    private fun getBlockedSet(): MutableSet<String> {
        val raw = Settings.Secure.getString(context.contentResolver, SENSOR_BLOCKED_PACKAGES) ?: ""
        return if (raw.isEmpty()) {
            mutableSetOf()
        } else {
            raw.split(";").filter { it.isNotEmpty() }.toMutableSet()
        }
    }

    private fun saveBlockedSet(set: Set<String>) {
        val serialized = set.joinToString(";")
        Settings.Secure.putString(context.contentResolver, SENSOR_BLOCKED_PACKAGES, serialized)
    }

    fun loadApps(): List<SensorBlockApp> {
        val collator = Collator.getInstance()
        val blockedSet = getBlockedSet()
        return packageManager.getInstalledApplications(0)
            // Overlays and other resource-only packages run no code, so there is nothing to restrict.
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
        // Sensors are filtered when the process starts, so restart the app.
        activityManager.forceStopPackage(app.packageName)
        return app.copy(info = current, blocked = blocked)
    }
}
