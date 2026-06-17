package com.prplegryn.movio.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

data class SmsInitResult(
    val captchaToken: String,
    val challengeUrl: String = "",
)

data class SmsSendResult(
    val verificationId: String,
)

data class SmsVerifyResult(
    val verificationToken: String,
)

class GuangyaService(
    private var session: GuangyaSession,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun updateSession(newSession: GuangyaSession) {
        session = newSession
    }

    fun loginSmsInit(phoneNumber: String, captchaToken: String = ""): SmsInitResult {
        val body = JSONObject()
            .put("client_id", CLIENT_ID)
            .put("action", "POST:/v1/auth/verification")
            .put("device_id", deviceId())
            .put("meta", JSONObject().put("phone_number", phoneNumber))
        if (captchaToken.isNotBlank()) {
            body.put("captcha_token", captchaToken)
        }
        val json = postJson(
            "https://account.guangyapan.com/v1/shield/captcha/init",
            body,
            accountHeaders(),
        )
        return SmsInitResult(
            captchaToken = json.optString("captcha_token"),
            challengeUrl = json.optString("url"),
        )
    }

    fun loginSmsSend(phoneNumber: String, captchaToken: String): SmsSendResult {
        val json = postJson(
            "https://account.guangyapan.com/v1/auth/verification",
            JSONObject()
                .put("phone_number", phoneNumber)
                .put("target", "ANY")
                .put("client_id", CLIENT_ID),
            accountHeaders() + ("x-captcha-token" to captchaToken),
        )
        return SmsSendResult(verificationId = json.optString("verification_id"))
    }

    fun loginSmsVerify(verificationId: String, code: String): SmsVerifyResult {
        val json = postJson(
            "https://account.guangyapan.com/v1/auth/verification/verify",
            JSONObject()
                .put("verification_id", verificationId)
                .put("verification_code", code)
                .put("client_id", CLIENT_ID),
            accountHeaders(),
        )
        return SmsVerifyResult(verificationToken = json.optString("verification_token"))
    }

    fun loginSmsSignin(
        phoneNumber: String,
        code: String,
        verificationToken: String,
        captchaToken: String,
    ): GuangyaSession {
        val json = postJson(
            "https://account.guangyapan.com/v1/auth/signin",
            JSONObject()
                .put("verification_code", code)
                .put("verification_token", verificationToken)
                .put("username", phoneNumber)
                .put("client_id", CLIENT_ID),
            accountHeaders() + ("x-captcha-token" to captchaToken),
        )
        session = session.copy(
            accessToken = json.optString("access_token"),
            refreshToken = json.optString("refresh_token"),
            deviceId = deviceId(),
            phone = phoneNumber,
        )
        return session
    }

    fun userInfo(): JSONObject {
        return postJson(
            "https://account.guangyapan.com/v1/user/me",
            JSONObject(),
            accountHeaders() + ("authorization" to "Bearer ${session.accessToken}"),
        )
    }

    fun listVideos(rootId: String, pageSize: Int = 80): List<CloudVideo> {
        val all = mutableListOf<CloudVideo>()
        var page = 0
        while (page < 8) {
            val data = JSONObject()
                .put("parentId", if (rootId.isBlank() || rootId == "*") "*" else rootId)
                .put("page", page)
                .put("pageSize", pageSize)
                .put("orderBy", 3)
                .put("sortType", 1)
                .put("fileTypes", JSONArray().put(2))
                .put("resType", 1)
                .put("needPlayRecord", true)
            val json = postJson(
                "https://api.guangyapan.com/userres/v1/file/get_file_list",
                data,
                apiHeaders(),
            )
            val list = firstArray(json, "list", "files", "items", "data", "rows")
            if (list.length() == 0) break
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                val video = parseCloudVideo(item)
                if (video.id.isNotBlank() && video.name.isNotBlank()) {
                    all += video
                }
            }
            if (list.length() < pageSize) break
            page += 1
        }
        return all
    }

    fun downloadUrl(fileId: String): String {
        val json = postJson(
            "https://api.guangyapan.com/nd.bizuserres.s/v1/get_res_download_url",
            JSONObject().put("fileId", fileId),
            apiHeaders(),
        )
        return firstString(json, "downloadUrl", "download_url", "url", "data")
    }

    private fun parseCloudVideo(json: JSONObject): CloudVideo {
        val play = json.optJSONObject("playRecord") ?: json.optJSONObject("play_record") ?: JSONObject()
        return CloudVideo(
            id = firstString(json, "fileId", "file_id", "id", "fid"),
            name = firstString(json, "fileName", "name", "filename", "title"),
            parentId = firstString(json, "parentId", "parent_id"),
            size = firstLong(json, "fileSize", "size", "bytes"),
            durationMs = firstLong(json, "duration", "durationMs", "videoDuration").normalizeDuration(),
            playProgressMs = firstLong(play, "position", "progress", "playProgress", "play_progress").normalizeDuration(),
            rawCoverUrl = firstString(json, "cover", "thumbnail", "thumb", "previewUrl"),
        )
    }

    private fun postJson(
        url: String,
        body: JSONObject,
        headers: Map<String, String>,
    ): JSONObject {
        val requestBody = body.toString().toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .headers(okhttp3.Headers.headersOf(*headers.flatMap { listOf(it.key, it.value) }.toTypedArray()))
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                error("光鸭请求失败 ${response.code}: $text")
            }
            return JSONObject(text.ifBlank { "{}" })
        }
    }

    private fun accountHeaders(): Map<String, String> =
        mapOf(
            "accept" to "*/*",
            "content-type" to "application/json",
            "origin" to "https://www.guangyapan.com",
            "referer" to "https://www.guangyapan.com/",
            "user-agent" to USER_AGENT,
            "x-client-id" to CLIENT_ID,
            "x-client-version" to "0.0.1",
            "x-device-id" to deviceId(),
            "x-device-model" to "chrome%2F147.0.0.0",
            "x-device-name" to "PC-Chrome",
            "x-device-sign" to "wdi10.${deviceId()}${randomHex(16)}",
            "x-net-work-type" to "NONE",
            "x-os-version" to "Android",
            "x-platform-version" to "1",
            "x-protocol-version" to "301",
            "x-provider-name" to "NONE",
            "x-sdk-version" to "9.0.2",
        )

    private fun apiHeaders(): Map<String, String> =
        mapOf(
            "accept" to "application/json, text/plain, */*",
            "authorization" to "Bearer ${session.accessToken}",
            "content-type" to "application/json",
            "did" to deviceId(),
            "dt" to "4",
            "origin" to "https://www.guangyapan.com",
            "referer" to "https://www.guangyapan.com/",
            "traceparent" to traceParent(),
            "user-agent" to USER_AGENT,
        )

    private fun deviceId(): String =
        session.deviceId.ifBlank { randomHex(16) }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val CLIENT_ID = "aMe-8VSlkrbQXpUR"
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36"
    }
}

