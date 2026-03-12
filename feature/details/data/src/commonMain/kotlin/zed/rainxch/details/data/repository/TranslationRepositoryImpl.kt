package zed.rainxch.details.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.core.data.network.createPlatformHttpClient
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.domain.model.ProxyConfig
import zed.rainxch.details.domain.model.TranslationResult
import zed.rainxch.details.domain.repository.TranslationRepository

class TranslationRepositoryImpl(
    private val localizationManager: LocalizationManager,
) : TranslationRepository,
    AutoCloseable {
    private val httpClient: HttpClient = createPlatformHttpClient(ProxyConfig.None)

    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val cacheMutex = Mutex()
    private val cache = LinkedHashMap<String, TranslationResult>(50, 0.75f, true)
    private val maxCacheSize = 50
    private val maxChunkSize = 4500

    override suspend fun translate(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        val cacheKey = "${text.hashCode()}:$targetLanguage"
        cacheMutex.withLock { cache[cacheKey] }?.let { return it }

        val chunks = chunkText(text)
        val translatedParts = mutableListOf<Pair<String, String>>()

        var detectedLang: String? = null

        for ((chunkText, delimiter) in chunks) {
            val response = translateSingleChunk(chunkText, targetLanguage, sourceLanguage)
            translatedParts.add(response.translatedText to delimiter)
            if (detectedLang == null) {
                detectedLang = response.detectedSourceLanguage
            }
        }

        val result =
            TranslationResult(
                translatedText =
                    translatedParts
                        .dropLast(1)
                        .joinToString("") { (text, delim) -> text + delim } +
                        translatedParts.lastOrNull()?.first.orEmpty(),
                detectedSourceLanguage = detectedLang,
            )

        cacheMutex.withLock {
            if (cache.size >= maxCacheSize) {
                val firstKey = cache.keys.first()
                cache.remove(firstKey)
            }
            cache[cacheKey] = result
        }
        return result
    }

    override fun getDeviceLanguageCode(): String = localizationManager.getPrimaryLanguageCode()

    private suspend fun translateSingleChunk(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): TranslationResult {
        val responseText =
            httpClient
                .get(
                    "https://translate.googleapis.com/translate_a/single",
                ) {
                    parameter("client", "gtx")
                    parameter("sl", sourceLanguage)
                    parameter("tl", targetLanguage)
                    parameter("dt", "t")
                    parameter("q", text)
                }.bodyAsText()

        return try {
            parseTranslationResponse(responseText)
        } catch (_: Exception) {
            TranslationResult(
                translatedText = text,
                detectedSourceLanguage = null,
            )
        }
    }

    private fun parseTranslationResponse(responseText: String): TranslationResult {
        val root = json.parseToJsonElement(responseText).jsonArray

        val segments = root[0].jsonArray
        val translatedText =
            segments.joinToString("") { segment ->
                segment.jsonArray[0].jsonPrimitive.content
            }

        val detectedLang =
            try {
                root[2].jsonPrimitive.content
            } catch (_: Exception) {
                null
            }

        return TranslationResult(
            translatedText = translatedText,
            detectedSourceLanguage = detectedLang,
        )
    }

    private fun chunkText(text: String): List<Pair<String, String>> {
        val paragraphs = text.split("\n\n")
        val chunks = mutableListOf<Pair<String, String>>()
        val currentChunk = StringBuilder()

        for (paragraph in paragraphs) {
            if (paragraph.length > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(Pair(currentChunk.toString(), "\n\n"))
                    currentChunk.clear()
                }
                chunkLargeParagraph(paragraph, chunks)
            } else if (currentChunk.length + paragraph.length + 2 > maxChunkSize) {
                chunks.add(Pair(currentChunk.toString(), "\n\n"))
                currentChunk.clear()
                currentChunk.append(paragraph)
            } else {
                if (currentChunk.isNotEmpty()) currentChunk.append("\n\n")
                currentChunk.append(paragraph)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(Pair(currentChunk.toString(), "\n\n"))
        }

        return chunks
    }

    private fun chunkLargeParagraph(
        paragraph: String,
        chunks: MutableList<Pair<String, String>>,
    ) {
        val lines = paragraph.split("\n")
        val currentChunk = StringBuilder()

        for (line in lines) {
            if (line.length > maxChunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(Pair(currentChunk.toString(), "\n"))
                    currentChunk.clear()
                }
                var start = 0
                while (start < line.length) {
                    val end = minOf(start + maxChunkSize, line.length)
                    chunks.add(Pair(line.substring(start, end), ""))
                    start = end
                }
            } else if (currentChunk.length + line.length + 1 > maxChunkSize) {
                chunks.add(Pair(currentChunk.toString(), "\n"))
                currentChunk.clear()
                currentChunk.append(line)
            } else {
                if (currentChunk.isNotEmpty()) currentChunk.append("\n")
                currentChunk.append(line)
            }
        }

        if (currentChunk.isNotEmpty()) {
            chunks.add(Pair(currentChunk.toString(), "\n"))
        }
    }

    override fun close() {
        httpClient.close()
    }
}
