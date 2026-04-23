package com.xadras.app.data.repository

import com.xadras.app.data.api.ApiService
import com.xadras.app.data.local.TokenManager
import com.xadras.app.data.model.LoginRequest
import com.xadras.app.data.model.RegisterRequest
import com.xadras.app.utils.Resource
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório de autenticação.
 *
 * Gere login, registo e sessões de utilizador.
 * O backend utiliza Djoser com autenticação por Token.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenManager: TokenManager
) {

    /**
     * Login — envia credenciais e guarda o token recebido.
     */
    suspend fun login(username: String, password: String): Resource<Unit> {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val body = response.body()!!
                // Djoser devolve auth_token (não access/refresh como JWT)
                tokenManager.saveToken(body.auth_token)
                tokenManager.saveUsername(username)
                Resource.Success(Unit)
            } else {
                Resource.Error("Credenciais inválidas")
            }
        } catch (e: Exception) {
            Resource.Error("Erro de ligação: ${e.message}")
        }
    }

    /**
     * Registo — cria utilizador e faz login automático.
     */
    suspend fun register(username: String, email: String, password: String, re_password: String): Resource<Unit> {
        return try {
            val response = api.register(RegisterRequest(username, email, password, re_password))
            if (response.isSuccessful) {
                // Após registo, fazer login para obter o token
                return login(username, password)
            } else {
                val error = response.errorBody()?.string() ?: "Erro ao registar"
                Resource.Error(error)
            }
        } catch (e: Exception) {
            Resource.Error("Erro de ligação: ${e.message}")
        }
    }

    /**
     * Criar sessão de convidado (guest).
     */
    suspend fun loginAsGuest(): Resource<Unit> {
        return try {
            val response = api.createGuest()
            if (response.isSuccessful) {
                val body = response.body()!!
                tokenManager.saveToken(body.token)
                tokenManager.saveUsername(body.username)
                Resource.Success(Unit)
            } else {
                Resource.Error("Falha ao criar sessão de convidado")
            }
        } catch (e: Exception) {
            Resource.Error("Erro de ligação: ${e.message}")
        }
    }

    /**
     * Logout — limpa tokens locais e invalida no servidor.
     */
    suspend fun logout() {
        try { api.logout() } catch (_: Exception) { }
        tokenManager.clearAll()
    }

    fun isLoggedIn() = tokenManager.isLoggedIn
    fun getUsername() = tokenManager.username
}