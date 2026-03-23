package com.xadras.app.data.api

import com.xadras.app.data.model.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Interface Retrofit para comunicação com o backend Django.
 *
 * O backend utiliza Djoser com autenticação por Token.
 * O cabeçalho de autenticação é adicionado automaticamente
 * pelo [AuthInterceptor].
 */
interface ApiService {

    // ─── Autenticação (Djoser Token) ──────────────────────────────────────────

    /** Registar um novo utilizador. */
    @POST("users/")
    suspend fun register(@Body request: RegisterRequest): Response<RegisterResponse>

    /** Login — devolve um token de autenticação. */
    @POST("token/login/")
    suspend fun login(@Body request: LoginRequest): Response<TokenLoginResponse>

    /** Logout — invalida o token atual. */
    @POST("token/logout/")
    suspend fun logout(): Response<Unit>

    /** Criar um utilizador convidado (guest). */
    @POST("accounts/guest/")
    suspend fun createGuest(): Response<GuestResponse>

    /** Eliminar conta de utilizador convidado. */
    @DELETE("accounts/guest/delete/")
    suspend fun deleteGuest(): Response<Unit>

    // ─── Utilizador ───────────────────────────────────────────────────────────

    /** Obter perfil do utilizador autenticado. */
    @GET("users/me/")
    suspend fun getMe(): Response<UserProfile>

    /** Obter estatísticas do utilizador. */
    @GET("accounts/stats/")
    suspend fun getStats(): Response<UserStats>

    // ─── Jogo ─────────────────────────────────────────────────────────────────

    /** Listar jogos do utilizador. */
    @GET("game/")
    suspend fun getGames(): Response<List<GameSummary>>

    /** Obter detalhes de um jogo. */
    @GET("game/{id}/")
    suspend fun getGame(@Path("id") gameId: Int): Response<GameDetail>

    /** Submeter uma jogada. */
    @POST("game/{id}/move/")
    suspend fun submitMove(
        @Path("id") gameId: Int,
        @Body request: MoveRequest
    ): Response<MoveResponse>

    // ─── Live Board — FEN da app do telemóvel ─────────────────────────────────

    /**
     * Enviar FEN detetado pela câmara para o backend.
     * O backend retransmite para o browser ligado à mesma sessão.
     */
    @POST("game/live-board/fen/")
    suspend fun sendFen(@Body request: FenRequest): Response<FenResponse>

    // ─── Torneios ─────────────────────────────────────────────────────────────

    /** Listar torneios disponíveis. */
    @GET("tournaments/")
    suspend fun getTournaments(): Response<List<Tournament>>

    /** Juntar-se a um torneio. */
    @POST("tournaments/{id}/join/")
    suspend fun joinTournament(
        @Path("id") tournamentId: Int,
        @Body request: JoinTournamentRequest
    ): Response<JoinTournamentResponse>

    /** Obter os jogos de um torneio. */
    @GET("tournaments/{id}/games/")
    suspend fun getTournamentGames(@Path("id") tournamentId: Int): Response<List<TournamentGame>>
}