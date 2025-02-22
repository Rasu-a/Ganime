package ani.saikou.parsers.anime.extractors

import ani.saikou.FileUrl
import ani.saikou.client
import ani.saikou.parsers.Video
import ani.saikou.parsers.VideoContainer
import ani.saikou.parsers.VideoExtractor
import ani.saikou.parsers.VideoServer
import ani.saikou.parsers.anime.NineAnime.Companion.cipher
import ani.saikou.parsers.anime.NineAnime.Companion.encrypt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class VizCloud(override val server: VideoServer) : VideoExtractor() {

    @Serializable private data class Sources(val file: String)
    @Serializable private data class Media(val sources: List<Sources>)
    @Serializable private data class Data(val media: Media)
    @Serializable private data class Response(val data: Data)

    private val regex = Regex("(.+?/)e(?:mbed)?/([a-zA-Z0-9]+)")

    override suspend fun extract(): VideoContainer {

        val embed = server.embed
        val group = regex.find(embed.url)?.groupValues!!

        val host = group[1]
        val viz = getKey()
        val id = encrypt(
            cipher(
                viz.cipherKey,
                group[2].also { println(it) }
            ).also { println(it) },
            viz.encryptKey
        ).also { println(it) }

        val link = "${host}mediainfo/${dashify(id, viz.dashTable)}?key=${viz.mainKey}"
        val response = client.get(link, embed.headers)
        println(link)
        if (!response.text.startsWith("{")) throw Exception("Seems like 9Anime kiddies changed stuff again, Go touch some grass for bout an hour Or use a different Server")
        return VideoContainer(response.parsed<Response>().data.media.sources.map {
            val file = FileUrl(it.file, mapOf("referer" to host))
            Video(null, true, file)
        })
    }

    companion object {
        private var lastChecked = 0L
        private const val jsonLink = "https://raw.githubusercontent.com/AnimeJeff/OverFlow/main/keys.json"
        private var cipherKey: VizCloudKey? = null
        suspend fun getKey(): VizCloudKey {
            cipherKey = if (cipherKey != null && (lastChecked - System.currentTimeMillis()) < 1000 * 60 * 30) cipherKey!!
            else {
                lastChecked = System.currentTimeMillis()
                client.get(jsonLink).parsed()
            }
            return cipherKey!!
        }

        @Serializable
        data class VizCloudKey(
            @SerialName("cipherKey") val cipherKey: String,
            @SerialName("mainKey") val mainKey: String,
            @SerialName("encryptKey") val encryptKey: String,
            @SerialName("dashTable") val dashTable: String
        )

        private const val baseTable = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+=/_"

        private fun dashify(id: String, dashTable: String): String {
            val table = dashTable.split(" ")
            return id.mapIndexedNotNull { i, c ->
                table.getOrNull((baseTable.indexOf(c) * 16) + (i % 16))
            }.joinToString("-")
        }
    }
}

