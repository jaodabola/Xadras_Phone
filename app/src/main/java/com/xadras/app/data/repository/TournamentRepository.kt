package com.xadras.app.data.repository

import com.xadras.app.data.api.ApiService
import com.xadras.app.data.model.JoinTournamentRequest
import com.xadras.app.data.model.JoinTournamentResponse
import com.xadras.app.data.model.Tournament
import com.xadras.app.data.model.TournamentGame
import com.xadras.app.utils.Resource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TournamentRepository @Inject constructor(private val api: ApiService) {

    suspend fun getTournaments(): Resource<List<Tournament>> {
        return try {
            val response = api.getTournaments()
            if (response.isSuccessful) Resource.Success(response.body()!!)
            else Resource.Error("Erro ao carregar torneios: ${response.code()}")
        } catch (e: Exception) {
            Resource.Error("Erro de ligação: ${e.message}")
        }
    }

    suspend fun joinTournament(tournamentId: String): Resource<JoinTournamentResponse> {
        return try {
            val response = api.joinTournament(tournamentId, JoinTournamentRequest(tournamentId))
            if (response.isSuccessful) Resource.Success(response.body()!!)
            else Resource.Error("Não foi possível entrar no torneio")
        } catch (e: Exception) {
            Resource.Error("Erro de ligação: ${e.message}")
        }
    }

    suspend fun getTournamentGames(tournamentId: String): Resource<List<TournamentGame>> {
        return try {
            val response = api.getTournamentGames(tournamentId)
            if (response.isSuccessful) Resource.Success(response.body()!!)
            else Resource.Error("Erro ao carregar jogos")
        } catch (e: Exception) {
            Resource.Error("Erro de ligação: ${e.message}")
        }
    }
}