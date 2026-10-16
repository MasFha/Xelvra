package com.mdevz.xelvra.core.xray

import libv2ray.Libv2ray

object XrayCoreManager {

    sealed interface VersionResult {
        data class Success(
            val version: String
        ) : VersionResult

        data class Failure(
            val error: Throwable
        ) : VersionResult
    }

    fun getVersion(): VersionResult {
        return try {
            Libv2ray.touch()

            val version = Libv2ray.checkVersionX()
                .orEmpty()
                .trim()

            if (version.isBlank()) {
                VersionResult.Failure(
                    IllegalStateException(
                        "Xray core returned an empty version"
                    )
                )
            } else {
                VersionResult.Success(version)
            }
        } catch (error: Throwable) {
            VersionResult.Failure(error)
        }
    }
}
