package com.example.data

import android.content.Context
import com.example.model.ArtCollection
import com.example.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class SavesRepository(context: Context) {
    private val prefs = context.getSharedPreferences("artflux_saves_store", Context.MODE_PRIVATE)

    private val _savedItemsMap = MutableStateFlow<Map<String, MediaItem>>(emptyMap())
    val savedItemsMap: StateFlow<Map<String, MediaItem>> = _savedItemsMap.asStateFlow()

    private val _savedItems = MutableStateFlow<List<MediaItem>>(emptyList())
    val savedItems: StateFlow<List<MediaItem>> = _savedItems.asStateFlow()

    private val _savedIds = MutableStateFlow<Set<String>>(emptySet())
    val savedIds: StateFlow<Set<String>> = _savedIds.asStateFlow()

    private val _collections = MutableStateFlow<List<ArtCollection>>(emptyList())
    val collections: StateFlow<List<ArtCollection>> = _collections.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val savedJson = prefs.getString("saved_artworks_json", null)
        val itemsMap = mutableMapOf<String, MediaItem>()
        if (!savedJson.isNullOrBlank()) {
            try {
                val array = JSONArray(savedJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val item = MediaItem.fromJson(obj)
                    itemsMap[item.id] = item
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _savedItemsMap.value = itemsMap
        _savedItems.value = itemsMap.values.toList().reversed()
        _savedIds.value = itemsMap.keys.toSet()

        val colJson = prefs.getString("art_collections_json", null)
        val colsList = mutableListOf<ArtCollection>()
        if (!colJson.isNullOrBlank()) {
            try {
                val array = JSONArray(colJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    colsList.add(ArtCollection.fromJson(obj))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _collections.value = colsList
    }

    private fun persistItems() {
        val array = JSONArray()
        for (item in _savedItemsMap.value.values) {
            array.put(item.toJson())
        }
        prefs.edit().putString("saved_artworks_json", array.toString()).apply()
        _savedItems.value = _savedItemsMap.value.values.toList().reversed()
        _savedIds.value = _savedItemsMap.value.keys.toSet()
    }

    private fun persistCollections() {
        val array = JSONArray()
        for (col in _collections.value) {
            array.put(col.toJson())
        }
        prefs.edit().putString("art_collections_json", array.toString()).apply()
    }

    private var lastUnsavedItem: MediaItem? = null
    private var lastUnsavedCollections: List<String> = emptyList()

    fun isSaved(mediaId: String): Boolean {
        return _savedIds.value.contains(mediaId)
    }

    fun saveItem(item: MediaItem) {
        val currentMap = _savedItemsMap.value.toMutableMap()
        currentMap[item.id] = item
        _savedItemsMap.value = currentMap
        persistItems()
    }

    fun unsaveItem(mediaId: String) {
        val item = _savedItemsMap.value[mediaId]
        if (item != null) {
            lastUnsavedItem = item
            lastUnsavedCollections = _collections.value.filter { mediaId in it.mediaIds }.map { it.id }
        }

        val currentMap = _savedItemsMap.value.toMutableMap()
        currentMap.remove(mediaId)
        _savedItemsMap.value = currentMap
        persistItems()

        val updatedCols = _collections.value.map { col ->
            if (mediaId in col.mediaIds) {
                col.copy(mediaIds = col.mediaIds.filter { it != mediaId })
            } else {
                col
            }
        }
        _collections.value = updatedCols
        persistCollections()
    }

    fun undoLastUnsave() {
        val item = lastUnsavedItem ?: return
        saveItem(item)
        val collectionIds = lastUnsavedCollections
        if (collectionIds.isNotEmpty()) {
            val updated = _collections.value.map { col ->
                if (col.id in collectionIds && item.id !in col.mediaIds) {
                    col.copy(mediaIds = col.mediaIds + item.id)
                } else col
            }
            _collections.value = updated
            persistCollections()
        }
        lastUnsavedItem = null
        lastUnsavedCollections = emptyList()
    }

    fun toggleSave(item: MediaItem) {
        if (isSaved(item.id)) {
            unsaveItem(item.id)
        } else {
            saveItem(item)
        }
    }

    // --- COLLECTION CRUD ---

    fun createCollection(name: String): ArtCollection {
        val cleanName = name.trim().ifBlank { "Untitled Collection" }
        val newCol = ArtCollection(
            id = "col_${System.currentTimeMillis()}_${(1000..9999).random()}",
            name = cleanName
        )
        val currentCols = _collections.value.toMutableList()
        currentCols.add(0, newCol)
        _collections.value = currentCols
        persistCollections()
        return newCol
    }

    fun renameCollection(collectionId: String, newName: String) {
        val cleanName = newName.trim().ifBlank { "Untitled Collection" }
        val updated = _collections.value.map { col ->
            if (col.id == collectionId) col.copy(name = cleanName) else col
        }
        _collections.value = updated
        persistCollections()
    }

    fun deleteCollection(collectionId: String) {
        val updated = _collections.value.filter { it.id != collectionId }
        _collections.value = updated
        persistCollections()
    }

    fun addMediaToCollection(collectionId: String, mediaItem: MediaItem) {
        if (!isSaved(mediaItem.id)) {
            saveItem(mediaItem)
        }

        val updated = _collections.value.map { col ->
            if (col.id == collectionId) {
                if (mediaItem.id !in col.mediaIds) {
                    col.copy(mediaIds = col.mediaIds + mediaItem.id)
                } else col
            } else col
        }
        _collections.value = updated
        persistCollections()
    }

    fun removeMediaFromCollection(collectionId: String, mediaId: String) {
        val updated = _collections.value.map { col ->
            if (col.id == collectionId) {
                col.copy(mediaIds = col.mediaIds.filter { it != mediaId })
            } else col
        }
        _collections.value = updated
        persistCollections()
    }

    fun toggleMediaInCollection(collectionId: String, mediaItem: MediaItem) {
        val col = _collections.value.firstOrNull { it.id == collectionId } ?: return
        if (mediaItem.id in col.mediaIds) {
            removeMediaFromCollection(collectionId, mediaItem.id)
        } else {
            addMediaToCollection(collectionId, mediaItem)
        }
    }

    fun getCollectionsForMedia(mediaId: String): List<ArtCollection> {
        return _collections.value.filter { mediaId in it.mediaIds }
    }

    fun getMediaForCollection(collectionId: String): List<MediaItem> {
        val col = _collections.value.firstOrNull { it.id == collectionId } ?: return emptyList()
        val itemsMap = _savedItemsMap.value
        return col.mediaIds.mapNotNull { itemsMap[it] }
    }
}
