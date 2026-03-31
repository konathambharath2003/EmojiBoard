package dev.patrickgold.florisboard

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.util.Random

class Emojifier(context: Context) {
    private val wordMap = HashMap<String, List<String>>() // Word -> List of weighted emojis
    private val random = Random()

    // 🚫 NSFW / Inappropriate Blocklist
    private val blocklist = setOf(
        "🍆", "🍑", "💦", "🖕" // Add any others you want to filter out
    )

    init {
        try {
            // Load JSON
            val jsonString = context.assets.open("emoji-data.json").bufferedReader().use { it.readText() }
            val json = JSONObject(jsonString)

            val keys = json.keys()
            while (keys.hasNext()) {
                val word = keys.next() // e.g., "hello"
                val emojiData = json.getJSONObject(word) // {"👋": 5, "😊": 2}

                // Convert frequency counts into a flat weighted list
                // e.g. {"👋": 2, "😊": 1} becomes ["👋", "👋", "😊"]
                val weightedList = ArrayList<String>()
                val emojiKeys = emojiData.keys()

                while (emojiKeys.hasNext()) {
                    val emoji = emojiKeys.next()
                    if (blocklist.contains(emoji)) continue // Skip bad emojis

                    val count = emojiData.getInt(emoji)
                    // Cap max repetition to save memory, e.g. max 10 entries per emoji
                    val effectiveCount = Math.min(count, 5)

                    repeat(effectiveCount) {
                        weightedList.add(emoji)
                    }
                }

                if (weightedList.isNotEmpty()) {
                    wordMap[word.lowercase()] = weightedList
                }
            }
            Log.d("VibeEmojifier", "Loaded ${wordMap.size} words for emojification")

        } catch (e: Exception) {
            Log.e("VibeEmojifier", "Failed to load emoji data", e)
        }
    }

    fun emojify(text: String, density: Float = 1.0f): String {
        val words = text.split(" ") // Split by space
        val sb = StringBuilder()

        for (word in words) {
            sb.append(word)

            // Clean word for lookup (remove punctuation like "Hello!" -> "hello")
            val cleanWord = word.lowercase().replace(Regex("[^a-z0-9]"), "")

            // Check if we should add emoji (Density check + Word exists)
            if (random.nextFloat() < density && wordMap.containsKey(cleanWord)) {
                val emojis = wordMap[cleanWord]!!
                if (emojis.isNotEmpty()) {
                    val randomEmoji = emojis[random.nextInt(emojis.size)]
                    sb.append(" ").append(randomEmoji)
                }
            }
            sb.append(" ")
        }
        return sb.toString().trim()
    }
}
