package dev.busung.s25uroot

import android.content.Context
import android.system.Os
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class VerifiedPayloads(
    val profile: TargetProfile,
    val exploit: File,
    val kernelSu: File,
)

class PayloadRepository(private val context: Context) {
    fun loadTargets(): List<TargetProfile> {
        val commit = resolveMainCommit()
        val manifestBytes = downloadBytes(rawUrl(commit, "support/targets-v3.json"), MAX_MANIFEST_BYTES)
        cacheManifest(commit, manifestBytes)
        return SupportManifest.parse(manifestBytes).targets.map { profile -> profile.copy(
            exploit = profile.exploit.copy(url = pinArtifactUrl(profile.exploit.url, commit)),
            kernelSu = profile.kernelSu.copy(url = pinArtifactUrl(profile.kernelSu.url, commit)),
        ) }
    }

    fun loadTargetsOffline(): List<TargetProfile>? {
        val cached = readCachedManifest() ?: return null
        return SupportManifest.parse(cached.bytes).targets.map { profile -> profile.copy(
            exploit = profile.exploit.copy(url = pinArtifactUrl(profile.exploit.url, cached.commit)),
            kernelSu = profile.kernelSu.copy(url = pinArtifactUrl(profile.kernelSu.url, cached.commit)),
        ) }
    }

    fun resolveTarget(snapshot: DeviceSnapshot): TargetProfile {
        val online = try { loadTargets() } catch (_: Throwable) { null }
        if (online != null) return online.firstOrNull { it.matches(snapshot) }
            ?: error(context.getString(R.string.repo_no_profile))
        val offline = loadTargetsOffline()
            ?: error(context.getString(R.string.repo_no_profile))
        return offline.firstOrNull { it.matches(snapshot) }
            ?: error(context.getString(R.string.repo_no_profile))
    }

    fun resolveTarget(profileId: String): TargetProfile {
        val online = try { loadTargets() } catch (_: Throwable) { null }
        if (online != null) return online.firstOrNull { it.profileId == profileId }
            ?: error(context.getString(R.string.repo_profile_missing, profileId))
        val offline = loadTargetsOffline()
            ?: error(context.getString(R.string.repo_profile_missing, profileId))
        return offline.firstOrNull { it.profileId == profileId }
            ?: error(context.getString(R.string.repo_profile_missing, profileId))
    }

    fun download(profile: TargetProfile, onProgress: (String) -> Unit): VerifiedPayloads {
        val directory = File(context.filesDir, "payloads/${profile.profileId}").apply { mkdirs() }
        val exploit = if (profile.profileId == "testing-a55" && TESTING_PAYLOAD.exists()) {
            onProgress("Using local testing payload")
            copyTestingPayload(TESTING_PAYLOAD, File(directory, "cve-2026-43499-app.so"))
        } else {
            downloadOrUseCached(
                profile.exploit,
                File(directory, "cve-2026-43499-app.so"),
                context.getString(R.string.artifact_exploit),
                onProgress,
            )
        }
        val kernelSu = downloadOrUseCached(
            profile.kernelSu,
            File(directory, "ksud-s25u-kdp"),
            context.getString(R.string.artifact_kernelsu),
            onProgress,
        )
        Os.chmod(exploit.absolutePath, 0b100100100)
        Os.chmod(kernelSu.absolutePath, 0b100100100)
        return VerifiedPayloads(profile, exploit, kernelSu)
    }

    fun hasCachedPayloads(profileId: String): Boolean {
        val directory = File(context.filesDir, "payloads/$profileId")
        return File(directory, "cve-2026-43499-app.so").exists() &&
            File(directory, "ksud-s25u-kdp").exists()
    }

    fun getCachedPayloads(profile: TargetProfile): VerifiedPayloads {
        val directory = File(context.filesDir, "payloads/${profile.profileId}")
        val exploit = File(directory, "cve-2026-43499-app.so")
        val kernelSu = File(directory, "ksud-s25u-kdp")
        require(exploit.exists() && kernelSu.exists()) { "Cached payloads not found" }
        return VerifiedPayloads(profile, exploit, kernelSu)
    }

