package com.xadras.app.ui.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xadras.app.data.model.Tournament
import com.xadras.app.data.repository.TournamentRepository
import com.xadras.app.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TournamentUiState(
    val tournaments: List<Tournament> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val joinedGameId: Int? = null // Mantido como Int para o ID do Jogo (Game)
)

@HiltViewModel
class TournamentViewModel @Inject constructor(
    private val tournamentRepository: TournamentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TournamentUiState())
    val uiState: StateFlow<TournamentUiState> = _uiState

    init { loadTournaments() }

    fun loadTournaments() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = tournamentRepository.getTournaments()) {
                is Resource.Success -> _uiState.value = _uiState.value.copy(
                    tournaments = result.data, isLoading = false
                )
                is Resource.Error   -> _uiState.value = _uiState.value.copy(
                    error = result.message, isLoading = false
                )
                else -> Unit
            }
        }
    }

    fun joinTournament(tournamentId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            when (val result = tournamentRepository.joinTournament(tournamentId)) {
                is Resource.Success -> _uiState.value = _uiState.value.copy(
                    isLoading = false, joinedGameId = null // O backend atual não devolve game_id imediato
                )
                is Resource.Error   -> _uiState.value = _uiState.value.copy(
                    isLoading = false, error = result.message
                )
                else -> Unit
            }
        }
    }

    fun clearJoinedGame() { _uiState.value = _uiState.value.copy(joinedGameId = null) }
}