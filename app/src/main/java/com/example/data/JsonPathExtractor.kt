package com.example.data

import org.json.JSONArray
import org.json.JSONObject

object JsonPathExtractor {

    fun extractValue(obj: JSONObject, path: String): String {
        if (path.isBlank()) return ""
        val parts = path.split(".")
        var current: Any? = obj
        for (i in parts.indices) {
            val part = parts[i]
            if (current is JSONObject) {
                if (i == parts.lastIndex) {
                    val raw = current.opt(part)
                    return when (raw) {
                        null, JSONObject.NULL -> ""
                        else -> raw.toString()
                    }
                } else {
                    current = current.opt(part)
                }
            } else {
                return ""
            }
        }
        return ""
    }

    fun extractNestedJsonArray(obj: JSONObject, path: String): JSONArray? {
        val parts = path.split(".")
        var current: Any? = obj
        for (i in parts.indices) {
            val part = parts[i]
            if (current is JSONObject) {
                if (i == parts.lastIndex) {
                    return current.optJSONArray(part)
                } else {
                    current = current.opt(part)
                }
            } else {
                return null
            }
        }
        return null
    }

    fun extractTags(obj: JSONObject, tagsField: String): List<String> {
        if (tagsField.isBlank()) return emptyList()
        val parts = tagsField.split(".")
        var current: Any? = obj
        for (part in parts) {
            if (current is JSONObject) {
                current = current.opt(part)
            } else {
                break
            }
        }

        return when (current) {
            is JSONArray -> {
                val list = mutableListOf<String>()
                for (i in 0 until current.length()) {
                    val item = current.opt(i)
                    if (item is JSONObject) {
                        val name = item.optString("name", item.optString("title", ""))
                        if (name.isNotBlank()) list.add(name)
                    } else if (item != null) {
                        list.add(item.toString())
                    }
                }
                list
            }
            is String -> {
                current.split(",", " ", ";")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
            }
            else -> emptyList()
        }
    }
}
