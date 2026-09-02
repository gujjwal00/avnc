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
import com.gaurav.avnc.R
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.LiveEvent
import com.gaurav.avnc.util.debugCheck
import com.gaurav.avnc.util.isTrue
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
     * Importing/Exporting is done on a background thread.
     **************************************************************************/

    @Serializable
    private data class Container(
            val version: Int = 1,
            var profiles: List<ServerProfile>,
            var preferences: Map<String, JsonElement> = emptyMap()
    )

    private val serializer = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    val exportSettings = MutableLiveData(true)
    val exportProfiles = MutableLiveData(true)
    val exportSecrets = MutableLiveData(false)
    val deleteCurrentServerBeforeImport = MutableLiveData(false)

    val importExportFinishedEvent = LiveEvent<Result<String>>()

    /**
     * Exports data to given [uri].
     */
    fun export(uri: Uri) {
        val exportSettings = exportSettings.isTrue
        val exportProfiles = exportProfiles.isTrue
        val exportSecrets = exportSecrets.isTrue && exportProfiles
        debugCheck(exportSettings || exportProfiles)

        launchIO {
            runCatching {
                // Serialize
                val data = Container(
                        profiles = if (exportProfiles) serverProfileDao.getList() else emptyList(),
                        preferences = if (exportSettings) collectPreferences() else emptyMap()
                )

                if (!exportSecrets)
                    scrubSecrets(data.profiles)

                val json = serializer.encodeToString(data)

                // Write out
                app.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.writer().use { it.write(json) }
                } ?: throw IOException("Unable to write the file.")

                return@runCatching app.getString(R.string.msg_exported)
            }.let {
                importExportFinishedEvent.fireAsync(it)
            }
        }
    }


    /**
     * Imports data from given [uri].
     */
    fun import(uri: Uri) {
        val deleteCurrentServers = deleteCurrentServerBeforeImport.isTrue

        launchIO {
            runCatching {

                val json = app.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.reader().use { it.readText() }
                } ?: throw IOException("Unable to read the file.")

                // Deserialize
                val data = serializer.decodeFromString<Container>(json)

                //This is where migrations would be applied (if required in future)

                //Update database
                if (data.profiles.isNotEmpty()) {
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
                }

                // Replay app preferences (best-effort, unknown keys are ignored)
                applyPreferences(data.preferences)

                return@runCatching app.getString(R.string.msg_imported)
            }.let {
                importExportFinishedEvent.fireAsync(it)
            }
        }
    }

    /**
     * Reads all preferences as typed [JsonElement]s.
     */
    private fun collectPreferences(): Map<String, JsonElement> {
        val all = PreferenceManager.getDefaultSharedPreferences(app).all
        val map = mutableMapOf<String, JsonElement>()
        for ((key, value) in all) {
            if (key.startsWith("run_info_"))
                continue

            when (value) {
                null -> Log.w("PrefsViewModel", "Skipping null preference: $key")
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
     * Imports preferences from JSON
     */
    private fun applyPreferences(prefs: Map<String, JsonElement>) {
        PreferenceManager.getDefaultSharedPreferences(app).edit {
            for ((key, element) in prefs) {
                when (element) {
                    is JsonPrimitive if element.isString ->
                        putString(key, element.content)
                    is JsonPrimitive -> {
                        val content = element.content
                        when {
                            content == "true" || content == "false" -> putBoolean(key, content.toBoolean())
                            content.toIntOrNull() != null -> putInt(key, content.toInt())
                            content.toFloatOrNull() != null -> putFloat(key, content.toFloat())
                            content.toLongOrNull() != null -> putLong(key, content.toLong())
                            else -> Log.w("PrefsViewModel", "Ignoring unknown preference: $key")
                        }
                    }
                    is JsonArray -> putStringSet(key, element.mapNotNull { (it as? JsonPrimitive)?.content }.toSet())
                    else -> Log.w("PrefsViewModel", "Ignoring unknown preference: $key")
                }
            }
        }
    }

    private fun scrubSecrets(profiles: List<ServerProfile>) {
        profiles.forEach {
            it.password = ""
            it.sshPassword = ""
            it.sshPrivateKey = ""
        }
    }
}