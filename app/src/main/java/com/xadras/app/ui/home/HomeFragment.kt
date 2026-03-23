package com.xadras.app.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.xadras.app.R
import com.xadras.app.databinding.FragmentHomeBinding
import com.xadras.app.ui.auth.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.username.collect { name ->
                    binding.tvWelcome.text = if (name != null) "Olá, $name" else "Bem-vindo"
                }
            }
        }

        binding.btnCamera.setOnClickListener {
            // Local mode: no broadcast, no gameId
            findNavController().navigate(
                HomeFragmentDirections.actionHomeToCamera(gameId = -1, broadcast = false)
            )
        }

        binding.btnTournaments.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_tournament)
        }

        binding.btnLogout.setOnClickListener {
            authViewModel.logout()
            findNavController().navigate(R.id.action_home_to_login)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}