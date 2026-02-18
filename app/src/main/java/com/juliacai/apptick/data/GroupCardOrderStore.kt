package com.juliacai.apptick.data

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object GroupCardOrderStore {
    const val KEY_GROUP_CARD_ORDER = "group_card_order"

    private val gson = Gson()

    fun readOrder(prefs: SharedPreferences): List<Long> {
        val raw = prefs.getString(KEY_GROUP_CARD_ORDER, null) ?: return emptyList()
        val type = object : TypeToken<List<Long>>() {}.type
        return runCatching { gson.fromJson<List<Long>>(raw, type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    fun writeOrder(prefs: SharedPreferences, orderedIds: List<Long>) {
        prefs.edit {
            if (orderedIds.isEmpty()) {
                remove(KEY_GROUP_CARD_ORDER)
            } else {
                putString(KEY_GROUP_CARD_ORDER, gson.toJson(orderedIds))
            }
        }
    }

    fun sanitizeOrder(savedOrder: List<Long>, availableIds: List<Long>): List<Long> {
        if (availableIds.isEmpty()) return emptyList()

        val availableSet = availableIds.toSet()
        val seen = LinkedHashSet<Long>()
        val result = ArrayList<Long>(availableIds.size)

        savedOrder.forEach { id ->
            if (id in availableSet && seen.add(id)) {
                result.add(id)
            }
        }

        availableIds.forEach { id ->
            if (seen.add(id)) {
                result.add(id)
            }
        }

        return result
    }

    fun <T> applyOrder(items: List<T>, savedOrder: List<Long>, idSelector: (T) -> Long): List<T> {
        if (items.isEmpty()) return emptyList()
        if (savedOrder.isEmpty()) return items

        val byId = items.associateBy(idSelector)
        val ordered = ArrayList<T>(items.size)
        val seen = HashSet<Long>()

        savedOrder.forEach { id ->
            val item = byId[id]
            if (item != null && seen.add(id)) {
                ordered.add(item)
            }
        }

        items.forEach { item ->
            val id = idSelector(item)
            if (seen.add(id)) {
                ordered.add(item)
            }
        }

        return ordered
    }
}
