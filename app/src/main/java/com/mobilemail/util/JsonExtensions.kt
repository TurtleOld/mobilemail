package com.mobilemail.util

import org.json.JSONArray
import org.json.JSONObject

fun JSONObject.optStringOrNull(key: String): String? {
    return if (has(key) && !isNull(key)) {
        optString(key, null)
    } else {
        null
    }
}

fun JSONObject.optIntOrNull(key: String): Int? {
    return if (has(key) && !isNull(key)) {
        optInt(key, 0).takeIf { it != 0 }
    } else {
        null
    }
}

fun JSONObject.optBooleanOrNull(key: String): Boolean? {
    return if (has(key) && !isNull(key)) {
        optBoolean(key, false)
    } else {
        null
    }
}

fun JSONObject.optLongOrNull(key: String): Long? {
    return if (has(key) && !isNull(key)) {
        optLong(key, 0L).takeIf { it != 0L }
    } else {
        null
    }
}

fun JSONObject.optJSONObjectOrNull(key: String): JSONObject? {
    return if (has(key) && !isNull(key)) {
        optJSONObject(key)
    } else {
        null
    }
}

fun JSONObject.optJSONArrayOrNull(key: String): JSONArray? {
    return if (has(key) && !isNull(key)) {
        optJSONArray(key)
    } else {
        null
    }
}

fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until length()) {
        list.add(get(i))
    }
    return list
}

fun JSONArray.toStringList(): List<String> {
    val list = mutableListOf<String>()
    for (i in 0 until length()) {
        list.add(getString(i))
    }
    return list
}

fun JSONObject.getStringOrEmpty(key: String): String {
    return optString(key, )
}

fun JSONObject.getIntOrDefault(key: String, default: Int = 0): Int {
    return optInt(key, default)
}

fun JSONObject.getBooleanOrDefault(key: String, default: Boolean = false): Boolean {
    return optBoolean(key, default)
}
