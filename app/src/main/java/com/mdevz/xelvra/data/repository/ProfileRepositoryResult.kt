package com.mdevz.xelvra.data.repository

import com.mdevz.xelvra.data.model.ServerProfile

sealed interface ProfileRepositoryResult {
    data class Success(
        val profiles: List<ServerProfile>
    ) : ProfileRepositoryResult

    data object ProfileNotFound :
        ProfileRepositoryResult

    data object StorageReadFailed :
        ProfileRepositoryResult

    data object StorageWriteFailed :
        ProfileRepositoryResult
}
