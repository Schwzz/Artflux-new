package com.example.model

import org.json.JSONArray
import org.json.JSONObject

data class ArtCollection(
    val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val mediaIds: List<String> = emptyList()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("createdAt", createdAt)
        val arr = JSONArray()
        for (mId in mediaIds) {
            arr.put(mId)
        }
        obj.put("mediaIds", arr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): ArtCollection {
            val ids = mutableListOf<String>()
            val arr = obj.optJSONArray("mediaIds")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val mId = arr.getString(i)
                    if (mId !in ids) {
                        ids.add(mId)
                    }
                }
            }
            return ArtCollection(
                id = obj.getString("id"),
                name = obj.getString("name"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                mediaIds = ids
            )
        }
    }
}
