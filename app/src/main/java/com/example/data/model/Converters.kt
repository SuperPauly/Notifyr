package com.example.data.model

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

class Converters {
    @TypeConverter
    fun fromActionItemList(list: List<ActionItem>?): String {
        if (list == null) return "[]"
        val array = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("label", item.label)
            obj.put("kind", item.kind.name)
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toActionItemList(jsonStr: String?): List<ActionItem> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<ActionItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "")
                val label = obj.optString("label", "")
                val kindStr = obj.optString("kind", ActionKind.BUTTON.name)
                val kind = try {
                    ActionKind.valueOf(kindStr)
                } catch (_: Exception) {
                    ActionKind.BUTTON
                }
                list.add(ActionItem(id, label, kind))
            }
        } catch (_: Exception) {
            // gracefully fallback to empty
        }
        return list
    }

    @TypeConverter
    fun fromActionKind(kind: ActionKind?): String {
        return kind?.name ?: ActionKind.BUTTON.name
    }

    @TypeConverter
    fun toActionKind(value: String?): ActionKind {
        return try {
            if (value != null) ActionKind.valueOf(value) else ActionKind.BUTTON
        } catch (_: Exception) {
            ActionKind.BUTTON
        }
    }

    @TypeConverter
    fun fromOutboxStatus(status: OutboxStatus?): String {
        return status?.name ?: OutboxStatus.QUEUED.name
    }

    @TypeConverter
    fun toOutboxStatus(value: String?): OutboxStatus {
        return try {
            if (value != null) OutboxStatus.valueOf(value) else OutboxStatus.QUEUED
        } catch (_: Exception) {
            OutboxStatus.QUEUED
        }
    }
}
