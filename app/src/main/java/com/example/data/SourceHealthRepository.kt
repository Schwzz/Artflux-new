package com.example.data

import android.content.Context
import com.example.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SourceHealthRepository(context: Context) {
    private val prefs = context.getSharedPreferences("artflux_source_health", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()

    private fun read(): Map<String, SourceHealth> = runCatching {
        val root = JSONObject(prefs.getString("status", "{}").orEmpty())
        root.keys().asSequence().mapNotNull { id -> runCatching {
            val o = root.getJSONObject(id)
            id to SourceHealth(SourceCondition.valueOf(o.getString("condition")), o.getLong("checked"),
                o.optLong("success"), o.optLong("retry"), o.optInt("http"))
        }.getOrNull() }.toMap()
    }.getOrDefault(emptyMap())

    fun success(id: String, now: Long = System.currentTimeMillis()) = put(id, SourceHealth(SourceCondition.HEALTHY, now, now))
    fun failure(id: String, error: Throwable, now: Long = System.currentTimeMillis()) {
        val failure = SourceRequestException.from(error)
        put(id, SourceHealth(failure.condition, now, mutable.value[id]?.lastSuccessAt ?: 0, failure.retryAt, failure.httpStatus))
    }
    fun remove(id: String) { mutable.value = mutable.value - id; persist() }
    private fun put(id: String, value: SourceHealth) { mutable.value = mutable.value + (id to value); persist() }
    private fun persist() {
        val root = JSONObject()
        mutable.value.forEach { (id, h) -> root.put(id, JSONObject().put("condition", h.condition.name).put("checked", h.checkedAt)
            .put("success", h.lastSuccessAt).put("retry", h.retryAt).put("http", h.httpStatus)) }
        prefs.edit().putString("status", root.toString()).apply()
    }
}
