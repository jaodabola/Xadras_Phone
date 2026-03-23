package com.xadras.app.data.api

import com.xadras.app.data.local.TokenManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Interceptor que adiciona automaticamente o cabeçalho de autenticação
 * a todos os pedidos HTTP, exceto endpoints públicos.
 *
 * O backend Django utiliza Djoser com autenticação por Token,
 * logo o formato é: "Authorization: Token <token>"
 */
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager
) : Interceptor {

    /** Caminhos que não requerem autenticação. */
    private val noAuthPaths = listOf(
        "auth/users/",             // Registo
        "auth/token/login/",       // Login
        "accounts/guest/",         // Criar guest
    )

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // Saltar cabeçalho de auth para endpoints públicos
        if (noAuthPaths.any { path.contains(it) }) {
            return chain.proceed(request)
        }

        // Obter token armazenado localmente
        val token = runBlocking { tokenManager.accessToken.firstOrNull() }

        val newRequest = if (token != null) {
            request.newBuilder()
                .addHeader("Authorization", "Token $token")
                .build()
        } else request

        return chain.proceed(newRequest)
    }
}