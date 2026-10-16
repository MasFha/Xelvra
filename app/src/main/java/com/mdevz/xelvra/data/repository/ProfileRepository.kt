package com.mdevz.xelvra.data.repository

import com.mdevz.xelvra.data.local.ProfileLocalStorage
import com.mdevz.xelvra.data.local.ProfileStorageResult
import com.mdevz.xelvra.data.model.ServerProfile

class ProfileRepository(
    private val storage: ProfileLocalStorage
) {

    fun getProfiles(): ProfileRepositoryResult {
        return when (val result = storage.read()) {
            is ProfileStorageResult.Success -> {
                ProfileRepositoryResult.Success(
                    result.profiles
                )
            }

            ProfileStorageResult.ReadFailed -> {
                ProfileRepositoryResult.StorageReadFailed
            }

            ProfileStorageResult.WriteFailed -> {
                ProfileRepositoryResult.StorageReadFailed
            }
        }
    }

    fun addProfile(
        profile: ServerProfile
    ): ProfileRepositoryResult {
        return mutate { profiles ->
            profiles + profile
        }
    }

    fun updateProfile(
        profile: ServerProfile
    ): ProfileRepositoryResult {
        val current = readProfiles()
            ?: return ProfileRepositoryResult.StorageReadFailed

        val index = current.indexOfFirst {
            it.id == profile.id
        }

        if (index == -1) {
            return ProfileRepositoryResult.ProfileNotFound
        }

        val updated = current.toMutableList()
        updated[index] = profile

        return writeProfiles(updated)
    }

    fun deleteProfile(
        profileId: String
    ): ProfileRepositoryResult {
        val current = readProfiles()
            ?: return ProfileRepositoryResult.StorageReadFailed

        if (current.none { it.id == profileId }) {
            return ProfileRepositoryResult.ProfileNotFound
        }

        return writeProfiles(
            current.filterNot {
                it.id == profileId
            }
        )
    }

    fun findProfile(
        profileId: String
    ): ServerProfile? {
        val current = readProfiles()
            ?: return null

        return current.firstOrNull {
            it.id == profileId
        }
    }

    private fun mutate(
        operation: (List<ServerProfile>) ->
            List<ServerProfile>
    ): ProfileRepositoryResult {
        val current = readProfiles()
            ?: return ProfileRepositoryResult.StorageReadFailed

        return writeProfiles(
            operation(current)
        )
    }

    private fun readProfiles():
        List<ServerProfile>? {
        return when (val result = storage.read()) {
            is ProfileStorageResult.Success ->
                result.profiles

            ProfileStorageResult.ReadFailed ->
                null

            ProfileStorageResult.WriteFailed ->
                null
        }
    }

    private fun writeProfiles(
        profiles: List<ServerProfile>
    ): ProfileRepositoryResult {
        return when (
            val result = storage.write(profiles)
        ) {
            is ProfileStorageResult.Success -> {
                ProfileRepositoryResult.Success(
                    result.profiles
                )
            }

            ProfileStorageResult.ReadFailed -> {
                ProfileRepositoryResult.StorageWriteFailed
            }

            ProfileStorageResult.WriteFailed -> {
                ProfileRepositoryResult.StorageWriteFailed
            }
        }
    }
}
