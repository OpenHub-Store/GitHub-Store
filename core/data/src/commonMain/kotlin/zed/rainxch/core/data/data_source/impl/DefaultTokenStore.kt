package zed.rainxch.core.data.data_source.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import kotlin.time.Clock

class DefaultTokenStore(
    private val dataStore: DataStore<Preferences>,
) : TokenStore {
    private val TOKEN_KEY = stringPreferencesKey("token")
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun save(token: GithubDeviceTokenSuccessDto) {
        val stamped =
            token.copy(
                savedAtEpochMillis = token.savedAtEpochMillis ?: Clock.System.now().toEpochMilliseconds(),
            )
        val jsonString = json.encodeToString(GithubDeviceTokenSuccessDto.serializer(), stamped)
        dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = jsonString
        }
    }

    override fun tokenFlow(): Flow<GithubDeviceTokenSuccessDto?> {
        return dataStore.data.map { preferences ->
            val raw = preferences[TOKEN_KEY] ?: return@map null
            runCatching {
                json.decodeFromString(GithubDeviceTokenSuccessDto.serializer(), raw)
            }.getOrNull()
        }
    }

    override suspend fun currentToken(): GithubDeviceTokenSuccessDto? {
        val preferences = dataStore.data.first()
        val raw = preferences[TOKEN_KEY] ?: return null
        return runCatching {
            json.decodeFromString(GithubDeviceTokenSuccessDto.serializer(), raw)
        }.getOrNull()
    }

    override fun blockingCurrentToken(): GithubDeviceTokenSuccessDto? =
        runBlocking {
            val preferences = dataStore.data.first()
            val raw = preferences[TOKEN_KEY] ?: return@runBlocking null
            runCatching {
                json.decodeFromString(GithubDeviceTokenSuccessDto.serializer(), raw)
            }.getOrNull()
        }

    override suspend fun clear() {
        dataStore.edit { it.remove(TOKEN_KEY) }
    }

    override suspend fun isTokenExpired(): Boolean {
        val token = currentToken() ?: return true
        val savedAt = token.savedAtEpochMillis ?: return false
        val expiresIn = token.expiresIn ?: return false
        val expiresAtMillis = savedAt + (expiresIn * 1000L)
        return Clock.System.now().toEpochMilliseconds() > expiresAtMillis
    }
}
