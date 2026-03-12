package zed.rainxch.core.data.local.data_store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createDataStore(context: Context): DataStore<Preferences> =
    createDataStore(
        producePath = {
            context.filesDir.resolve(_root_ide_package_.zed.rainxch.core.data.local.data_store.dataStoreFileName).absolutePath
        },
    )
