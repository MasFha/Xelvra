package com.mdevz.xelvra.ui.profiles

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.snackbar.Snackbar
import com.mdevz.xelvra.R
import com.mdevz.xelvra.data.local.ActiveProfileStore
import com.mdevz.xelvra.data.local.ProfileLocalStorage
import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolType
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.repository.ProfileRepository
import com.mdevz.xelvra.data.repository.ProfileRepositoryResult
import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.mdevz.xelvra.network.latency.EndpointKey
import com.mdevz.xelvra.network.latency.EndpointLatencyManager
import com.mdevz.xelvra.network.latency.EndpointLatencyState
import com.mdevz.xelvra.protocol.uri.ProfileUriCodec
import com.mdevz.xelvra.ui.editor.ProfileEditorActivity
import java.util.UUID

class ProfilesFragment : Fragment(
    R.layout.fragment_profiles
) {

    private lateinit var repository: ProfileRepository
    private lateinit var activeProfileStore: ActiveProfileStore


    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(
            view,
            savedInstanceState
        )

        repository = ProfileRepository(
            ProfileLocalStorage(
                requireContext().applicationContext
            )
        )

        activeProfileStore = ActiveProfileStore(
            requireContext().applicationContext
        )

        view.findViewById<View>(
            R.id.test_all_latency_button
        ).setOnClickListener {
            testAllProfileLatency()
        }

        view.findViewById<View>(
            R.id.add_profile_button
        ).setOnClickListener {
            showAddProfileDialog()
        }
    }

    override fun onResume() {
        super.onResume()

        if (::repository.isInitialized) {
            loadProfiles()
        }
    }

    private fun loadProfiles() {
        when (
            val result = repository.getProfiles()
        ) {
            is ProfileRepositoryResult.Success -> {
                renderProfiles(result.profiles)
            }

            ProfileRepositoryResult.StorageReadFailed,
            ProfileRepositoryResult.StorageWriteFailed,
            ProfileRepositoryResult.ProfileNotFound -> {
                showLoadError()
            }
        }
    }

    private fun renderProfiles(
        profiles: List<ServerProfile>
    ) {
        val root = view ?: return

        val emptyCard =
            root.findViewById<View>(
                R.id.empty_profiles_card
            )

        val list =
            root.findViewById<RecyclerView>(
                R.id.profiles_list
            )

        if (list.layoutManager == null) {
            list.layoutManager =
                LinearLayoutManager(
                    requireContext()
                )

            list.itemAnimator = null
        }

        if (profiles.isEmpty()) {
            emptyCard.visibility =
                View.VISIBLE

            list.visibility =
                View.GONE

            list.adapter = null
            return
        }

        emptyCard.visibility =
            View.GONE

        list.visibility =
            View.VISIBLE

        list.adapter =
            ProfilesAdapter(
                profiles = profiles,
                activeProfileId =
                    activeProfileStore
                        .getActiveProfileId()
            )
    }

    private inner class ProfilesAdapter(
        private val profiles:
            List<ServerProfile>,
        private val activeProfileId:
            String?
    ) : RecyclerView.Adapter<
        ProfilesAdapter.ProfileViewHolder
    >() {

        override fun onCreateViewHolder(
            parent: android.view.ViewGroup,
            viewType: Int
        ): ProfileViewHolder {
            val itemView =
                layoutInflater.inflate(
                    R.layout.item_profile,
                    parent,
                    false
                )

            return ProfileViewHolder(
                itemView
            )
        }

        override fun getItemCount(): Int {
            return profiles.size
        }

        override fun onBindViewHolder(
            holder: ProfileViewHolder,
            position: Int
        ) {
            holder.bind(
                profile = profiles[position],
                isActive =
                    profiles[position].id ==
                        activeProfileId,
                hasTopMargin =
                    position > 0
            )
        }

        inner class ProfileViewHolder(
            itemView: View
        ) : RecyclerView.ViewHolder(
            itemView
        ) {

            private val card =
                itemView.findViewById<
                    MaterialCardView
                >(
                    R.id.profile_card
                )

            private val activeIndicator =
                itemView.findViewById<View>(
                    R.id.profile_active_indicator
                )

            private val avatar =
                itemView.findViewById<TextView>(
                    R.id.profile_avatar
                )

            private val name =
                itemView.findViewById<TextView>(
                    R.id.profile_name
                )

            private val protocol =
                itemView.findViewById<TextView>(
                    R.id.profile_protocol
                )

            private val latency =
                itemView.findViewById<TextView>(
                    R.id.profile_latency
                )

            private val overflow =
                itemView.findViewById<View>(
                    R.id.profile_overflow
                )

            fun bind(
                profile: ServerProfile,
                isActive: Boolean,
                hasTopMargin: Boolean
            ) {
                val params =
                    card.layoutParams

                if (
                    params is RecyclerView.LayoutParams
                ) {
                    params.topMargin =
                        if (hasTopMargin) {
                            dp(8)
                        } else {
                            0
                        }

                    card.layoutParams =
                        params
                }

                card.setCardBackgroundColor(
                    MaterialColors.getColor(
                        card,
                        if (isActive) {
                            com.google.android.material.R.attr
                                .colorSurfaceContainer
                        } else {
                            com.google.android.material.R.attr
                                .colorSurfaceContainerLow
                        }
                    )
                )

                card.strokeColor =
                    MaterialColors.getColor(
                        card,
                        if (isActive) {
                            androidx.appcompat.R.attr
                                .colorPrimary
                        } else {
                            com.google.android.material.R.attr
                                .colorOutlineVariant
                        }
                    )

                card.strokeWidth =
                    dp(if (isActive) 2 else 1)

                activeIndicator.visibility =
                    if (isActive) {
                        View.VISIBLE
                    } else {
                        View.INVISIBLE
                    }

                val firstLetter =
                    profile.remarks
                        .trim()
                        .firstOrNull()
                        ?.uppercaseChar()
                        ?.toString()
                        ?: "?"

                avatar.text =
                    firstLetter

                avatar.contentDescription =
                    profile.remarks

                avatar.setTextColor(
                    MaterialColors.getColor(
                        card,
                        if (isActive) {
                            com.google.android.material.R.attr
                                .colorOnPrimary
                        } else {
                            com.google.android.material.R.attr
                                .colorOnSecondaryContainer
                        }
                    )
                )

                avatar.background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.OVAL

                        setColor(
                            MaterialColors.getColor(
                                card,
                                if (isActive) {
                                    androidx.appcompat.R.attr
                                        .colorPrimary
                                } else {
                                    com.google.android.material.R.attr
                                        .colorSecondaryContainer
                                }
                            )
                        )
                    }

                name.text =
                    profile.remarks

                protocol.text =
                    protocolLabel(
                        profile.protocol
                    )

                val latencyState =
                    EndpointLatencyManager
                        .getState(
                            EndpointKey.from(
                                address =
                                    profile.address,
                                port =
                                    profile.port
                            )
                        )

                renderLatencyState(
                    textView = latency,
                    state = latencyState
                )

                overflow.setOnClickListener {
                    showProfileMenu(
                        anchor = it,
                        profile = profile
                    )
                }

                card.setOnClickListener {
                    selectProfile(
                        profile = profile,
                        isActive = isActive
                    )
                }
            }
        }
    }

    private fun isVpnProfileMutationBlocked(): Boolean {
        return VpnRuntimeManager.isSessionActive()
    }

    private fun showVpnRunningProfileWarning() {
        if (!isAdded) {
            return
        }

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(
                R.string.vpn_running_profile_title
            )
            .setMessage(
                R.string.vpn_running_profile_message
            )
            .setPositiveButton(
                R.string.action_ok,
                null
            )
            .show()
    }

    private fun selectProfile(
        profile: ServerProfile,
        isActive: Boolean
    ) {
        if (isActive) {
            return
        }

        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        if (
            activeProfileStore.setActiveProfileId(
                profile.id
            )
        ) {
            Snackbar.make(
                requireView(),
                getString(
                    R.string.profile_selected,
                    profile.remarks
                ),
                Snackbar.LENGTH_SHORT
            ).show()

            loadProfiles()
        } else {
            Snackbar.make(
                requireView(),
                R.string.profile_select_failed,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun showProfileMenu(
        anchor: View,
        profile: ServerProfile
    ) {
        PopupMenu(
            requireContext(),
            anchor
        ).apply {
            menu.add(
                0,
                MENU_EDIT,
                0,
                R.string.edit_profile
            ).setIcon(R.drawable.ic_edit)

            menu.add(
                0,
                MENU_DUPLICATE,
                1,
                R.string.duplicate
            ).setIcon(R.drawable.ic_duplicate)

            menu.add(
                0,
                MENU_SHARE,
                2,
                R.string.share_uri
            ).setIcon(R.drawable.ic_link)

            menu.add(
                0,
                MENU_LATENCY,
                3,
                R.string.test_latency
            ).setIcon(R.drawable.ic_speed)

            menu.add(
                0,
                MENU_DELETE,
                4,
                R.string.delete
            ).setIcon(R.drawable.ic_delete)

            setForceShowIcon(true)

            setOnMenuItemClickListener { item ->
                if (
                    item.itemId != MENU_LATENCY &&
                    isVpnProfileMutationBlocked()
                ) {
                    showVpnRunningProfileWarning()
                    return@setOnMenuItemClickListener true
                }

                when (item.itemId) {
                    MENU_EDIT -> {
                        openProfileEditor(profile)
                        true
                    }

                    MENU_DUPLICATE -> {
                        duplicateProfile(profile)
                        true
                    }

                    MENU_SHARE -> {
                        copyProfileUri(profile)
                        true
                    }

                    MENU_LATENCY -> {
                        testProfileLatency(profile)
                        true
                    }

                    MENU_DELETE -> {
                        confirmDeleteProfile(profile)
                        true
                    }

                    else -> false
                }
            }

            show()
        }
    }

    private fun testProfileLatency(
        profile: ServerProfile
    ) {
        EndpointLatencyManager.test(
            address = profile.address,
            port = profile.port
        ) {
            if (isAdded) {
                refreshLatencyUi()
            }
        }
    }

    private fun testAllProfileLatency() {
        val profiles =
            when (
                val result =
                    repository.getProfiles()
            ) {
                is ProfileRepositoryResult.Success -> {
                    result.profiles
                }

                ProfileRepositoryResult.ProfileNotFound,
                ProfileRepositoryResult.StorageReadFailed,
                ProfileRepositoryResult.StorageWriteFailed -> {
                    showLoadError()
                    return
                }
            }

        if (profiles.isEmpty()) {
            return
        }

        val endpoints =
            profiles.map {
                it.address to it.port
            }

        EndpointLatencyManager.testAll(
            endpoints = endpoints
        ) {
            if (isAdded) {
                refreshLatencyUi()
            }
        }
    }

    private fun refreshLatencyUi() {
        val root = view ?: return

        root.findViewById<RecyclerView>(
            R.id.profiles_list
        ).adapter?.notifyDataSetChanged()
    }

    private fun renderLatencyState(
        textView: TextView,
        state: EndpointLatencyState
    ) {
        when (state) {
            EndpointLatencyState.NotTested -> {
                textView.setText(
                    R.string.latency_not_tested
                )

                textView.setTextColor(
                    MaterialColors.getColor(
                        textView,
                        com.google.android.material.R.attr
                            .colorOnSurfaceVariant
                    )
                )
            }

            EndpointLatencyState.Testing -> {
                textView.setText(
                    R.string.latency_testing
                )

                textView.setTextColor(
                    MaterialColors.getColor(
                        textView,
                        androidx.appcompat.R.attr
                            .colorPrimary
                    )
                )
            }

            is EndpointLatencyState.Success -> {
                textView.text =
                    "${state.milliseconds} ms"

                textView.setTextColor(
                    latencyColor(
                        view = textView,
                        milliseconds =
                            state.milliseconds
                    )
                )
            }

            is EndpointLatencyState.Timeout -> {
                textView.setText(
                    R.string.latency_timeout
                )

                textView.setTextColor(
                    warningColor(textView)
                )
            }

            is EndpointLatencyState.Failed -> {
                textView.setText(
                    R.string.latency_failed
                )

                textView.setTextColor(
                    MaterialColors.getColor(
                        textView,
                        androidx.appcompat.R.attr.colorError
                    )
                )
            }
        }
    }

    private fun latencyColor(
        view: View,
        milliseconds: Long
    ): Int {
        return when {
            milliseconds < 250L -> {
                android.graphics.Color.rgb(
                    46,
                    125,
                    50
                )
            }

            milliseconds < 500L -> {
                warningColor(view)
            }

            else -> {
                MaterialColors.getColor(
                    view,
                    androidx.appcompat.R.attr.colorError
                )
            }
        }
    }

    private fun warningColor(
        view: View
    ): Int {
        val isLight =
            MaterialColors.isColorLight(
                MaterialColors.getColor(
                    view,
                    com.google.android.material.R.attr
                        .colorSurface
                )
            )

        return if (isLight) {
            android.graphics.Color.rgb(
                181,
                120,
                0
            )
        } else {
            android.graphics.Color.rgb(
                255,
                193,
                7
            )
        }
    }

    private fun openProfileEditor(
        profile: ServerProfile
    ) {
        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        val intent = Intent(
            requireContext(),
            ProfileEditorActivity::class.java
        ).apply {
            putExtra(
                ProfileEditorActivity.EXTRA_PROFILE_ID,
                profile.id
            )
        }

        startActivity(intent)
    }

    private fun copyProfileUri(
        profile: ServerProfile
    ) {
        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        val uri = try {
            ProfileUriCodec.encode(profile)
        } catch (_: Exception) {
            Snackbar.make(
                requireView(),
                R.string.profile_uri_copy_failed,
                Snackbar.LENGTH_LONG
            ).show()

            return
        }

        if (uri.isBlank()) {
            Snackbar.make(
                requireView(),
                R.string.profile_uri_copy_failed,
                Snackbar.LENGTH_LONG
            ).show()

            return
        }

        val clipboard =
            requireContext().getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                getString(
                    R.string.profile_uri_clip_label
                ),
                uri
            )
        )

        Snackbar.make(
            requireView(),
            R.string.profile_uri_copied,
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun duplicateProfile(
        profile: ServerProfile
    ) {
        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        val profiles = when (
            val result = repository.getProfiles()
        ) {
            is ProfileRepositoryResult.Success -> {
                result.profiles
            }

            ProfileRepositoryResult.ProfileNotFound,
            ProfileRepositoryResult.StorageReadFailed,
            ProfileRepositoryResult.StorageWriteFailed -> {
                Snackbar.make(
                    requireView(),
                    R.string.profile_duplicate_failed,
                    Snackbar.LENGTH_LONG
                ).show()

                return
            }
        }

        val existingNames = profiles
            .map { it.remarks.trim() }
            .toSet()

        val baseName = profile.remarks
            .trim()
            .ifBlank {
                getString(R.string.unnamed_profile)
            }

        var duplicateName =
            getString(
                R.string.profile_copy_name,
                baseName
            )

        var copyNumber = 2

        while (duplicateName in existingNames) {
            duplicateName =
                getString(
                    R.string.profile_copy_numbered_name,
                    baseName,
                    copyNumber
                )

            copyNumber++
        }

        val duplicate = profile.copy(
            id = UUID.randomUUID().toString(),
            remarks = duplicateName
        )

        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        when (
            repository.addProfile(duplicate)
        ) {
            is ProfileRepositoryResult.Success -> {
                Snackbar.make(
                    requireView(),
                    getString(
                        R.string.profile_duplicated,
                        duplicate.remarks
                    ),
                    Snackbar.LENGTH_SHORT
                ).show()

                loadProfiles()
            }

            ProfileRepositoryResult.ProfileNotFound,
            ProfileRepositoryResult.StorageReadFailed,
            ProfileRepositoryResult.StorageWriteFailed -> {
                Snackbar.make(
                    requireView(),
                    R.string.profile_duplicate_failed,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun confirmDeleteProfile(
        profile: ServerProfile
    ) {
        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(R.string.delete_profile)
            .setMessage(
                getString(
                    R.string.delete_profile_message,
                    profile.remarks
                )
            )
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .setPositiveButton(
                R.string.delete
            ) { _, _ ->
                deleteProfile(profile)
            }
            .show()
    }

    private fun deleteProfile(
        profile: ServerProfile
    ) {
        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        when (
            repository.deleteProfile(profile.id)
        ) {
            is ProfileRepositoryResult.Success -> {
                if (
                    activeProfileStore
                        .getActiveProfileId() == profile.id
                ) {
                    activeProfileStore.clearActiveProfile()
                }

                Snackbar.make(
                    requireView(),
                    R.string.profile_deleted,
                    Snackbar.LENGTH_SHORT
                ).show()

                loadProfiles()
            }

            ProfileRepositoryResult.ProfileNotFound,
            ProfileRepositoryResult.StorageReadFailed,
            ProfileRepositoryResult.StorageWriteFailed -> {
                Snackbar.make(
                    requireView(),
                    R.string.profile_delete_failed,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun protocolLabel(
        protocol: Protocol
    ): String {
        return getString(
            when (protocol) {
                Protocol.VMESS ->
                    R.string.protocol_vmess

                Protocol.VLESS ->
                    R.string.protocol_vless

                Protocol.TROJAN ->
                    R.string.protocol_trojan

                Protocol.SHADOWSOCKS ->
                    R.string.protocol_shadowsocks
            }
        )
    }

    private fun dp(value: Int): Int {
        return (
            value *
                resources.displayMetrics.density
        ).toInt()
    }

    private fun showLoadError() {
        view?.let { root ->
            Snackbar.make(
                root,
                R.string.profile_load_failed,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private fun showAddProfileDialog() {
        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        val content =
            layoutInflater.inflate(
                R.layout.dialog_add_profile,
                null
            )

        val dialog =
            MaterialAlertDialogBuilder(
                requireContext()
            )
                .setView(content)
                .create()

        content.findViewById<View>(
            R.id.add_profile_uri
        ).setOnClickListener {
            dialog.dismiss()
            showImportUriDialog()
        }

        content.findViewById<View>(
            R.id.add_profile_clipboard
        ).setOnClickListener {
            dialog.dismiss()
            importProfileFromClipboard()
        }

        content.findViewById<View>(
            R.id.add_profile_manual
        ).setOnClickListener {
            dialog.dismiss()
            showProtocolPicker()
        }

        content.findViewById<View>(
            R.id.add_profile_cancel
        ).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun importProfileFromClipboard() {
        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        val clipboard =
            requireContext().getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        if (!clipboard.hasPrimaryClip()) {
            Snackbar.make(
                requireView(),
                R.string.clipboard_empty,
                Snackbar.LENGTH_SHORT
            ).show()

            return
        }

        val clip =
            clipboard.primaryClip

        if (
            clip == null ||
            clip.itemCount == 0
        ) {
            Snackbar.make(
                requireView(),
                R.string.clipboard_empty,
                Snackbar.LENGTH_SHORT
            ).show()

            return
        }

        val text =
            clip.getItemAt(0)
                .coerceToText(requireContext())
                ?.toString()
                ?.trim()
                .orEmpty()

        if (text.isBlank()) {
            Snackbar.make(
                requireView(),
                R.string.clipboard_empty,
                Snackbar.LENGTH_SHORT
            ).show()

            return
        }

        if (!isSupportedProfileUri(text)) {
            Snackbar.make(
                requireView(),
                R.string.clipboard_no_profile_uri,
                Snackbar.LENGTH_LONG
            ).show()

            return
        }

        openImportedProfile(text)
    }

    private fun isSupportedProfileUri(
        value: String
    ): Boolean {
        val normalized =
            value.trimStart().lowercase()

        return normalized.startsWith("vmess://") ||
            normalized.startsWith("vless://") ||
            normalized.startsWith("trojan://") ||
            normalized.startsWith("ss://")
    }

    private fun showImportUriDialog() {
        if (isVpnProfileMutationBlocked()) {
            showVpnRunningProfileWarning()
            return
        }

        val context =
            requireContext()

        val content =
            layoutInflater.inflate(
                R.layout.dialog_import_uri,
                null,
                false
            )

        val container =
            content.findViewById<
                TextInputLayout
            >(
                R.id.profile_uri_container
            )

        val input =
            content.findViewById<
                TextInputEditText
            >(
                R.id.profile_uri_input
            )

        val dialog =
            MaterialAlertDialogBuilder(
                context
            )
                .setTitle(
                    R.string.import_uri
                )
                .setView(content)
                .setNegativeButton(
                    android.R.string.cancel,
                    null
                )
                .setPositiveButton(
                    R.string.import_action,
                    null
                )
                .create()

        dialog.setOnShowListener {
            val positive =
                dialog.getButton(
                    android.app.AlertDialog
                        .BUTTON_POSITIVE
                )

            positive.setOnClickListener {
                val uri =
                    input.text
                        ?.toString()
                        ?.trim()
                        .orEmpty()

                if (uri.isBlank()) {
                    container.error =
                        getString(
                            R.string
                                .profile_uri_required
                        )

                    return@setOnClickListener
                }

                container.error =
                    null

                openImportedProfile(
                    uri
                )

                dialog.dismiss()
            }

            input.requestFocus()
        }

        dialog.show()
    }

    private fun openImportedProfile(
        uri: String
    ) {
        val intent = Intent(
            requireContext(),
            ProfileEditorActivity::class.java
        ).apply {
            putExtra(
                ProfileEditorActivity.EXTRA_IMPORT_URI,
                uri
            )
        }

        startActivity(intent)
    }

    private fun showProtocolPicker() {
        val protocols = ProtocolType.entries

        val labels = protocols
            .map { it.displayName }
            .toTypedArray()

        MaterialAlertDialogBuilder(
            requireContext()
        )
            .setTitle(R.string.choose_protocol)
            .setItems(labels) { _, which ->
                val protocol =
                    protocols.getOrNull(which)
                        ?: return@setItems

                openManualEditor(protocol)
            }
            .setNegativeButton(
                android.R.string.cancel,
                null
            )
            .show()
    }

    private fun openManualEditor(
        protocol: ProtocolType
    ) {
        val intent = Intent(
            requireContext(),
            ProfileEditorActivity::class.java
        ).apply {
            putExtra(
                ProfileEditorActivity.EXTRA_PROTOCOL,
                protocol.name
            )
        }

        startActivity(intent)
    }

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_DUPLICATE = 2
        private const val MENU_SHARE = 3
        private const val MENU_LATENCY = 4
        private const val MENU_DELETE = 5
    }
}
