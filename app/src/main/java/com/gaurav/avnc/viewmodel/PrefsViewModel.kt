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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gaurav.avnc.model.ServerProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Viewmodel for preferences activity.
 */
class PrefsViewModel(app: Application) : BaseViewModel(app) {

    /**************************************************************************
     * Import/Export
     *
     * Currently we are only exporting server profiles but preferences can be
     * exported in future.
     *
     * Importing/Exporting is done in background and [isDoingIE], [lastIEResult]
     * are used to track the work.
     **************************************************************************/

    /**
     * Container used for packing & serializing data.
     */
    @Serializable
    private data class Backup(
            val version: Int = 1,
            val profiles: List<ServerProfile>
    )

    /**
     * JSON serializer
     */
    private val serializer = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
        prettyPrint = true
    }


    /**
     * Whether any import/export operation is running.
     */
    val isDoingIE = MutableLiveData(false)


    /**
     * Describes the result of last import/export operation.
     * `null` means no result is available yet.
     */
    val lastIEResult = MutableLiveData<String?>(null)

    /**
     * Exports data to given [uri].
     */
    fun export(uri: Uri, exportPasswords: Boolean) = launchIETask {
        //Prepare data
        val profiles = serverProfileDao.getAll()
        if (!exportPasswords)
            profiles.forEach { it.password = "" }

        //Serialize
        val data = Backup(profiles = profiles)
        val json = serializer.encodeToString(data)

        //Write out
        val writer = app.contentResolver.openOutputStream(uri)?.writer()
        writer?.write(json)
        writer?.close()

        lastIEResult.postValue("Exported successfully!")
    }

    /**
     * Imports data from given [uri].
     */
    fun import(uri: Uri, deleteCurrentServers: Boolean) = launchIETask {
        //Read JSON
        val reader = app.contentResolver.openInputStream(uri)?.reader()
        val json = reader?.readText()
        reader?.close()

        //Deserialize
        if (json == null) return@launchIETask
        val data = serializer.decodeFromString<Backup>(json)

        //This is where migrations would be applied (if required in future)

        //Update database
        if (deleteCurrentServers) {
            serverProfileDao.overwriteTable(data.profiles)
        } else {
            //Remove IDs so that they don't conflict with saved profiles
            data.profiles.forEach { it.ID = 0 }
            serverProfileDao.insert(data.profiles)
        }

        lastIEResult.postValue("Imported successfully!")
    }

    /**
     * Executes given import/export task in background and updates state accordingly.
     */
    private fun launchIETask(task: () -> Unit) {
        if (isDoingIE.value == true)
            return

        isDoingIE.value = true
        lastIEResult.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                task()
            } catch (e: Throwable) {
                lastIEResult.postValue("Task failed: ${e.message}")
                Log.e("ImportExport", "Task failed", e)
            } finally {
                isDoingIE.postValue(false)
            }
        }
    }
}