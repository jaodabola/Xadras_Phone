package com.xadras.app.ui.tournament

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.xadras.app.R
import com.xadras.app.databinding.FragmentTournamentBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TournamentFragment : Fragment() {

    private var _binding: FragmentTournamentBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TournamentViewModel by viewModels()
    private lateinit var adapter: TournamentAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTournamentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeState()

        binding.swipeRefresh.setOnRefreshListener { viewModel.loadTournaments() }
    }

    private fun setupRecyclerView() {
        adapter = TournamentAdapter { tournament ->
            viewModel.joinTournament(tournament.id)
        }
        binding.rvTournaments.adapter = adapter
        binding.rvTournaments.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.isVisible = state.isLoading
                    binding.swipeRefresh.isRefreshing = false
                    adapter.submitList(state.tournaments)
                    binding.tvEmpty.isVisible = state.tournaments.isEmpty() && !state.isLoading

                    state.error?.let {
                        Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                    }

                    // When join succeeds, show message
                    state.joinedGameId?.let { gameId ->
                        Snackbar.make(binding.root, "Inscrito com sucesso! Use o site para jogar.", Snackbar.LENGTH_LONG).show()
                        viewModel.clearJoinedGame()
                    }
                }
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}