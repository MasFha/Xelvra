package com.mdevz.xelvra.core.xray

import android.content.Context
import com.mdevz.xelvra.core.log.AppLog
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object XrayAssetManager {

    const val GEOSITE = "geosite.dat"
    const val GEOIP = "geoip.dat"

    private val requiredAssets =
        listOf(
            GEOSITE,
            GEOIP
        )

    enum class Source {
        BUNDLED,
        UPDATED,
        MISSING
    }

    data class AssetInfo(
        val name: String,
        val source: Source,
        val size: Long,
        val sha256: String?,
        val installedAt: Long = 0L,
        val remoteSha256: String? = null
    ) {
        val updateAvailable: Boolean
            get() =
                sha256 != null &&
                    remoteSha256 != null &&
                    !sha256.equals(
                        remoteSha256,
                        ignoreCase = true
                    )
    }

    data class UpdateCheckResult(
        val geosite: AssetInfo,
        val geoip: AssetInfo,
        val checkedAt: Long
    ) {
        val hasUpdates: Boolean
            get() =
                geosite.updateAvailable ||
                    geoip.updateAvailable
    }

    data class UpdateProgress(
        val assetName: String,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val stage: String
    )

    data class UpdateResult(
        val geosite: AssetInfo,
        val geoip: AssetInfo,
        val changedAssets: Set<String>
    )

    /**
     * Called at the Xray startup boundary.
     *
     * Recovery and bootstrap happen while asset mutation is
     * serialized against core startup.
     */
    fun prepareForCore(
        context: Context
    ) {
        XrayAssetCoordinator.withAssetRead {
            recoverInterruptedTransactions(
                context
            )

            ensureBundledAssetsInternal(
                context
            )
        }
    }

    fun ensureBundledAssets(
        context: Context
    ) {
        
        XrayAssetCoordinator.withAssetMutation {
            recoverInterruptedTransactions(
                context
            )

            ensureBundledAssetsInternal(
                context
            )
        }
    }

    private fun ensureBundledAssetsInternal(
        context: Context
    ) {
        requiredAssets.forEach { name ->
            ensureBundledAsset(
                context,
                name
            )
        }
    }

    fun assetFile(
        context: Context,
        name: String
    ): File {
        require(
            name in requiredAssets
        ) {
            "Unsupported Xray asset: $name"
        }

        return File(
            context.filesDir,
            name
        )
    }

    fun hasAsset(
        context: Context,
        name: String
    ): Boolean {
        val file =
            assetFile(
                context,
                name
            )

        return file.isFile &&
            file.length() > 0L
    }

    fun ensureMetadata(
        context: Context
    ) {
        val store =
            AssetMetadataStore(context)

        if (store.isInitialized()) {
            return
        }

        ensureBundledAssets(context)

        requiredAssets.forEach { name ->
            val target =
                assetFile(
                    context,
                    name
                )

            if (
                !target.isFile ||
                target.length() <= 0L
            ) {
                return@forEach
            }

            val currentHash =
                sha256(target)

            val bundledHash =
                bundledSha256(
                    context,
                    name
                )

            store.setInstalled(
                name = name,
                sha256 = currentHash,
                size = target.length(),
                source =
                    if (
                        currentHash.equals(
                            bundledHash,
                            ignoreCase = true
                        )
                    ) {
                        Source.BUNDLED
                    } else {
                        Source.UPDATED
                    },
                installedAt =
                    target.lastModified()
                        .takeIf { it > 0L }
                        ?: System.currentTimeMillis()
            )
        }

        store.setInitialized()
    }

    fun getAssetInfo(
        context: Context,
        name: String
    ): AssetInfo {
        ensureMetadata(context)

        val target =
            assetFile(
                context,
                name
            )

        if (
            !target.isFile ||
            target.length() <= 0L
        ) {
            return AssetInfo(
                name = name,
                source = Source.MISSING,
                size = 0L,
                sha256 = null
            )
        }

        val metadata =
            AssetMetadataStore(
                context
            ).getAsset(name)

        if (
            metadata.installedSha256 == null ||
            metadata.installedSize != target.length()
        ) {
            val currentHash =
                sha256(target)

            val bundledHash =
                bundledSha256(
                    context,
                    name
                )

            val source =
                if (
                    currentHash.equals(
                        bundledHash,
                        ignoreCase = true
                    )
                ) {
                    Source.BUNDLED
                } else {
                    Source.UPDATED
                }

            AssetMetadataStore(
                context
            ).setInstalled(
                name = name,
                sha256 = currentHash,
                size = target.length(),
                source = source,
                installedAt =
                    target.lastModified()
                        .takeIf { it > 0L }
                        ?: System.currentTimeMillis()
            )

            val refreshed =
                AssetMetadataStore(
                    context
                ).getAsset(name)

            return AssetInfo(
                name = name,
                source = refreshed.source,
                size = target.length(),
                sha256 =
                    refreshed.installedSha256,
                installedAt =
                    refreshed.installedAt,
                remoteSha256 =
                    refreshed.remoteSha256
            )
        }

        return AssetInfo(
            name = name,
            source = metadata.source,
            size = target.length(),
            sha256 =
                metadata.installedSha256,
            installedAt =
                metadata.installedAt,
            remoteSha256 =
                metadata.remoteSha256
        )
    }

    fun getLastCheckedAt(
        context: Context
    ): Long {
        return AssetMetadataStore(
            context
        ).getLastCheckedAt()
    }

    fun checkForUpdates(
        context: Context
    ): UpdateCheckResult {
        ensureMetadata(context)

        val store =
            AssetMetadataStore(context)

        requiredAssets.forEach { name ->
            val checksum =
                downloadChecksum(name)

            store.setRemoteSha256(
                name,
                checksum
            )
        }

        val checkedAt =
            System.currentTimeMillis()

        store.setLastCheckedAt(
            checkedAt
        )

        return UpdateCheckResult(
            geosite =
                getAssetInfo(
                    context,
                    GEOSITE
                ),
            geoip =
                getAssetInfo(
                    context,
                    GEOIP
                ),
            checkedAt = checkedAt
        )
    }

    fun updateAssets(
        context: Context,
        onProgress: (UpdateProgress) -> Unit
    ): UpdateResult {
        return XrayAssetCoordinator.withAssetMutation {
            updateAssetsInternal(
                context,
                onProgress
            )
        }
    }

    private fun updateAssetsInternal(
        context: Context,
        onProgress: (UpdateProgress) -> Unit
    ): UpdateResult {
        recoverInterruptedTransactions(
            context
        )

        ensureMetadata(context)

        val store =
            AssetMetadataStore(context)

        val expectedChecksums =
            requiredAssets.associateWith { name ->
                downloadChecksum(name)
            }

        expectedChecksums.forEach {
            (name, checksum) ->

            store.setRemoteSha256(
                name,
                checksum
            )
        }

        val changed =
            requiredAssets.filter { name ->
                val local =
                    getAssetInfo(
                        context,
                        name
                    ).sha256

                val remote =
                    expectedChecksums
                        .getValue(name)

                local == null ||
                    !local.equals(
                        remote,
                        ignoreCase = true
                    )
            }

        if (changed.isEmpty()) {
            store.setLastCheckedAt(
                System.currentTimeMillis()
            )

            return UpdateResult(
                geosite =
                    getAssetInfo(
                        context,
                        GEOSITE
                    ),
                geoip =
                    getAssetInfo(
                        context,
                        GEOIP
                    ),
                changedAssets =
                    emptySet()
            )
        }

        val workDir =
            File(
                context.filesDir,
                "asset-update"
            )

        if (workDir.exists()) {
            workDir.deleteRecursively()
        }

        check(workDir.mkdirs()) {
            "Unable to create asset update directory"
        }

        val candidates =
            changed.associateWith { name ->
                File(
                    workDir,
                    "$name.part"
                )
            }

        try {
            changed.forEach { name ->
                val candidate =
                    candidates.getValue(name)

                downloadAsset(
                    name = name,
                    destination = candidate,
                    onProgress = onProgress
                )

                onProgress(
                    UpdateProgress(
                        assetName = name,
                        downloadedBytes =
                            candidate.length(),
                        totalBytes =
                            candidate.length(),
                        stage =
                            "Verifying SHA-256"
                    )
                )

                val actual =
                    sha256(candidate)

                val expected =
                    expectedChecksums
                        .getValue(name)

                check(
                    actual.equals(
                        expected,
                        ignoreCase = true
                    )
                ) {
                    "SHA-256 mismatch for $name"
                }
            }

            commitCandidates(
                context = context,
                candidates = candidates
            )

            val installedAt =
                System.currentTimeMillis()

            changed.forEach { name ->
                val target =
                    assetFile(
                        context,
                        name
                    )

                store.setInstalled(
                    name = name,
                    sha256 =
                        expectedChecksums
                            .getValue(name),
                    size = target.length(),
                    source = Source.UPDATED,
                    installedAt =
                        installedAt
                )
            }

            store.setLastCheckedAt(
                installedAt
            )

            return UpdateResult(
                geosite =
                    getAssetInfo(
                        context,
                        GEOSITE
                    ),
                geoip =
                    getAssetInfo(
                        context,
                        GEOIP
                    ),
                changedAssets =
                    changed.toSet()
            )
        } finally {
            workDir.deleteRecursively()
        }
    }

    fun restoreBundledAssets(
        context: Context
    ) {
        XrayAssetCoordinator.withAssetMutation {
            restoreBundledAssetsInternal(
                context
            )
        }
    }

    private fun restoreBundledAssetsInternal(
        context: Context
    ) {
        recoverInterruptedTransactions(
            context
        )

        ensureMetadata(context)

        requiredAssets.forEach { name ->
            installBundledAsset(
                context = context,
                name = name,
                replaceExisting = true
            )
        }

        val store =
            AssetMetadataStore(context)

        val installedAt =
            System.currentTimeMillis()

        requiredAssets.forEach { name ->
            val target =
                assetFile(
                    context,
                    name
                )

            store.setInstalled(
                name = name,
                sha256 =
                    sha256(target),
                size = target.length(),
                source = Source.BUNDLED,
                installedAt = installedAt
            )
        }

        AppLog.xray(
            "Bundled Xray assets restored"
        )
    }

    private fun recoverInterruptedTransactions(
        context: Context
    ) {
        val workDir =
            File(
                context.filesDir,
                "asset-update"
            )

        if (workDir.exists()) {
            workDir.deleteRecursively()
        }

        requiredAssets.forEach { name ->
            val target =
                assetFile(
                    context,
                    name
                )

            val backup =
                File(
                    context.filesDir,
                    "$name.previous"
                )

            val bootstrap =
                File(
                    context.filesDir,
                    "$name.bootstrap"
                )

            if (bootstrap.exists()) {
                bootstrap.delete()
            }

            if (backup.exists()) {
                if (
                    target.isFile &&
                    target.length() > 0L
                ) {

                    backup.delete()
                } else {
                    if (target.exists()) {
                        target.delete()
                    }

                    check(
                        backup.renameTo(
                            target
                        )
                    ) {
                        "Unable to recover $name"
                    }

                    AppLog.xray(
                        "Recovered interrupted asset: $name"
                    )
                }
            }
        }
    }

    private fun downloadChecksum(
        name: String
    ): String {
        val text =
            downloadText(
                "$RELEASE_BASE/$name.sha256sum"
            )

        val checksum =
            text.trim()
                .split(
                    Regex("\\s+")
                )
                .firstOrNull()
                ?.lowercase()
                .orEmpty()

        check(
            checksum.matches(
                Regex("[0-9a-f]{64}")
            )
        ) {
            "Invalid checksum for $name"
        }

        return checksum
    }

    private fun downloadText(
        url: String
    ): String {
        val connection =
            openConnection(url)

        try {
            checkResponse(connection)

            return connection.inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadAsset(
        name: String,
        destination: File,
        onProgress: (UpdateProgress) -> Unit
    ) {
        val connection =
            openConnection(
                "$RELEASE_BASE/$name"
            )

        try {
            checkResponse(connection)

            val total =
                connection.contentLengthLong

            connection.inputStream
                .buffered(
                    DOWNLOAD_BUFFER_SIZE
                )
                .use { input ->
                    FileOutputStream(
                        destination
                    ).use { output ->
                        val buffer =
                            ByteArray(
                                DOWNLOAD_BUFFER_SIZE
                            )

                        var downloaded =
                            0L

                        var lastPercent =
                            -1

                        var lastDispatchAt =
                            0L

                        while (true) {
                            val count =
                                input.read(buffer)

                            if (count < 0) {
                                break
                            }

                            if (count == 0) {
                                continue
                            }

                            output.write(
                                buffer,
                                0,
                                count
                            )

                            downloaded += count

                            val now =
                                System.currentTimeMillis()

                            val percent =
                                if (total > 0L) {
                                    (
                                        downloaded *
                                            100L /
                                            total
                                        )
                                        .coerceIn(
                                            0L,
                                            100L
                                        )
                                        .toInt()
                                } else {
                                    -1
                                }

                            val shouldDispatch =
                                if (total > 0L) {
                                    percent !=
                                        lastPercent
                                } else {
                                    now -
                                        lastDispatchAt >=
                                        PROGRESS_INTERVAL_MS
                                }

                            if (shouldDispatch) {
                                onProgress(
                                    UpdateProgress(
                                        assetName =
                                            name,
                                        downloadedBytes =
                                            downloaded,
                                        totalBytes =
                                            total,
                                        stage =
                                            "Downloading"
                                    )
                                )

                                lastPercent =
                                    percent

                                lastDispatchAt =
                                    now
                            }
                        }

                        output.fd.sync()

                        onProgress(
                            UpdateProgress(
                                assetName = name,
                                downloadedBytes =
                                    downloaded,
                                totalBytes =
                                    total,
                                stage =
                                    "Downloading"
                            )
                        )
                    }
                }

            check(
                destination.isFile &&
                    destination.length() > 0L
            ) {
                "Downloaded asset is empty: $name"
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun commitCandidates(
        context: Context,
        candidates: Map<String, File>
    ) {
        val backups =
            mutableMapOf<String, File>()

        val committed =
            mutableSetOf<String>()

        try {
            candidates.keys.forEach { name ->
                val target =
                    assetFile(
                        context,
                        name
                    )

                val backup =
                    File(
                        context.filesDir,
                        "$name.previous"
                    )

                if (backup.exists()) {
                    check(backup.delete()) {
                        "Unable to clear backup for $name"
                    }
                }

                if (target.exists()) {
                    check(
                        target.renameTo(
                            backup
                        )
                    ) {
                        "Unable to backup $name"
                    }

                    backups[name] =
                        backup
                }
            }

            candidates.forEach {
                (name, candidate) ->

                val target =
                    assetFile(
                        context,
                        name
                    )

                check(
                    candidate.renameTo(
                        target
                    )
                ) {
                    "Unable to activate $name"
                }

                committed.add(name)
            }

            backups.values.forEach {
                check(
                    !it.exists() ||
                        it.delete()
                ) {
                    "Unable to clear asset backup"
                }
            }

            AppLog.xray(
                "Xray assets updated successfully: " +
                    candidates.keys.joinToString()
            )
        } catch (error: Throwable) {
            committed.forEach { name ->
                assetFile(
                    context,
                    name
                ).delete()
            }

            backups.forEach {
                (name, backup) ->

                if (backup.exists()) {
                    backup.renameTo(
                        assetFile(
                            context,
                            name
                        )
                    )
                }
            }

            throw IllegalStateException(
                "Unable to activate Xray assets",
                error
            )
        }
    }

    private fun openConnection(
        url: String
    ): HttpURLConnection {
        return (
            URL(url)
                .openConnection()
                as HttpURLConnection
            ).apply {
                connectTimeout =
                    NETWORK_TIMEOUT_MS

                readTimeout =
                    NETWORK_TIMEOUT_MS

                instanceFollowRedirects =
                    true

                requestMethod =
                    "GET"

                setRequestProperty(
                    "Accept",
                    "application/octet-stream"
                )

                setRequestProperty(
                    "User-Agent",
                    "Xelvra"
                )
            }
    }

    private fun checkResponse(
        connection: HttpURLConnection
    ) {
        val code =
            connection.responseCode

        check(
            code in 200..299
        ) {
            "HTTP $code while downloading assets"
        }
    }

    fun sha256(
        file: File
    ): String {
        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        file.inputStream()
            .buffered(
                DOWNLOAD_BUFFER_SIZE
            )
            .use { input ->
                val buffer =
                    ByteArray(
                        DOWNLOAD_BUFFER_SIZE
                    )

                while (true) {
                    val read =
                        input.read(buffer)

                    if (read < 0) {
                        break
                    }

                    if (read > 0) {
                        digest.update(
                            buffer,
                            0,
                            read
                        )
                    }
                }
            }

        return digest.digest()
            .joinToString("") {
                "%02x".format(it)
            }
    }

    private fun bundledSha256(
        context: Context,
        name: String
    ): String {
        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            )

        context.assets.open(name)
            .buffered(
                DOWNLOAD_BUFFER_SIZE
            )
            .use { input ->
                val buffer =
                    ByteArray(
                        DOWNLOAD_BUFFER_SIZE
                    )

                while (true) {
                    val read =
                        input.read(buffer)

                    if (read < 0) {
                        break
                    }

                    if (read > 0) {
                        digest.update(
                            buffer,
                            0,
                            read
                        )
                    }
                }
            }

        return digest.digest()
            .joinToString("") {
                "%02x".format(it)
            }
    }

    private fun ensureBundledAsset(
        context: Context,
        name: String
    ) {
        val target =
            assetFile(
                context,
                name
            )

        if (
            target.isFile &&
            target.length() > 0L
        ) {
            AppLog.xray(
                "Asset ready: $name " +
                    "(${target.length()} bytes)"
            )
            return
        }

        installBundledAsset(
            context = context,
            name = name,
            replaceExisting = false
        )
    }

    private fun installBundledAsset(
        context: Context,
        name: String,
        replaceExisting: Boolean
    ) {
        val target =
            assetFile(
                context,
                name
            )

        if (
            !replaceExisting &&
            target.isFile &&
            target.length() > 0L
        ) {
            return
        }

        val temporary =
            File(
                context.filesDir,
                "$name.bootstrap"
            )

        try {
            if (temporary.exists()) {
                temporary.delete()
            }

            context.assets.open(name)
                .use { input ->
                    FileOutputStream(
                        temporary
                    ).use { output ->
                        input.copyTo(
                            output,
                            DOWNLOAD_BUFFER_SIZE
                        )

                        output.fd.sync()
                    }
                }

            check(
                temporary.isFile &&
                    temporary.length() > 0L
            ) {
                "Bundled asset is empty: $name"
            }

            if (target.exists()) {
                check(target.delete()) {
                    "Unable to replace $name"
                }
            }

            check(
                temporary.renameTo(
                    target
                )
            ) {
                "Unable to install bundled asset: $name"
            }

            AppLog.xray(
                "Bundled asset installed: $name " +
                    "(${target.length()} bytes)"
            )
        } catch (error: Throwable) {
            temporary.delete()

            throw IllegalStateException(
                "Failed to install bundled Xray asset: $name",
                error
            )
        }
    }

    private const val RELEASE_BASE =
        "https://github.com/Loyalsoldier/" +
            "v2ray-rules-dat/releases/latest/download"

    private const val NETWORK_TIMEOUT_MS =
        30_000

    private const val DOWNLOAD_BUFFER_SIZE =
        64 * 1024

    private const val PROGRESS_INTERVAL_MS =
        120L
}
