package com.mdevz.xelvra.data.local

import com.mdevz.xelvra.data.model.ServerProfile

sealed interface ProfileStorageResult {
    data class Success(
        val profiles: List<ServerProfile>
    ) : ProfileStorageResult

    data object ReadFailed : ProfileStorageResult

    data object WriteFailed : ProfileStorageResult
}
