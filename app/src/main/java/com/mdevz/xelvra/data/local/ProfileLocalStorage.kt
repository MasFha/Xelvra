package com.mdevz.xelvra.data.local

import android.content.Context
import android.util.AtomicFile
import com.mdevz.xelvra.data.model.ServerProfile
import java.io.File
import java.io.FileOutputStream

class ProfileLocalStorage(
    context: Context
) {

    private val atomicFile = AtomicFile(
        File(
            context.filesDir,
            FILE_NAME
        )
    )

    @Synchronized
    fun read(): ProfileStorageResult {
        val file = atomicFile.baseFile

        if (!file.exists()) {
            return ProfileStorageResult.Success(
                emptyList()
            )
        }

        return try {
            val json = atomicFile.openRead()
                .bufferedReader(Charsets.UTF_8)
                .use { reader ->
                    reader.readText()
                }

            ProfileStorageResult.Success(
                ProfileJsonCodec.decodeProfiles(json)
            )
        } catch (_: Exception) {
            ProfileStorageResult.ReadFailed
        }
    }

    @Synchronized
    fun write(
        profiles: List<ServerProfile>
    ): ProfileStorageResult {
        var output: FileOutputStream? = null

        return try {
            val json =
                ProfileJsonCodec.encodeProfiles(profiles)

            output = atomicFile.startWrite()

            output.write(
                json.toByteArray(Charsets.UTF_8)
            )

            atomicFile.finishWrite(output)

            ProfileStorageResult.Success(
                profiles
            )
        } catch (_: Exception) {
            output?.let { stream ->
                atomicFile.failWrite(stream)
            }

            ProfileStorageResult.WriteFailed
        }
    }

    companion object {
        private const val FILE_NAME =
            "profiles_v1.json"
    }
}
