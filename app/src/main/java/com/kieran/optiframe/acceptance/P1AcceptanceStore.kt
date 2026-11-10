package com.kieran.optiframe.acceptance

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class P1AcceptanceStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    @Synchronized
    fun add(record: P1AcceptanceRecord) {
        val records = (all() + record).takeLast(MAX_RECORDS)
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        preferences.edit().putString(RECORDS_KEY, array.toString()).apply()
    }

    @Synchronized
    fun all(): List<P1AcceptanceRecord> {
        val encoded = preferences.getString(RECORDS_KEY, null) ?: return emptyList()
        val array = runCatching { JSONArray(encoded) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val record = runCatching { item.toRecord() }.getOrNull()
                if (record != null) this.add(record)
            }
        }.takeLast(MAX_RECORDS)
    }

    @Synchronized
    fun clear() {
        preferences.edit().remove(RECORDS_KEY).apply()
    }

    @Synchronized
    fun preset(): P1AcceptancePreset =
        preferences.getString(PRESET_KEY, null)
            ?.let { name -> P1AcceptancePreset.values().firstOrNull { it.name == name } }
            ?: P1AcceptancePreset.DEMO

    @Synchronized
    fun setPreset(preset: P1AcceptancePreset) {
        preferences.edit().putString(PRESET_KEY, preset.name).apply()
    }

    private fun P1AcceptanceRecord.toJson(): JSONObject = JSONObject().apply {
        put("fast60", fast60)
        put("fileSizeBytes", fileSizeBytes)
        put("sha256Verified", sha256Verified)
        put("goodputKibPerSecond", goodputKibPerSecond ?: JSONObject.NULL)
        put("macroblockPassRate", macroblockPassRate ?: JSONObject.NULL)
        put("elapsedMillis", elapsedMillis ?: JSONObject.NULL)
        put("firstMinuteKibPerSecond", firstMinuteKibPerSecond ?: JSONObject.NULL)
        put("lastMinuteKibPerSecond", lastMinuteKibPerSecond ?: JSONObject.NULL)
    }

    private fun JSONObject.toRecord(): P1AcceptanceRecord = P1AcceptanceRecord(
        fast60 = getBoolean("fast60"),
        fileSizeBytes = getLong("fileSizeBytes"),
        sha256Verified = getBoolean("sha256Verified"),
        goodputKibPerSecond = nullableDouble("goodputKibPerSecond"),
        macroblockPassRate = nullableDouble("macroblockPassRate"),
        elapsedMillis = nullableLong("elapsedMillis"),
        firstMinuteKibPerSecond = nullableDouble("firstMinuteKibPerSecond"),
        lastMinuteKibPerSecond = nullableDouble("lastMinuteKibPerSecond")
    )

    private fun JSONObject.nullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return getDouble(key).takeIf(Double::isFinite)
    }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    companion object {
        private const val PREFERENCES_NAME = "optiframe-acceptance"
        private const val RECORDS_KEY = "records"
        private const val PRESET_KEY = "preset"
        private const val MAX_RECORDS = 200
    }
}
