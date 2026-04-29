package com.xadras.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
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
import org.opencv.android.OpenCVLoader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fragment de câmara para deteção de tabuleiro de xadrez.
 *
 * Apresenta um loading screen enquanto o modelo YOLO carrega,
 * depois inicia a deteção de tabuleiro e tracking de FEN.
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

        // Inicializar OpenCV de forma nativa
        if (OpenCVLoader.initDebug()) {
            Log.i("OpenCV", "OpenCV carregado com sucesso!")
        } else {
            Log.e("OpenCV", "Falha ao carregar OpenCV nativo.")
        }

        // Mostrar loading overlay inicialmente
        binding.loadingOverlay.visibility = View.VISIBLE
        binding.cardControls.visibility = View.GONE
        binding.cardStatus.visibility = View.GONE

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

            // Pré-visualização do vídeo (forçar 4:3 usando ResolutionSelector modernizado)
            val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                .setAspectRatioStrategy(androidx.camera.core.resolutionselector.AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            // Análise de frames para deteção de peças (forçar 4:3)
            imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    var lastAnalyzeTime = 0L

                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val currentTime = System.currentTimeMillis()
                        
                        // Capturar e processar a 4 Frames Por Segundo (250 ms)
                        // Isto é rápido o suficiente para parecer instantâneo na web, 
                        // mas muito mais brando que 30 FPS para poupar bateria e não aquecer.
                        if (currentTime - lastAnalyzeTime >= 100) {
                            lastAnalyzeTime = currentTime
                            
                            val rawBitmap = imageProxy.toBitmap()
                            val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()

                            val bitmap = if (rotation != 0f) {
                                val matrix = android.graphics.Matrix()
                                matrix.postRotate(rotation)
                                android.graphics.Bitmap.createBitmap(
                                    rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true
                                )
                            } else {
                                rawBitmap
                            }

                            viewModel.processFrame(bitmap)
                        }
                        
                        // Fundamental libertar a frame mesmo se for ignorada (para a câmara continuar "live")
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

        // Botão de reset (novo jogo)
        binding.btnResetTracker.setOnClickListener {
            viewModel.resetTracker()
            Snackbar.make(
                binding.root,
                "Posição reiniciada — novo jogo",
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->

                    // ── Loading overlay ──
                    if (!state.modelReady) {
                        binding.loadingOverlay.isVisible = true
                        binding.cardControls.isVisible = false
                        binding.cardStatus.isVisible = false
                        binding.tvLoadingStatus.text = state.loadingMessage
                        return@collect
                    }

                    // Modelo pronto — esconder loading com fade-out
                    if (binding.loadingOverlay.isVisible) {
                        hideLoadingOverlay()
                    }

                    // ── Status bar ──
                    binding.cardStatus.isVisible = true
                    binding.cardControls.isVisible = true

                    binding.tvStatus.text = state.statusMessage
                    binding.tvFen.text = when {
                        state.currentFen.isNotEmpty() -> state.currentFen
                        state.boardDetected -> "Tabuleiro detetado (${state.squaresDetected} quadrados)"
                        else -> "Aguardando deteção..."
                    }

                    // Indicador de confiança
                    binding.tvConfidence.text = if (state.confidence > 0f)
                        "%.0f%%".format(state.confidence * 100) else ""

                    // Tracker status (jogada, turno)
                    if (state.isTracking && state.trackerStatus.isNotEmpty()) {
                        binding.tvTrackerStatus.isVisible = true
                        binding.tvTrackerStatus.text = "♟ ${state.trackerStatus}"
                    } else {
                        binding.tvTrackerStatus.isVisible = false
                    }

                    // Indicador de ligação
                    binding.indicatorConnected.isVisible = state.isConnected

                    // Botão de transmissão
                    binding.btnToggleBroadcast.text = if (state.isBroadcasting)
                        "⏹ Parar transmissão" else "▶ Transmitir"

                    // Campo do código de sessão
                    binding.etSessionCode.isVisible = !state.isBroadcasting
                    binding.tvSessionLabel.isVisible = !state.isBroadcasting

                    // Overlay AR e Limpeza de Lados
                    if (state.boardCorners != null) {
                        binding.overlayView.updateCorners(
                            state.boardCorners,
                            state.imageWidth,
                            state.imageHeight
                        )
                    } else {
                        binding.overlayView.clear()
                    }
                }
            }
        }
    }

    /**
     * Esconder o loading overlay com uma animação de fade-out suave.
     */
    private fun hideLoadingOverlay() {
        val fadeOut = AlphaAnimation(1f, 0f).apply {
            duration = 400
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    _binding?.loadingOverlay?.visibility = View.GONE
                }
            })
        }
        binding.loadingOverlay.startAnimation(fadeOut)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}