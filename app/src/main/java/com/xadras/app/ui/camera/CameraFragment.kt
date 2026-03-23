package com.xadras.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.xadras.app.databinding.FragmentCameraBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fragment de câmara para deteção de tabuleiro de xadrez.
 *
 * Utiliza CameraX para captura de frames e o [CameraViewModel]
 * para inferência e transmissão do FEN para o backend.
 *
 * O utilizador introduz o código de sessão de 6 caracteres
 * mostrado no browser para iniciar a transmissão.
 */
@AndroidEntryPoint
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: CameraViewModel by viewModels()

    private lateinit var cameraExecutor: ExecutorService
    private var imageAnalysis: ImageAnalysis? = null

    // ─── Permissão da câmara ──────────────────────────────────────────────────

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else Snackbar.make(binding.root, "Permissão de câmara necessária", Snackbar.LENGTH_LONG).show()
    }

    // ─── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        checkCameraPermission()
        setupButtons()
        observeUiState()

        // Iniciar em modo local por defeito
        viewModel.startLocalView()
    }

    // ─── Configuração da câmara ───────────────────────────────────────────────

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Pré-visualização do vídeo
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // Análise de frames para deteção de peças
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val bitmap = imageProxy.toBitmap()
                        viewModel.processFrame(bitmap)
                        imageProxy.close()
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("CameraFragment", "Falha ao ligar câmara: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // ─── Interface ────────────────────────────────────────────────────────────

    private fun setupButtons() {
        // Botão de iniciar/parar transmissão
        binding.btnToggleBroadcast.setOnClickListener {
            val state = viewModel.uiState.value
            if (state.isBroadcasting) {
                viewModel.stopBroadcast()
            } else {
                // Obter o código de sessão introduzido pelo utilizador
                val sessionCode = binding.etSessionCode.text.toString().trim().uppercase()
                if (sessionCode.length == 6) {
                    viewModel.startBroadcast(sessionCode)
                } else {
                    Snackbar.make(
                        binding.root,
                        "Introduza o código de sessão de 6 caracteres do browser",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    // Atualizar textos de estado
                    binding.tvStatus.text = state.statusMessage
                    binding.tvFen.text = if (state.currentFen.isNotEmpty())
                        state.currentFen else "Aguardando deteção..."

                    // Indicador de confiança
                    binding.tvConfidence.text = if (state.confidence > 0f)
                        "%.0f%%".format(state.confidence * 100) else ""

                    // Indicador de ligação
                    binding.indicatorConnected.isVisible = state.isConnected

                    // Botão de transmissão — texto e visibilidade
                    binding.btnToggleBroadcast.text = if (state.isBroadcasting)
                        "⏹ Parar transmissão" else "▶ Transmitir"

                    // Campo do código de sessão — ocultar quando já está a transmitir
                    binding.etSessionCode.isVisible = !state.isBroadcasting
                    binding.tvSessionLabel.isVisible = !state.isBroadcasting
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}