private fun firstArray(json: JSONObject, vararg keys: String): JSONArray {
    for (key in keys) {
        val value = json.opt(key)
        if (value is JSONArray) return value
        if (value is JSONObject) {
            val nested = firstArray(value, "list", "files", "items", "rows")
            if (nested.length() > 0) return nested
        }
    }
    return JSONArray()
}

fun firstString(json: JSONObject, vararg keys: String): String {
    for (key in keys) {
        val value = json.opt(key)
        if (value is String && value.isNotBlank()) return value
        if (value is Number) return value.toString()
        if (value is JSONObject) {
            val nested = firstString(value, "url", "value", "downloadUrl")
            if (nested.isNotBlank()) return nested
        }
    }
    return ""
}

private fun firstLong(json: JSONObject, vararg keys: String): Long {
    for (key in keys) {
        if (json.has(key)) return json.optLong(key, 0L)
    }
    return 0L
}

private fun Long.normalizeDuration(): Long =
    if (this in 1 until 100_000) this * 1000L else this

private fun randomHex(bytes: Int): String {
    val data = ByteArray(bytes)
    SecureRandom().nextBytes(data)
    return data.joinToString("") { "%02x".format(it) }
}

private fun traceParent(): String =
    "00-${randomHex(16)}-${randomHex(8)}-01"
