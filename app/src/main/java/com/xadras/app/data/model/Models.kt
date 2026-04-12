package com.xadras.app.data.model

// ─── Autenticação ────────────────────────────────────────────────────────────

/** Pedido de login (Djoser Token). */
data class LoginRequest(
    val username: String,
    val password: String
)

/** Resposta de login — contém o token de autenticação. */
data class TokenLoginResponse(
    val auth_token: String
)

/** Pedido de registo de utilizador. */
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String
)

/** Resposta de registo — dados do utilizador criado. */
data class RegisterResponse(
    val id: Int,
    val username: String,
    val email: String
)

/** Resposta ao criar um utilizador convidado (guest). */
data class GuestResponse(
    val username: String,
    val token: String,
    val is_guest: Boolean
)

// ─── Utilizador ──────────────────────────────────────────────────────────────

/** Perfil básico do utilizador. */
data class UserProfile(
    val id: Int,
    val username: String,
    val email: String? = null,
    val elo_rating: Int = 1200,
    val games_played: Int = 0,
    val games_won: Int = 0,
    val games_lost: Int = 0,
    val games_drawn: Int = 0
)

/** Estatísticas detalhadas do utilizador. */
data class UserStats(
    val elo_rating: Int,
    val games_played: Int,
    val games_won: Int,
    val games_lost: Int,
    val games_drawn: Int,
    val win_rate: Double,
    val draw_rate: Double
)

// ─── Live Board (câmara → backend → browser) ─────────────────────────────────

/**
 * Pedido para enviar FEN detetado pela câmara.
 * O campo session_id identifica a sessão do browser que vai receber o FEN.
 */
data class FenRequest(
    val fen: String,
    val session_id: String
)

/** Resposta do endpoint Live Board FEN. */
data class FenResponse(
    val mensagem: String? = null,
    val fen: String? = null,
    val session_id: String? = null,
    val erro: String? = null
)

// ─── Jogo ────────────────────────────────────────────────────────────────────

/** Resumo de um jogo (para listagens). */
data class GameSummary(
    val id: Int,
    val white_player: PlayerInfo?,
    val black_player: PlayerInfo?,
    val status: String,
    val result: String? = null
)

/** Informação básica de um jogador. */
data class PlayerInfo(
    val id: Int,
    val username: String
)

/** Detalhes completos de um jogo. */
data class GameDetail(
    val id: Int,
    val white_player: PlayerInfo?,
    val black_player: PlayerInfo?,
    val status: String,
    val result: String? = null,
    val fen_string: String?,
    val moves: List<MoveInfo>?
)

/** Informação de uma jogada. */
data class MoveInfo(
    val move_number: Int,
    val move_san: String,
    val fen_after: String
)

/** Pedido para submeter uma jogada. */
data class MoveRequest(
    val move_san: String,
    val fen_after: String
)

/** Resposta ao submeter uma jogada. */
data class MoveResponse(
    val id: Int,
    val move_number: Int,
    val move_san: String,
    val fen_after: String
)

// ─── Torneios ────────────────────────────────────────────────────────────────

/** Informação de um torneio. */
data class Tournament(
    val id: String,
    val name: String,
    val description: String,
    val status: String,
    val participant_count: Int,
    val max_participants: Int?,
    val is_participant: Boolean = false,
    val is_joined: Boolean = false
)

/** Pedido para juntar-se a um torneio. */
data class JoinTournamentRequest(val tournament_id: String)

/** Resposta ao juntar-se a um torneio. */
data class JoinTournamentResponse(
    val success: Boolean,
    val game_id: Int?,
    val message: String?
)

/** Informação de um jogo num torneio. */
data class TournamentGame(
    val id: Int,
    val white_player: PlayerInfo?,
    val black_player: PlayerInfo?,
    val status: String,
    val result: String? = null
)