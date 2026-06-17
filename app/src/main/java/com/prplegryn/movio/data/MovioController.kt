package com.prplegryn.movio.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prplegryn.movio.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MovioController(context: Context) {
    private val appContext = context.applicationContext
    private val store = MovioStore(appContext)

    var settings by mutableStateOf(store.loadSettings())
        private set
    var library by mutableStateOf<List<MediaGroup>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var selectedGroup by mutableStateOf<MediaGroup?>(null)
        private set

    var pendingCaptchaToken by mutableStateOf("")
        private set
    var pendingVerificationId by mutableStateOf("")
        private set
    var pendingPhone by mutableStateOf("")
        private set

    private var guangya = GuangyaService(settings.guangya)

    fun updateRootId(value: String) {
        settings = settings.copy(rootId = value.ifBlank { "*" })
        store.saveSettings(settings)
    }

    fun updateTmdbToken(value: String) {
        settings = settings.copy(tmdbToken = value.trim())
        store.saveSettings(settings)
    }

    suspend fun requestSms(phone: String) {
        val normalizedPhone = phone.trim()
        val result = runBusy("验证码已发送") {
            val init = guangya.loginSmsInit(normalizedPhone)
            val send = guangya.loginSmsSend(normalizedPhone, init.captchaToken)
            init to send
        } ?: return
        pendingPhone = normalizedPhone
        pendingCaptchaToken = result.first.captchaToken
        pendingVerificationId = result.second.verificationId
    }

    suspend fun finishSmsLogin(code: String) {
        val verificationId = pendingVerificationId
        val captchaToken = pendingCaptchaToken
        val phoneNumber = pendingPhone
        val normalizedCode = code.trim()
        val session = runBusy("光鸭登录成功") {
            val verify = guangya.loginSmsVerify(verificationId, normalizedCode)
            val session = guangya.loginSmsSignin(
                phoneNumber = phoneNumber,
                code = normalizedCode,
                verificationToken = verify.verificationToken,
                captchaToken = captchaToken,
            )
            session
        } ?: return
        settings = settings.copy(guangya = session)
        store.saveSettings(settings)
        guangya.updateSession(session)
        pendingCaptchaToken = ""
        pendingVerificationId = ""
    }

    suspend fun refreshLibrary() {
        val updated = runBusy("资源库已更新") {
            guangya.updateSession(settings.guangya)
            val mediaLibrary = MediaLibrary(
                guangya = guangya,
                tmdb = TmdbService(settings.tmdbToken),
                store = store,
            )
            mediaLibrary.load(settings.rootId)
        } ?: return
        library = updated
    }

    fun openDetail(group: MediaGroup) {
        selectedGroup = group
    }

    fun closeDetail() {
        selectedGroup = null
    }

    suspend fun play(context: Context, group: MediaGroup, episode: LibraryEpisode? = null) {
        val video = episode?.video ?: group.movieFile ?: group.episodes.firstOrNull()?.video
        if (video == null) {
            message = "没有可播放文件"
            return
        }
        val url = runBusy("") {
            guangya.updateSession(settings.guangya)
            guangya.downloadUrl(video.id)
        } ?: return
        val startMs = store.progress(video.id).takeIf { it > 0L } ?: video.playProgressMs
        val intent = Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_URL, url)
            .putExtra(PlayerActivity.EXTRA_TITLE, episode?.tmdb?.title ?: group.displayTitle)
            .putExtra(PlayerActivity.EXTRA_FILE_ID, video.id)
            .putExtra(PlayerActivity.EXTRA_START_MS, startMs)
        context.startActivity(intent)
    }

    fun imageUrl(path: String, size: String = "w500"): String =
        TmdbService(settings.tmdbToken).imageUrl(path, size)

    private suspend fun <T> runBusy(successMessage: String, block: suspend () -> T): T? {
        loading = true
        message = ""
        return try {
            val result = withContext(Dispatchers.IO) {
                block()
            }
            if (successMessage.isNotBlank()) {
                message = successMessage
            }
            result
        } catch (t: Throwable) {
            message = t.message ?: "操作失败"
            null
        } finally {
            loading = false
        }
    }
}
