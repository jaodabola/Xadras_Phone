package com.xadras.app.ui.tournament

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.xadras.app.data.model.Tournament
import com.xadras.app.databinding.ItemTournamentBinding

class TournamentAdapter(
    private val onJoin: (Tournament) -> Unit
) : ListAdapter<Tournament, TournamentAdapter.ViewHolder>(DiffCallback) {

    inner class ViewHolder(private val binding: ItemTournamentBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(tournament: Tournament) {
            binding.tvName.text = tournament.name
            binding.tvDescription.text = tournament.description
            binding.tvStatus.text = when (tournament.status) {
                "upcoming" -> "🕐 Em breve"
                "active"   -> "🟢 A decorrer"
                "finished" -> "🏁 Terminado"
                else       -> tournament.status
            }
            binding.tvParticipants.text = buildString {
                append("${tournament.participant_count}")
                tournament.max_participants?.let { append("/$it") }
                append(" jogadores")
            }
            binding.btnJoin.isEnabled = tournament.status == "active" || tournament.status == "upcoming"
            binding.btnJoin.setOnClickListener { onJoin(tournament) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTournamentBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private object DiffCallback : DiffUtil.ItemCallback<Tournament>() {
        override fun areItemsTheSame(a: Tournament, b: Tournament) = a.id == b.id
        override fun areContentsTheSame(a: Tournament, b: Tournament) = a == b
    }
}