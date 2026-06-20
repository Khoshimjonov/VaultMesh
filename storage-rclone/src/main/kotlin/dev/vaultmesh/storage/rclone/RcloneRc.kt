package dev.vaultmesh.storage.rclone

import dev.vaultmesh.storage.RemoteEntry
import dev.vaultmesh.storage.StorageUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class RcloneRcException(val command: String, val status: Int, message: String) :
    RuntimeException("rclone RC '$command' failed ($status): $message")

/** Typed wrapper over rclone's remote-control HTTP API (verified against rclone v1.74). */
class RcloneRc(private val baseUrl: String, private val authHeader: String?) {

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun call(command: String, body: JsonObject = JsonObject(emptyMap())): JsonObject =
        withContext(Dispatchers.IO) {
            val builder = HttpRequest.newBuilder(URI.create("$baseUrl/$command"))
                .timeout(Duration.ofHours(6)) // long transfers
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            authHeader?.let { builder.header("Authorization", it) }

            val resp = http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            val parsed = runCatching { json.parseToJsonElement(resp.body()).jsonObject }
                .getOrElse { JsonObject(emptyMap()) }
            if (resp.statusCode() !in 200..299) {
                val msg = parsed["error"]?.jsonPrimitive?.contentOrNull ?: resp.body().take(300)
                throw RcloneRcException(command, resp.statusCode(), msg)
            }
            parsed
        }

    suspend fun version(): String =
        call("core/version")["version"]?.jsonPrimitive?.contentOrNull ?: "unknown"

    /** Additive mirror of a whole directory tree (no deletions on the destination). */
    suspend fun syncCopy(srcFs: String, dstFs: String) {
        call(
            "sync/copy",
            buildJsonObject {
                put("srcFs", srcFs)
                put("dstFs", dstFs)
                put("createEmptySrcDirs", true)
            },
        )
    }

    suspend fun list(fs: String, remote: String = ""): List<RemoteEntry> {
        val result = call(
            "operations/list",
            buildJsonObject { put("fs", fs); put("remote", remote) },
        )
        return result["list"]?.jsonArray?.map { e ->
            val o = e.jsonObject
            RemoteEntry(
                path = o["Path"]?.jsonPrimitive?.contentOrNull ?: "",
                name = o["Name"]?.jsonPrimitive?.contentOrNull ?: "",
                size = o["Size"]?.jsonPrimitive?.longOrZero() ?: 0,
                isDir = (o["IsDir"]?.jsonPrimitive?.contentOrNull == "true"),
            )
        } ?: emptyList()
    }

    suspend fun listConfiguredRemotes(): List<String> =
        call("config/listremotes")["remotes"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

    /**
     * Quota for [fs] via `operations/about`. Returns null when the backend doesn't support it
     * (rclone returns an error for e.g. plain S3) so callers can show "not reported" instead of failing.
     */
    suspend fun about(fs: String): StorageUsage? = runCatching {
        val o = call("operations/about", buildJsonObject { put("fs", fs) })
        StorageUsage(
            total = o["total"]?.jsonPrimitive?.longOrNull(),
            used = o["used"]?.jsonPrimitive?.longOrNull(),
            free = o["free"]?.jsonPrimitive?.longOrNull(),
            trashed = o["trashed"]?.jsonPrimitive?.longOrNull(),
            other = o["other"]?.jsonPrimitive?.longOrNull(),
            objects = o["objects"]?.jsonPrimitive?.longOrNull(),
        ).takeIf { it.hasAny }
    }.getOrNull()

    /** Creates/updates an rclone remote (e.g. type="drive", "onedrive", "mega", "yandex", "s3"). */
    suspend fun configCreate(name: String, type: String, parameters: Map<String, String>) {
        call(
            "config/create",
            buildJsonObject {
                put("name", name)
                put("type", type)
                put(
                    "parameters",
                    buildJsonObject { parameters.forEach { (k, v) -> put(k, v) } },
                )
            },
        )
    }

    suspend fun configDelete(name: String) {
        call("config/delete", buildJsonObject { put("name", name) })
    }

    private fun JsonPrimitive.longOrZero(): Long = contentOrNull?.toLongOrNull() ?: 0
    private fun JsonPrimitive.longOrNull(): Long? = contentOrNull?.toLongOrNull()
}
