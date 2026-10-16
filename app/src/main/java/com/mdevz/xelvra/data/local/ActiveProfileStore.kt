package com.mdevz.xelvra.data.local

import android.content.Context

class ActiveProfileStore(
    context: Context
) {

    private val preferences =
        context.applicationContext.getSharedPreferences(
            PREFERENCES_NAME,
            Context.MODE_PRIVATE
        )

    fun getActiveProfileId(): String? {
        return preferences
            .getString(KEY_ACTIVE_PROFILE_ID, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun setActiveProfileId(
        profileId: String
    ): Boolean {
        if (profileId.isBlank()) {
            return false
        }

        return preferences
            .edit()
            .putString(
                KEY_ACTIVE_PROFILE_ID,
                profileId
            )
            .commit()
    }

    fun clearActiveProfile(): Boolean {
        return preferences
            .edit()
            .remove(KEY_ACTIVE_PROFILE_ID)
            .commit()
    }

    companion object {
        private const val PREFERENCES_NAME =
            "xelvra_state"

        private const val KEY_ACTIVE_PROFILE_ID =
            "active_profile_id"
    }
}
