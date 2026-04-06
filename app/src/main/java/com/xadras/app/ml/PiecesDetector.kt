// package com.xadras.app.ml

// import android.content.Context
// import android.graphics.Bitmap
// import android.util.Log
// import org.tensorflow.lite.Interpreter
// import org.tensorflow.lite.gpu.CompatibilityList
// import org.tensorflow.lite.gpu.GpuDelegate
// import java.io.FileInputStream
// import java.nio.ByteBuffer
// import java.nio.ByteOrder
// import java.nio.channels.FileChannel

// /**
//  * Detetor de Peças YOLO (Placeholder).
//  *
//  * Modelo que classificará duas coisas: Peça Branca (0) e Peça Preta (1).
//  * Espera receber a Imagem "TopDown" de 560x560 gerada pelo detetor de tabuleiro.
//  */
// class PiecesDetector(private val context: Context) {

//     private var interpreter: Interpreter? = null
//     private var gpuDelegate: GpuDelegate? = null
    
//     // Configurações YOLO
//     private val INPUT_SIZE = 640 // Ou o tamanho que treinares o teu modelo de peças!
//     private var inputBuffer: ByteBuffer? = null

//     data class Piece(val isWhite: Boolean, val boundingBox: FloatArray)

//     fun initialize(modelName: String = "pieces_model.tflite") {
//         try {
//             val compatList = CompatibilityList()
//             val options = Interpreter.Options()

//             if (compatList.isDelegateSupportedOnThisDevice) {
//                 gpuDelegate = GpuDelegate(compatList.bestOptionsForThisDevice)
//                 options.addDelegate(gpuDelegate)
//             } else {
//                 options.setNumThreads(4)
//             }

//             val fileDescriptor = context.assets.openFd(modelName)
//             val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
//             val fileChannel = inputStream.channel
//             val startOffset = fileDescriptor.startOffset
//             val declaredLength = fileDescriptor.declaredLength
//             val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

//             interpreter = Interpreter(modelBuffer, options)
//             Log.i("PiecesDetector", "Modelo de Peças carregado com sucesso (GPU: ${compatList.isDelegateSupportedOnThisDevice})")
            
//             inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4).apply {
//                 order(ByteOrder.nativeOrder())
//             }
//         } catch (e: Exception) {
//             Log.w("PiecesDetector", "A aguardar o modelo de Peças ($modelName) ser adicionado aos assets...")
//         }
//     }

//     fun isReady(): Boolean = interpreter != null

//     /**
//      * Extrai peças do tabuleiro a partir da imagem top-down 560x560.
//      * Retorna uma lista de Peças detetadas.
//      */
//     fun detectPieces(topDownBoard: Bitmap): List<Piece> {
//         val interp = interpreter ?: return emptyList()
//         val buffer = inputBuffer ?: return emptyList()
//         // TODO: A implementar a matriz de I/O quando soubermos a estrutura do teu modelo YOLO de peças!
//         return emptyList()
//     }

//     fun close() {
//         interpreter?.close()
//         interpreter = null
//         gpuDelegate?.close()
//         gpuDelegate = null
//     }
// }
