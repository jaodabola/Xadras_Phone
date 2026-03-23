package com.xadras.app.ui.auth

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
import com.google.android.material.snackbar.Snackbar
import com.xadras.app.R
import com.xadras.app.databinding.FragmentLoginBinding
import com.xadras.app.utils.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeState()
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            viewModel.login(
                binding.etUsername.text.toString().trim(),
                binding.etPassword.text.toString()
            )
        }
        binding.btnGuest.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_camera)
        }
        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { state ->
                    when (state) {
                        is Resource.Loading -> setLoading(true)
                        is Resource.Success -> {
                            setLoading(false)
                            findNavController().navigate(R.id.action_login_to_home)
                            viewModel.resetState()
                        }
                        is Resource.Error -> {
                            setLoading(false)
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            viewModel.resetState()
                        }
                        null -> setLoading(false)
                    }
                }
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.isVisible = loading
        binding.btnLogin.isEnabled = !loading
        binding.btnGuest.isEnabled = !loading
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}