    private fun downloadOrUseCached(
        artifact: RemoteArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File {
        if (destination.exists() && destination.length() == artifact.size) {
            onProgress(context.getString(R.string.repo_using_cached, label))
            return destination
        }
        return try {
            downloadArtifact(artifact, destination, label, onProgress)
        } catch (e: Throwable) {
            if (destination.exists()) {
                onProgress(context.getString(R.string.repo_using_cached, label))
                destination
            } else {
                throw e
            }
        }
    }

    fun checkForUpdate(): UpdateStatus {
        val cached = readCachedManifest() ?: return UpdateStatus.UPDATE_AVAILABLE
        return try {
            val url = "$RAW_REPOSITORY/main/support/targets-v3.json"
            val latestBytes = downloadBytes(url, MAX_MANIFEST_BYTES)
            if (!latestBytes.contentEquals(cached.bytes)) {
                try {
                    val commit = resolveMainCommit()
                    cacheManifest(commit, latestBytes)
                } catch (_: Throwable) {
                    cacheManifest("main", latestBytes)
                }
                UpdateStatus.UPDATE_AVAILABLE
            } else {
                UpdateStatus.UP_TO_DATE
            }
        } catch (_: Throwable) {
            UpdateStatus.OFFLINE
        }
    }

    fun getCachedVersion(): String? {
        val cached = readCachedManifest() ?: return null
        return cached.commit.take(8)
    }

    private fun cacheManifest(commit: String, bytes: ByteArray) {
        val dir = File(context.filesDir, "cache").apply { mkdirs() }
        File(dir, "manifest_commit.txt").writeText(commit)
        File(dir, "manifest.json").writeBytes(bytes)
    }

    private fun readCachedManifest(): CachedManifest? {
        val dir = File(context.filesDir, "cache")
        val commitFile = File(dir, "manifest_commit.txt")
        val manifestFile = File(dir, "manifest.json")
        if (!commitFile.exists() || !manifestFile.exists()) return null
        return CachedManifest(commitFile.readText().trim(), manifestFile.readBytes())
    }

    private data class CachedManifest(val commit: String, val bytes: ByteArray)

    enum class UpdateStatus { UP_TO_DATE, UPDATE_AVAILABLE, OFFLINE }

    private fun downloadArtifact(
        artifact: RemoteArtifact,
        destination: File,
        label: String,
        onProgress: (String) -> Unit,
    ): File {
        onProgress(context.getString(R.string.repo_downloading, label))
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val connection = open(artifact.url)
        require(connection.contentLengthLong == -1L || connection.contentLengthLong == artifact.size) {
            context.getString(R.string.repo_size_mismatch, label)
        }
        var total = 0L
        connection.inputStream.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= artifact.size) {
                        context.getString(R.string.repo_size_exceeded, label)
                    }
                    output.write(buffer, 0, count)
                }
                output.fd.sync()
            }
        }
        connection.disconnect()
        require(total == artifact.size) { context.getString(R.string.repo_incomplete, label) }
        if (destination.exists()) destination.delete()
        require(temporary.renameTo(destination)) {
            context.getString(R.string.repo_finalize_failed, label)
        }
        onProgress(context.getString(R.string.repo_verified, label))
        return destination
    }

    private fun resolveMainCommit(): String {
        val response = downloadBytes(COMMIT_API_URL, MAX_COMMIT_RESPONSE_BYTES)
        val commit = JSONObject(response.toString(Charsets.UTF_8))
            .getJSONObject("object")
            .getString("sha")
        require(commit.matches(Regex("[0-9a-f]{40}"))) { context.getString(R.string.repo_commit_invalid) }
        return commit
    }

    private fun rawUrl(commit: String, path: String) = "$RAW_REPOSITORY/$commit/$path"

    private fun pinArtifactUrl(url: String, commit: String): String {
        val artifactPath = when {
            url.startsWith(MUTABLE_RAW_PREFIX) -> url.removePrefix(MUTABLE_RAW_PREFIX)
            url.startsWith(LEGACY_RAW_PREFIX) -> url.removePrefix(LEGACY_RAW_PREFIX)
            else -> null
        } ?: error(context.getString(R.string.repo_url_invalid))
        return "$RAW_REPOSITORY/$commit/$artifactPath"
    }

    private fun downloadBytes(url: String, maximum: Int): ByteArray {
        val connection = open(url)
        val bytes = connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maximum) {
                    context.getString(R.string.repo_response_too_large)
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        connection.disconnect()
        return bytes
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "S25URoot/${BuildConfig.VERSION_NAME}")
            connect()
            require(responseCode == HttpURLConnection.HTTP_OK) { "HTTP $responseCode" }
        }

    private fun copyTestingPayload(src: File, dst: File): File {
        if (dst.exists()) dst.delete()
        src.copyTo(dst)
        return dst
    }

    companion object {
        private val TESTING_PAYLOAD = File("/data/local/tmp/cve-2026-43499-app.so")
        private const val COMMIT_API_URL =
            "https://api.github.com/repos/axolot33l/Root-My-Galaxy-Payloads/git/ref/heads/main"
        private const val RAW_REPOSITORY =
            "https://raw.githubusercontent.com/axolot33l/Root-My-Galaxy-Payloads"
        private const val MUTABLE_RAW_PREFIX = "$RAW_REPOSITORY/main/"
        private const val LEGACY_RAW_PREFIX =
            "https://raw.githubusercontent.com/BuSung-dev/Root-My-Galaxy-Payloads/main/"
        private const val MAX_COMMIT_RESPONSE_BYTES = 16 * 1024
        private const val MAX_MANIFEST_BYTES = 256 * 1024
    }
}
