package com.xadras.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "xadras_prefs")

/**
 * Gestor de tokens de autenticação.
 *
 * Armazena o token Djoser e o nome de utilizador
 * localmente usando DataStore Preferences.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("auth_token")
        private val KEY_USER  = stringPreferencesKey("username")
    }

    /** Token de autenticação armazenado localmente. */
    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }

    /** Nome do utilizador. */
    val username: Flow<String?> = context.dataStore.data.map { it[KEY_USER] }

    /** Indica se o utilizador está autenticado. */
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[KEY_TOKEN] != null }

    /** Guardar o token de autenticação. */
    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[KEY_TOKEN] = token }
    }

    /** Guardar o nome de utilizador. */
    suspend fun saveUsername(name: String) {
        context.dataStore.edit { it[KEY_USER] = name }
    }

    /** Limpar todos os dados armazenados (logout). */
    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}