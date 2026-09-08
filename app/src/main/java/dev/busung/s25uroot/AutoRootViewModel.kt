package dev.busung.s25uroot

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

enum class AutoRootPhase {
    Checking,
    Downloading,
    Exploiting,
    LoadingKernelSu,
    Done,
    Failed,
}

data class AutoRootUiState(
    val phase: AutoRootPhase = AutoRootPhase.Checking,
    val message: String = "",
    val log: String = "",
) {
    val busy: Boolean
        get() = phase in setOf(
            AutoRootPhase.Checking,
            AutoRootPhase.Downloading,
            AutoRootPhase.Exploiting,
            AutoRootPhase.LoadingKernelSu,
        )
}

class AutoRootViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val repository = PayloadRepository(application)
    private val mutableState = MutableStateFlow(AutoRootUiState())
    private var rootJob: Job? = null

    val state: StateFlow<AutoRootUiState> = mutableState.asStateFlow()

    init {
        start()
    }

    fun start() {
        if (rootJob?.isActive == true) return
        rootJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (NativeProbe.isKernelSuActive()) {
                    mutableState.value = AutoRootUiState(
                        phase = AutoRootPhase.Done,
                        message = app.getString(R.string.status_ksu_active),
                        log = app.getString(R.string.status_ksu_active),
                    )
                    return@launch
                }

                setPhase(AutoRootPhase.Checking, app.getString(R.string.status_checking_github))
                val profile = repository.resolveTarget(DeviceSnapshot.current())
                appendLog("[+] Support profile: ${profile.profileId}")

                setPhase(AutoRootPhase.Downloading, app.getString(R.string.status_downloading_payload))
                val payloads = if (repository.hasCachedPayloads(profile.profileId)) {
                    appendLog("[+] Using cached payloads")
                    repository.getCachedPayloads(profile)
                } else {
                    repository.download(profile) { appendLog("[*] $it") }
                }
                appendLog(app.getString(R.string.log_download_verified))

                setPhase(AutoRootPhase.Exploiting, app.getString(R.string.status_exploit_running))
                executeExploit(payloads.exploit, profile.requiresFreshP0Session)

                setPhase(AutoRootPhase.LoadingKernelSu, app.getString(R.string.status_ksu_loading))
                stageAndLoadKsu(payloads.kernelSu)

                setPhase(AutoRootPhase.Done, app.getString(R.string.status_ksu_active))
                appendLog(app.getString(R.string.log_install_complete))
            } catch (error: Throwable) {
                appendLog("[-] ${error.message ?: error.javaClass.simpleName}")
                setPhase(AutoRootPhase.Failed, app.getString(R.string.status_install_failed))
            }
        }
    }

    private suspend fun executeExploit(payload: File, requiresFreshP0Session: Boolean) {
        val logFile = File(app.filesDir, "auto_root.log")
        logFile.delete()
        val helper = File(app.applicationInfo.nativeLibraryDir, "libcve43499root.so")
        val bootToken = readBootToken()
        val cachedP0 = readCachedP0(bootToken)

        val env = mutableMapOf<String, String>()
        env["EXPLOIT_ATTEMPTS"] = if (requiresFreshP0Session) "1" else "24"
        if (!requiresFreshP0Session) {
            env["P0_ATTEMPT_TIMEOUT_SEC"] = "45"
            env["EXPLOIT_ATTEMPT_TIMEOUT_SEC"] = "120"
            cachedP0?.let { env["SLIDE_P0_OFFSET"] = it }
        }

        val processBuilder = ProcessBuilder(
            helper.absolutePath,
            "--run-payload",
            payload.absolutePath,
            helper.absolutePath,
            logFile.absolutePath,
        ).redirectErrorStream(true)
        processBuilder.environment().putAll(env)

        val process = processBuilder.start()
        val captured = StringBuilder()
        val startedAt = SystemClock.elapsedRealtime()
        var lastProgressAt = startedAt
        var lastRawLog = ""

        while (process.isAlive) {
            drainProcessOutput(process, captured)
            val rawLog = if (logFile.exists()) logFile.readText() else ""
            if (rawLog != lastRawLog) {
                if (!requiresFreshP0Session) cacheP0Offset(bootToken, rawLog)
                lastRawLog = rawLog
                lastProgressAt = SystemClock.elapsedRealtime()
                publishExploitLog(rawLog)
            }
            val now = SystemClock.elapsedRealtime()
            if (!requiresFreshP0Session) {
                require(now - lastProgressAt < 90_000L) {
                    app.getString(R.string.error_exploit_stalled)
                }
            }
            require(now - startedAt < 900_000L) {
                app.getString(R.string.error_exploit_timeout)
            }
            delay(250)
        }

        val exitCode = process.waitFor()
        val rawLog = if (logFile.exists()) logFile.readText() else ""
        if (!requiresFreshP0Session) cacheP0Offset(bootToken, rawLog)
        publishExploitLog(rawLog)

        require(exitCode == 0) {
            app.getString(R.string.error_payload_exit, exitCode, "")
        }
        require(rawLog.contains("exploit completed") && rawLog.contains("done=1 root=1")) {
            app.getString(R.string.error_success_marker)
        }
        appendLog(app.getString(R.string.log_bootstrap_root))
    }

    private fun publishExploitLog(rawLog: String) {
        val clean = rawLog.trim()
        if (clean.isBlank()) return
        mutableState.value = mutableState.value.copy(log = clean)
    }

    private suspend fun stageAndLoadKsu(ksudFile: File) {
        val stageCmd = "/system/bin/cp '${ksudFile.absolutePath}' /data/local/tmp/ksud-s25u-kdp && " +
            "/system/bin/cp '${ksudFile.absolutePath}' /data/local/tmp/.ksud-stage && " +
            "/system/bin/chmod 755 /data/local/tmp/ksud-s25u-kdp /data/local/tmp/.ksud-stage"
        val stage = runHelperBlocking("-c", stageCmd)
        if (stage.first != 0) throw RuntimeException("KSU staging failed: ${stage.second}")

        val lateLoad = runHelperBlocking("--late-load")
        if (lateLoad.first != 0) throw RuntimeException("KSU late-load failed (rc=${lateLoad.first}): ${lateLoad.second}")

        val bootToken = readBootToken()
        if (bootToken != null) {
            app.getSharedPreferences("install_receipt", Application.MODE_PRIVATE)
                .edit()
                .putString("kernel_boot_id", bootToken)
                .putBoolean("verified", true)
                .commit()
        }
        appendLog(app.getString(R.string.log_ksu_control_verified))
    }

    private suspend fun runHelperBlocking(vararg args: String): Pair<Int, String> {
        val process = ProcessBuilder(listOf(File(app.applicationInfo.nativeLibraryDir, "libcve43499root.so").absolutePath) + args)
            .redirectErrorStream(true)
            .start()
        val buf = StringBuilder()
        val deadline = SystemClock.elapsedRealtime() + 120_000L
        while (process.isAlive) {
            drainProcessOutput(process, buf)
            if (SystemClock.elapsedRealtime() > deadline) {
                process.destroyForcibly()
                return Pair(-1, "Helper timed out")
            }
            delay(100)
        }
        drainProcessOutput(process, buf)
        return Pair(process.waitFor(), buf.toString().trim())
    }

    private fun drainProcessOutput(process: Process, buffer: StringBuilder) {
        try {
            drainStream(process.inputStream, buffer)
            drainStream(process.errorStream, buffer)
        } catch (_: Throwable) {}
    }

    private fun drainStream(stream: InputStream, buffer: StringBuilder) {
        val data = ByteArray(4096)
        while (stream.available() > 0) {
            val count = stream.read(data)
            if (count <= 0) break
            buffer.append(String(data, 0, count, Charsets.UTF_8))
        }
    }

    private fun readBootToken(): String? = runCatching {
        File("/proc/sys/kernel/random/boot_id")
            .readText(Charsets.US_ASCII)
            .trim()
            .takeIf(String::isNotBlank)
    }.getOrNull()

    private fun readCachedP0(bootToken: String?): String? {
        if (bootToken == null) return null
        val prefs = app.getSharedPreferences("p0_cache", Application.MODE_PRIVATE)
        if (prefs.getString("kernel_boot_id", null) != bootToken) return null
        return prefs.getString("offset", null)
    }

    private fun cacheP0Offset(bootToken: String?, log: String) {
        if (bootToken == null) return
        val match = P0_OFFSET_PATTERN.findAll(log).lastOrNull() ?: return
        val offset = match.groupValues[1].toLongOrNull(16) ?: return
        if (offset !in 0..0x1f0000L || offset and 0xffffL != 0L) return
        val value = "0x${offset.toString(16)}"
        val prefs = app.getSharedPreferences("p0_cache", Application.MODE_PRIVATE)
        if (prefs.getString("kernel_boot_id", null) == bootToken &&
            prefs.getString("offset", null) == value
        ) return
        prefs.edit()
            .putString("kernel_boot_id", bootToken)
            .putString("offset", value)
            .apply()
    }

    private fun setPhase(phase: AutoRootPhase, message: String) {
        mutableState.value = mutableState.value.copy(phase = phase, message = message)
        appendLog("[*] $message")
    }

    private fun appendLog(line: String) {
        val clean = line.trim()
        if (clean.isBlank()) return
        mutableState.value = mutableState.value.copy(
            log = (mutableState.value.log + "\n" + clean).trim(),
        )
    }

    companion object {
        private val P0_OFFSET_PATTERN = Regex("slide-kaslr-ok[^\\n]*slide=([0-9a-fA-F]{16})")
    }
}
