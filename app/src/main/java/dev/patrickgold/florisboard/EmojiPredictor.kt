package dev.patrickgold.florisboard

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.*
import kotlin.collections.ArrayList

class EmojiPredictor(context: Context) {
    private var interpreter: Interpreter? = null
    private val vocab = HashMap<String, Int>()
    private val emojis = ArrayList<String>()

    init {
        try {
            // loads model from assets
            val fileDescriptor = context.assets.openFd("emoji_model.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options()
            options.setNumThreads(4) // increase threads for more speed
            interpreter = Interpreter(modelBuffer, options)

            // loads vocab.json file
            val vocabString = context.assets.open("vocab.json").bufferedReader().use { it.readText() }
            val vocabJson = JSONObject(vocabString)
            val keys = vocabJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocab[key] = vocabJson.getInt(key)
            }

            // loads labels.json file and extracts array from mapped object
            val mapString = context.assets.open("labels.json").bufferedReader().use { it.readText() }
            val mapObj = JSONObject(mapString)
            val mapArray = mapObj.getJSONArray("labels")
            for (i in 0 until mapArray.length()) {
                emojis.add(mapArray.getString(i))
            }

            Log.d("VibeBoard", "Loaded: ${vocab.size} words, ${emojis.size} emojis")

        } catch (e: Exception) {
            Log.e("VibeBoard", "Init Error", e)
        }
    }

    fun predict(text: String, topK: Int = 3): List<String> {
        if (interpreter == null) return emptyList()

        // tokenizing
        val inputIds = IntArray(64) { 1 }
        val attentionMask = IntArray(64) { 0 }


        inputIds[0] = 0
        attentionMask[0] = 1
        var idx = 1

        // splliting on space
        val words = text.trim().split("\\s+".toRegex())

        for (word in words) {
            if (idx >= 63) break


            var tokenKey = word
            if (idx > 1) tokenKey = "Ġ$word"


            val id = vocab[tokenKey] ?: vocab[word] ?: 3

            inputIds[idx] = id
            attentionMask[idx] = 1
            idx++
        }


        if (idx < 64) {
            inputIds[idx] = 2
            attentionMask[idx] = 1
        }

        // creating int input buffers
        val inputBuffer = ByteBuffer.allocateDirect(64 * 4).order(ByteOrder.nativeOrder())
        val maskBuffer = ByteBuffer.allocateDirect(64 * 4).order(ByteOrder.nativeOrder())

        for (i in 0 until 64) {
            inputBuffer.putInt(inputIds[i])
            maskBuffer.putInt(attentionMask[i])
        }


        inputBuffer.rewind()
        maskBuffer.rewind()

        // inferencing
        val outputBuffer = Array(1) { FloatArray(emojis.size) }

        // input order (mask,input)
        val inputs: Array<Any> = arrayOf(maskBuffer, inputBuffer)
        val outputs = mapOf(0 to outputBuffer)

        try {
            interpreter?.runForMultipleInputsOutputs(inputs, outputs)
        } catch (e: Exception) {
            Log.e("EmojiPred", "Inference Error: input arrays wrong order", e)
            return emptyList()
        }

        //  getting topK=3 emojis and returning same
        val probs = outputBuffer[0]
        val sortedIndices = probs.indices.sortedByDescending { probs[it] }

        val result = ArrayList<String>()
        for (i in 0 until Math.min(topK, sortedIndices.size)) {
            result.add(emojis[sortedIndices[i]])
        }

        return result
    }
}
