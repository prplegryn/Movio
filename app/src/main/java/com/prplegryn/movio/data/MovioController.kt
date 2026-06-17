package com.prplegryn.movio.data

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    var rootFolders by mutableStateOf<List<CloudFolder>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var syncProgressPercent by mutableIntStateOf(0)
        private set
    var syncProgressLabel by mutableStateOf("")
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

    val loggedIn: Boolean
        get() = settings.guangya.accessToken.isNotBlank()

    fun updateRootId(value: String) {
        settings = settings.copy(rootId = value.ifBlank { "*" })
        store.saveSettings(settings)
    }

    fun updateTmdbToken(value: String) {
        settings = settings.copy(tmdbToken = value.trim())
        store.saveSettings(settings)
    }

    fun dismissMessage() {
        message = ""
    }

    fun logout() {
        settings = settings.copy(guangya = GuangyaSession(), rootId = "*")
        store.saveSettings(settings)
        guangya.updateSession(settings.guangya)
        rootFolders = emptyList()
        library = emptyList()
        pendingCaptchaToken = ""
        pendingVerificationId = ""
        pendingPhone = ""
        message = "已退出登录"
    }

    suspend fun requestSms(phone: String) {
        val normalizedPhone = phone.trim()
        if (normalizedPhone.isBlank() || normalizedPhone == "+86") {
            message = "请输入手机号"
            return
        }
        val result = runBusy("验证码已发送", "验证码发送失败") {
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
        if (verificationId.isBlank() || captchaToken.isBlank()) {
            message = "请先发送验证码"
            return
        }
        if (normalizedCode.isBlank()) {
            message = "请输入短信验证码"
            return
        }
        val session = runBusy("光鸭登录成功", "光鸭登录失败") {
            val verify = guangya.loginSmsVerify(verificationId, normalizedCode)
            val session = guangya.loginSmsSignin(
                phoneNumber = phoneNumber,
                code = normalizedCode,
                verificationToken = verify.verificationToken,
                captchaToken = captchaToken,
            )
            guangya.updateSession(session)
            session
        } ?: return
        settings = settings.copy(guangya = session)
        store.saveSettings(settings)
        guangya.updateSession(session)
        pendingCaptchaToken = ""
        pendingVerificationId = ""
        try {
            val folders = withContext(Dispatchers.IO) {
                guangya.listRootFolders()
            }
            persistGuangyaSession()
            rootFolders = rootOptions(folders)
        } catch (t: Throwable) {
            rootFolders = rootOptions(emptyList())
            message = "光鸭登录成功，目录获取失败：${t.message ?: "未知错误"}"
        }
    }

    suspend fun refreshRootFolders() {
        if (!loggedIn) {
            rootFolders = emptyList()
            return
        }
        val folders = runBusy("", "目录获取失败") {
            guangya.updateSession(settings.guangya)
            guangya.listRootFolders()
        } ?: return
        persistGuangyaSession()
        rootFolders = rootOptions(folders)
    }

    suspend fun refreshLibrary() {
        if (!loggedIn) {
            message = "请先登录光鸭"
            return
        }
        if (settings.tmdbToken.isBlank()) {
            message = "请先配置 TMDb Read Access Token"
            return
        }
        syncProgressPercent = 0
        syncProgressLabel = "准备同步"
        val updated = runBusy("资源库已更新", "资源库同步失败") {
            guangya.updateSession(settings.guangya)
            val mediaLibrary = MediaLibrary(
                guangya = guangya,
                tmdb = TmdbService(settings.tmdbToken),
                store = store,
            )
            mediaLibrary.load(settings.rootId) { percent, label ->
                withContext(Dispatchers.Main) {
                    syncProgressPercent = percent.coerceIn(0, 100)
                    syncProgressLabel = label
                }
            }
        } ?: run {
            syncProgressLabel = "同步失败"
            return
        }
        persistGuangyaSession()
        library = updated
        syncProgressPercent = 100
        syncProgressLabel = "同步完成"
    }

    fun openDetail(group: MediaGroup) {
        selectedGroup = group
    }

    fun closeDetail() {
        selectedGroup = null
    }

    suspend fun play(context: Context, group: MediaGroup, episode: LibraryEpisode? = null) {
        val video = episode?.video
            ?: group.movieFile
            ?: group.episodes.firstOrNull()?.video
            ?: group.unmatchedFiles.firstOrNull()
        if (video == null) {
            message = "没有可播放文件"
            return
        }
        playVideo(context, video, episode?.tmdb?.title ?: group.displayTitle)
    }

    suspend fun playVideo(context: Context, video: CloudVideo, title: String) {
        val url = runBusy("", "获取播放地址失败") {
            guangya.updateSession(settings.guangya)
            guangya.downloadUrl(video.id)
        } ?: return
        persistGuangyaSession()
        val startMs = store.progress(video.id).takeIf { it > 0L } ?: video.playProgressMs
        val intent = Intent(context, PlayerActivity::class.java)
            .putExtra(PlayerActivity.EXTRA_URL, url)
            .putExtra(PlayerActivity.EXTRA_TITLE, title)
            .putExtra(PlayerActivity.EXTRA_FILE_NAME, video.name)
            .putExtra(PlayerActivity.EXTRA_FILE_ID, video.id)
            .putExtra(PlayerActivity.EXTRA_START_MS, startMs)
        context.startActivity(intent)
    }

    fun imageUrl(path: String, size: String = "w500"): String =
        TmdbService(settings.tmdbToken).imageUrl(path, size)

    private fun rootOptions(folders: List<CloudFolder>): List<CloudFolder> =
        listOf(CloudFolder("*", "全部视频")) + folders

    private fun persistGuangyaSession() {
        val current = guangya.currentSession()
        if (current != settings.guangya) {
            settings = settings.copy(guangya = current)
            store.saveSettings(settings)
        }
    }

    private suspend fun <T> runBusy(
        successMessage: String,
        failurePrefix: String = "操作失败",
        block: suspend () -> T,
    ): T? {
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
            message = "$failurePrefix：${t.message ?: "未知错误"}"
            null
        } finally {
            loading = false
        }
    }
}
