package dev.patrickgold.florisboard

import android.content.Context
import android.util.Log
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

class SentimentPredictor(context: Context) {
    private var interpreter: Interpreter? = null
    private val vocab = HashMap<String, Int>()
    private val labels = ArrayList<String>()

    private val sentimentToEmojis = mapOf(
        "joy" to listOf("😂", "😊", "🥰"),
        "love" to listOf("❤️", "😍", "💕"),
        "anger" to listOf("😠", "😡", "🤬"),
        "sadness" to listOf("😭", "😢", "💔"),
        "fear" to listOf("😱", "😨", "😖"),
        "surprise" to listOf("😲", "🤯", "🫢"),
        "disgust" to listOf("🤢", "🤮", "🥴"),
        "optimism" to listOf("🤩", "💪", "🚀"),
        "pessimism" to listOf("😒", "😞️", "😑"),
        "anticipation" to listOf("🫣", "👀", "🤔"),
        "trust" to listOf("🤝", "💯", "🛡️")
    )

    init {
        try {
            Log.d("SentimentBoard", " Initializing Sentiment Predictor...")

            //loading model
            val assetPath = "sentiment/sentiment_model.tflite"

            // creates a cache
            val cacheFile = File(context.cacheDir, "sentiment_model_temp.tflite")

            // if already exists deleted
            if (cacheFile.exists()) {
                cacheFile.delete()
            }

            // copies the cache file to system memory
            Log.d("SentimentBoard", " Copying $assetPath to cache...")
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d("SentimentBoard", " File copied. Size: ${cacheFile.length()} bytes")

            // loads interpreter
            val options = Interpreter.Options()
            options.setNumThreads(4) // increase threads for more speed
            interpreter = Interpreter(cacheFile, options)
            Log.d("SentimentBoard", " Interpreter Created!")

            // loads voacb.json fiel
            val vocabStr = context.assets.open("sentiment/vocab.json").bufferedReader().use { it.readText() }
            val vocabJson = JSONObject(vocabStr)
            val keys = vocabJson.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                vocab[key] = vocabJson.getInt(key)
            }

            // loads labels.json file
            val labelsStr = context.assets.open("sentiment/labels.json").bufferedReader().use { it.readText() }
            val labelsJson = JSONObject(labelsStr).getJSONArray("labels")
            for (i in 0 until labelsJson.length()) {
                labels.add(labelsJson.getString(i))
            }

            Log.d("SentimentBoard", " SETUP COMPLETE. Labels: ${labels.size}, Vocab: ${vocab.size}")

        } catch (e: Exception) {
            // logging error
            Log.e("SentimentBoard", " CRITICAL FAILURE: ${e.message}", e)
            e.printStackTrace()
        }
    }

    fun predictEmojis(text: String, count: Int = 3): List<String> {
        if (interpreter == null) {

            return emptyList()
        }

        try {
            // create int type inputs
            val inputIds = IntArray(128) { 1 } // of padding 1's
            val attentionMask = IntArray(128) { 0 }

            inputIds[0] = 0
            attentionMask[0] = 1

            var idx = 1
            val words = text.trim().split("\\s+".toRegex())

            // tokenizing
            for (word in words) {
                if (idx >= 127) break

                var id = vocab["Ġ$word"]
                if (id == null) id = vocab[word]
                if (id == null) id = vocab["Ġ${word.lowercase()}"]
                if (id == null) id = vocab[word.lowercase()]
                if (id == null) id = 3

                inputIds[idx] = id!!
                attentionMask[idx] = 1
                idx++
            }
            inputIds[idx] = 2
            attentionMask[idx] = 1


            val inputBuffer = ByteBuffer.allocateDirect(128 * 4).order(ByteOrder.nativeOrder())
            val maskBuffer = ByteBuffer.allocateDirect(128 * 4).order(ByteOrder.nativeOrder())

            for (i in 0 until 128) {
                inputBuffer.putInt(inputIds[i])
                maskBuffer.putInt(attentionMask[i])
            }


            inputBuffer.rewind()
            maskBuffer.rewind()


            val outputBuffer = Array(1) { FloatArray(labels.size) }

            // input oredr (mask,input)

            val inputs: Array<Any> = arrayOf(maskBuffer, inputBuffer)
            val outputs = mapOf(0 to outputBuffer)

            interpreter?.runForMultipleInputsOutputs(inputs, outputs)


            val logits = outputBuffer[0]
            var maxScore = -Float.MAX_VALUE
            var maxIdx = -1

            for (i in logits.indices) {
                if (logits[i] > maxScore) {
                    maxScore = logits[i]
                    maxIdx = i
                }
            }


            if (maxIdx == -1) return emptyList()

            //selecting the best sentiment match with original text
            val bestSentiment = labels[maxIdx]
            Log.d("SentimentBoard", " Sentiment: $bestSentiment")

            return sentimentToEmojis[bestSentiment]?.take(count) ?: listOf("❓")

        } catch (e: Exception) {
            Log.e("SentimentBoard", " Prediction Error: ${e.message}")
            return emptyList()
        }
    }
}
