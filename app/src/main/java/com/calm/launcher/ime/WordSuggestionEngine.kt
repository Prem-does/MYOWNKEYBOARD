package com.calm.launcher.ime

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.Normalizer

/**
 * Simple word suggestion engine for Android keyboards.
 *
 * It supports prefix search and score-based ranking using a frequency dictionary.
 * Load a CSV in the form `word,frequency` from the assets folder for a large dictionary.
 */
class WordSuggestionEngine(
    private val context: Context,
    private val assetFileName: String = "wordfreq_large_en.csv"
) {

    private val root = TrieNode()
    private val frequencyMap = mutableMapOf<String, Float>()
    private val personalFrequency = mutableMapOf<String, Int>()
    private val topWords = mutableListOf<String>()
    private val staticFallback = listOf("the", "to", "and")
    private val minSuggestions = 3
    private val maxSuggestions = 4
    private var loaded = false

    var onLoaded: (() -> Unit)? = null

    init {
        Thread {
            try {
                loadDictionaryFromAssets(assetFileName)
                updateTopWords()
                loaded = true
                onLoaded?.invoke()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load suggestion dictionary: ${e.message}")
            }
        }.start()
    }

    fun suggest(rawInput: String, maxResults: Int = maxSuggestions): List<String> {
        val prefix = normalize(rawInput)
        if (prefix.isEmpty()) return if (loaded) topWords.take(maxResults) else staticFallback

        val candidates = mutableListOf<Suggestion>()

        // 1. prefix completions
        candidates += prefixSearch(prefix, maxResults * 2)

        // 2. if no good prefix results, try simple edit-distance corrections
        if (candidates.isEmpty()) {
            candidates += correctionCandidates(prefix, maxResults * 3)
        }

        // 3. fallback to top words that start with the prefix
        if (candidates.isEmpty()) {
            candidates += topWords
                .filter { it.startsWith(prefix) }
                .map { Suggestion(it, score(it, prefix)) }
        }

        val result = candidates
            .sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.word })
            .map { it.word }
            .distinct()
            .take(maxResults)
            .toMutableList()

        if (result.size < minSuggestions) {
            for (word in topWords) {
                if (result.size >= minSuggestions) break
                if (word !in result) result += word
            }
        }

        return result
    }

    fun learnWord(word: String) {
        val normalized = normalize(word)
        if (normalized.isEmpty()) return
        personalFrequency[normalized] = personalFrequency.getOrDefault(normalized, 0) + 1
    }

    private fun prefixSearch(prefix: String, limit: Int): List<Suggestion> {
        val node = findNode(prefix) ?: return emptyList()
        val suggestions = mutableListOf<Suggestion>()
        collectWords(node, suggestions, limit)
        return suggestions.map { it.copy(score = score(it.word, prefix)) }
    }

    private fun correctionCandidates(prefix: String, limit: Int): List<Suggestion> {
        val candidates = mutableListOf<Suggestion>()
        if (prefix.length < 2) return emptyList()

        val edits = generateOneEdits(prefix)
        for (candidate in edits) {
            if (frequencyMap.containsKey(candidate)) {
                candidates += Suggestion(candidate, score(candidate, prefix) - 0.5f)
            }
        }
        return candidates.sortedByDescending { it.score }.take(limit)
    }

    private fun score(word: String, prefix: String): Float {
        val freq = frequencyMap[word] ?: 1e-7f
        val freqScore = Math.log(freq.toDouble()).toFloat()
        val personal = personalFrequency[word]?.toFloat() ?: 0f
        val prefixMatch = if (word.startsWith(prefix)) prefix.length.toFloat() / word.length else 0f
        return freqScore * 1.0f + personal * 0.25f + prefixMatch * 0.75f
    }

    private fun normalize(text: String): String {
        val lowered = text.lowercase()
        val normalized = Normalizer.normalize(lowered, Normalizer.Form.NFKC)
        return normalized.replace(Regex("[^a-z']"), "")
    }

    private fun findNode(prefix: String): TrieNode? {
        var node = root
        for (char in prefix) {
            node = node.children[char] ?: return null
        }
        return node
    }

    private fun collectWords(node: TrieNode, output: MutableList<Suggestion>, limit: Int) {
        if (output.size >= limit) return
        if (node.isWord && node.word != null) {
            output += Suggestion(node.word!!, 0f)
        }
        for ((_, child) in node.children) {
            collectWords(child, output, limit)
            if (output.size >= limit) return
        }
    }

    private fun generateOneEdits(word: String): Set<String> {
        val edits = mutableSetOf<String>()
        val alphabet = "abcdefghijklmnopqrstuvwxyz'"

        for (i in word.indices) {
            // deletion
            edits += word.removeRange(i, i + 1)
            // transposition
            if (i + 1 < word.length) {
                val swapped = word.toCharArray()
                swapped[i] = word[i + 1]
                swapped[i + 1] = word[i]
                edits += String(swapped)
            }
            // replacement
            for (c in alphabet) {
                edits += word.substring(0, i) + c + word.substring(i + 1)
            }
        }

        for (i in 0..word.length) {
            for (c in alphabet) {
                edits += word.substring(0, i) + c + word.substring(i)
            }
        }

        return edits
    }

    private fun addWord(word: String, frequency: Float) {
        if (word.isBlank()) return
        frequencyMap[word] = frequency
        var node = root
        for (char in word) {
            node = node.children.getOrPut(char) { TrieNode() }
        }
        node.isWord = true
        node.word = word
    }

    private fun loadDictionaryFromAssets(assetName: String) {
        context.assets.open(assetName).use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                lines.drop(1).forEach { line ->
                    val parts = line.split(',')
                    if (parts.size < 2) return@forEach
                    val word = normalize(parts[0])
                    val freq = parts[1].toFloatOrNull() ?: return@forEach
                    addWord(word, freq)
                }
            }
        }
    }

    private fun updateTopWords() {
        topWords.clear()
        topWords += frequencyMap.entries
            .sortedByDescending { it.value }
            .map { it.key }
            .take(maxSuggestions)
    }

    private data class Suggestion(val word: String, val score: Float)

    private class TrieNode {
        val children = mutableMapOf<Char, TrieNode>()
        var isWord = false
        var word: String? = null
    }

    companion object {
        private const val TAG = "WordSuggestionEngine"
    }
}
