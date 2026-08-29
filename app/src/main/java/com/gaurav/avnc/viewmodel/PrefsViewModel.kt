/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import androidx.room.withTransaction
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.LiveEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.io.IOException

/**
 * Viewmodel for preferences activity.
 */
class PrefsViewModel(app: Application) : BaseViewModel(app) {


    /**************************************************************************
     * Import/Export
     *
     * Currently, we are only exporting server profiles but preferences can be
     * exported in the future.
     *
     * Importing/Exporting is done on a background thread.
     **************************************************************************/

    @Serializable
    private data class Container(
            val version: Int = 2,
            val profiles: List<ServerProfile> = emptyList(),
            val preferences: Map<String, JsonElement> = emptyMap()
    )

    private val serializer = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val importFinishedEvent = LiveEvent<Boolean>()
    val exportFinishedEvent = LiveEvent<Boolean>()
    val prefsExportFinishedEvent = LiveEvent<Boolean>()
    var importExportError = MutableLiveData<String>()


    /**
     * Exports data to given [uri].
     *
     * Includes both server profiles and the current app preferences (so the whole
     * configuration can be restored by importing the file back).
     */
    fun export(uri: Uri, exportSecrets: Boolean) {
        launchIO {
            runCatching {
                // Serialize
                val profiles = serverProfileDao.getList()
                if (!exportSecrets) scrubSecrets(profiles)
                val data = Container(profiles = profiles, preferences = collectPreferences())
                val json = serializer.encodeToString(data)

                // Write out
                app.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.writer().use { it.write(json) }
                } ?: throw IOException("Unable to write the file.")

            }.let {
                importExportError.postValue(it.exceptionOrNull()?.message)
                exportFinishedEvent.fireAsync(it.isSuccess)
            }
        }
    }

    /**
     * Exports only the app preferences as a simple XML file.
     * Server profiles are not included in this variant.
     */
    fun exportPrefs(uri: Uri) {
        launchIO {
            runCatching {
                val xml = buildPreferencesXml()
                app.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.writer().use { it.write(xml) }
                } ?: throw IOException("Unable to write the file.")
            }.let {
                importExportError.postValue(it.exceptionOrNull()?.message)
                prefsExportFinishedEvent.fireAsync(it.isSuccess)
            }
        }
    }


    /**
     * Imports data from given [uri].
     */
    fun import(uri: Uri, deleteCurrentServers: Boolean) {
        launchIO {
            runCatching {

                val json = app.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.reader().use { it.readText() }
                } ?: throw IOException("Unable to read the file.")

                // Deserialize
                val data = serializer.decodeFromString<Container>(json)

                //This is where migrations would be applied (if required in future)

                // Replay app preferences (best-effort, unknown keys are ignored)
                applyPreferences(data.preferences)

                //Update database
                if (deleteCurrentServers) {
                    db.withTransaction {
                        serverProfileDao.deleteAll()
                        serverProfileDao.save(data.profiles)
                    }
                } else {
                    //Reset IDs so that they don't conflict with saved profiles
                    data.profiles.forEach { it.ID = 0 }
                    serverProfileDao.save(data.profiles)
                }

            }.let {
                importExportError.postValue(it.exceptionOrNull()?.message)
                importFinishedEvent.fireAsync(it.isSuccess)
            }
        }
    }

    /**
     * Reads all default preferences as typed [JsonElement]s.
     */
    private fun collectPreferences(): Map<String, JsonElement> {
        val all = PreferenceManager.getDefaultSharedPreferences(app).all
        val map = mutableMapOf<String, JsonElement>()
        for ((key, value) in all) {
            when (value) {
                is String -> map[key] = JsonPrimitive(value)
                is Boolean -> map[key] = JsonPrimitive(value)
                is Int -> map[key] = JsonPrimitive(value)
                is Float -> map[key] = JsonPrimitive(value)
                is Long -> map[key] = JsonPrimitive(value)
                is Set<*> -> if (value.all { it is String })
                    map[key] = JsonArray(value.filterIsInstance<String>().map { JsonPrimitive(it) })
                else -> Log.w("PrefsViewModel", "Skipping unsupported preference type for key: $key")
            }
        }
        return map
    }

    /**
     * Replays exported preferences into the default [SharedPreferences].
     * Each value is written back with its original type; keys that cannot be
     * reconstructed are skipped (and logged).
     */
    private fun applyPreferences(prefs: Map<String, JsonElement>) {
        if (prefs.isEmpty()) return
        PreferenceManager.getDefaultSharedPreferences(app).edit().apply {
            for ((key, element) in prefs) {
                when {
                    element is JsonPrimitive && element.isString ->
                        putString(key, element.content)
                    element is JsonPrimitive -> {
                        val content = element.content
                        when {
                            content == "true" || content == "false" -> putBoolean(key, content.toBoolean())
                            content.toIntOrNull() != null -> putInt(key, content.toInt())
                            content.toLongOrNull() != null -> putLong(key, content.toLong())
                            content.toFloatOrNull() != null -> putFloat(key, content.toFloat())
                            else -> Log.w("PrefsViewModel", "Ignoring unknown preference: $key")
                        }
                    }
                    element is JsonArray ->
                        putStringSet(key, element.mapNotNull { (it as? JsonPrimitive)?.content }.toSet())
                    else -> Log.w("PrefsViewModel", "Ignoring unknown preference: $key")
                }
            }
        }.apply()
    }

    /**
     * Builds a simple, human-readable XML document of all preferences.
     */
    private fun buildPreferencesXml(): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<avnc-preferences>\n")
        for ((key, value) in PreferenceManager.getDefaultSharedPreferences(app).all) {
            when (value) {
                is String -> sb.append("  <entry key=\"$key\" type=\"string\">${escapeXml(value)}</entry>\n")
                is Boolean -> sb.append("  <entry key=\"$key\" type=\"boolean\">$value</entry>\n")
                is Int -> sb.append("  <entry key=\"$key\" type=\"int\">$value</entry>\n")
                is Float -> sb.append("  <entry key=\"$key\" type=\"float\">$value</entry>\n")
                is Long -> sb.append("  <entry key=\"$key\" type=\"long\">$value</entry>\n")
                is Set<*> -> if (value.all { it is String }) {
                    sb.append("  <entry key=\"$key\" type=\"string-set\">\n")
                    value.filterIsInstance<String>().forEach { sb.append("    <item>${escapeXml(it)}</item>\n") }
                    sb.append("  </entry>\n")
                }
            }
        }
        sb.append("</avnc-preferences>\n")
        return sb.toString()
    }

    private fun escapeXml(value: String): String =
            value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")

    private fun scrubSecrets(profiles: List<ServerProfile>) {
        profiles.forEach {
            it.password = ""
            it.sshPassword = ""
            it.sshPrivateKey = ""
        }
    }
}