package zed.rainxch.core.data.local.data_store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

fun createDataStore(): DataStore<Preferences> =
    createDataStore(
        producePath = {
            val file = File(System.getProperty("java.io.tmpdir"), dataStoreFileName)
            file.absolutePath
        },
    )
