package com.prplegryn.movio.data

import android.content.Context
import org.json.JSONObject
import java.util.UUID

class MovioStore(context: Context) {
    private val prefs = context.getSharedPreferences("movio_store", Context.MODE_PRIVATE)

    fun loadSettings(): MovioSettings {
        return MovioSettings(
            guangya = GuangyaSession(
                accessToken = prefs.getString("gy_access", "").orEmpty(),
                refreshToken = prefs.getString("gy_refresh", "").orEmpty(),
                deviceId = prefs.getString("gy_device", "").orEmpty().ifBlank { newDeviceId() },
                phone = prefs.getString("gy_phone", "").orEmpty(),
            ),
            rootId = prefs.getString("root_id", "*").orEmpty().ifBlank { "*" },
            tmdbToken = prefs.getString("tmdb_token", "").orEmpty(),
        )
    }

    fun saveSettings(settings: MovioSettings) {
        prefs.edit()
            .putString("gy_access", settings.guangya.accessToken)
            .putString("gy_refresh", settings.guangya.refreshToken)
            .putString("gy_device", settings.guangya.deviceId.ifBlank { newDeviceId() })
            .putString("gy_phone", settings.guangya.phone)
            .putString("root_id", settings.rootId.ifBlank { "*" })
            .putString("tmdb_token", settings.tmdbToken)
            .apply()
    }

    fun progress(fileId: String): Long {
        val data = JSONObject(prefs.getString("progress", "{}").orEmpty().ifBlank { "{}" })
        return data.optLong(fileId, 0L)
    }

    fun saveProgress(fileId: String, positionMs: Long) {
        val data = JSONObject(prefs.getString("progress", "{}").orEmpty().ifBlank { "{}" })
        data.put(fileId, positionMs.coerceAtLeast(0L))
        prefs.edit().putString("progress", data.toString()).apply()
    }

    private fun newDeviceId(): String =
        UUID.randomUUID().toString().replace("-", "")
}
