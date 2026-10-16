package com.mdevz.xelvra.core.xray

import android.content.Context

class AssetMetadataStore(
    context: Context
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    data class AssetMetadata(
        val installedSha256: String?,
        val installedSize: Long,
        val installedAt: Long,
        val source: XrayAssetManager.Source,
        val remoteSha256: String?
    )

    fun getAsset(
        name: String
    ): AssetMetadata {
        validateName(name)

        val source =
            runCatching {
                XrayAssetManager.Source.valueOf(
                    preferences.getString(
                        key(name, "source"),
                        XrayAssetManager.Source.MISSING.name
                    ) ?: XrayAssetManager.Source.MISSING.name
                )
            }.getOrDefault(
                XrayAssetManager.Source.MISSING
            )

        return AssetMetadata(
            installedSha256 =
                preferences.getString(
                    key(name, "installed_sha256"),
                    null
                ),
            installedSize =
                preferences.getLong(
                    key(name, "installed_size"),
                    0L
                ),
            installedAt =
                preferences.getLong(
                    key(name, "installed_at"),
                    0L
                ),
            source = source,
            remoteSha256 =
                preferences.getString(
                    key(name, "remote_sha256"),
                    null
                )
        )
    }

    fun setInstalled(
        name: String,
        sha256: String,
        size: Long,
        source: XrayAssetManager.Source,
        installedAt: Long = System.currentTimeMillis()
    ) {
        validateName(name)

        preferences.edit()
            .putString(
                key(name, "installed_sha256"),
                sha256.lowercase()
            )
            .putLong(
                key(name, "installed_size"),
                size
            )
            .putLong(
                key(name, "installed_at"),
                installedAt
            )
            .putString(
                key(name, "source"),
                source.name
            )
            .apply()
    }

    fun setRemoteSha256(
        name: String,
        sha256: String
    ) {
        validateName(name)

        preferences.edit()
            .putString(
                key(name, "remote_sha256"),
                sha256.lowercase()
            )
            .apply()
    }

    fun clearRemoteSha256(
        name: String
    ) {
        validateName(name)

        preferences.edit()
            .remove(
                key(name, "remote_sha256")
            )
            .apply()
    }

    fun getLastCheckedAt(): Long {
        return preferences.getLong(
            KEY_LAST_CHECKED_AT,
            0L
        )
    }

    fun setLastCheckedAt(
        value: Long
    ) {
        preferences.edit()
            .putLong(
                KEY_LAST_CHECKED_AT,
                value
            )
            .apply()
    }

    fun isInitialized(): Boolean {
        return preferences.getBoolean(
            KEY_INITIALIZED,
            false
        )
    }

    fun setInitialized() {
        preferences.edit()
            .putBoolean(
                KEY_INITIALIZED,
                true
            )
            .apply()
    }

    private fun validateName(
        name: String
    ) {
        require(
            name == XrayAssetManager.GEOSITE ||
                name == XrayAssetManager.GEOIP
        ) {
            "Unsupported Xray asset: $name"
        }
    }

    private fun key(
        name: String,
        suffix: String
    ): String {
        return "asset_${name.replace('.', '_')}_$suffix"
    }

    companion object {
        private const val PREFS_NAME =
            "xelvra_asset_metadata_v1"

        private const val KEY_INITIALIZED =
            "initialized"

        private const val KEY_LAST_CHECKED_AT =
            "last_checked_at"
    }
}
