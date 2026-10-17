package com.mdevz.xelvra.ui.editor

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mdevz.xelvra.core.vpn.VpnRuntimeManager
import com.mdevz.xelvra.R
import com.mdevz.xelvra.data.model.Protocol
import com.mdevz.xelvra.data.model.ProtocolOptions
import com.mdevz.xelvra.data.model.Security
import com.mdevz.xelvra.data.model.ServerProfile
import com.mdevz.xelvra.data.model.Transport
import com.mdevz.xelvra.data.local.ProfileLocalStorage
import com.mdevz.xelvra.data.repository.ProfileRepository
import com.mdevz.xelvra.data.repository.ProfileRepositoryResult
import com.mdevz.xelvra.protocol.uri.ProfileUriCodec
import com.mdevz.xelvra.protocol.uri.ProfileUriDecodeResult
import com.mdevz.xelvra.protocol.validator.ProfileValidation
import com.mdevz.xelvra.protocol.validator.ProfileValidator
import java.util.UUID

class ProfileEditorActivity : AppCompatActivity() {

    private lateinit var protocol: Protocol

    private var editingProfile: ServerProfile? = null

    private var importedProfile: ServerProfile? = null

    private lateinit var repository: ProfileRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_profile_editor)

        configureEdgeToEdge()

        repository = ProfileRepository(
            ProfileLocalStorage(applicationContext)
        )

        val editProfileId =
            intent.getStringExtra(EXTRA_PROFILE_ID)

        editingProfile = editProfileId
            ?.takeIf { it.isNotBlank() }
            ?.let(repository::findProfile)

        val importUri =
            intent.getStringExtra(EXTRA_IMPORT_URI)
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        importedProfile = importUri
            ?.let { uri ->
                when (
                    val result =
                        ProfileUriCodec.decode(uri)
                ) {
                    is ProfileUriDecodeResult.Success -> {
                        result.profile
                    }

                    is ProfileUriDecodeResult.Error -> {
                        Toast.makeText(
                            this,
                            uriErrorMessage(
                                result.error
                            ),
                            Toast.LENGTH_LONG
                        ).show()

                        null
                    }
                }
            }

        if (
            importUri != null &&
            importedProfile == null
        ) {
            finish()
            return
        }

        protocol = editingProfile?.protocol
            ?: importedProfile?.protocol
            ?: parseProtocol(
                intent.getStringExtra(EXTRA_PROTOCOL)
            )

        configureToolbar()
        configureProtocolFields()
        configureDropdowns()
        configureSave()

        editingProfile?.let { profile ->
            populateProfile(profile)
        }

        importedProfile?.let { profile ->
            populateProfile(profile)
        }
    }

    private fun configureEdgeToEdge() {
        val root = findViewById<View>(
            R.id.editor_root
        )

        val toolbar = findViewById<View>(
            R.id.editor_toolbar
        )

        val scroll = findViewById<View>(
            R.id.editor_scroll
        )

        val toolbarBaseHeight = dp(64)

        val toolbarInitialPaddingLeft =
            toolbar.paddingLeft

        val toolbarInitialPaddingRight =
            toolbar.paddingRight

        val toolbarInitialPaddingBottom =
            toolbar.paddingBottom

        val scrollInitialPaddingLeft =
            scroll.paddingLeft

        val scrollInitialPaddingTop =
            scroll.paddingTop

        val scrollInitialPaddingRight =
            scroll.paddingRight

        val scrollInitialPaddingBottom =
            scroll.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(
            root
        ) { _, windowInsets ->

            val statusBars =
                windowInsets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                )

            val navigationBars =
                windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                )

            val ime =
                windowInsets.getInsets(
                    WindowInsetsCompat.Type.ime()
                )

            toolbar.layoutParams =
                toolbar.layoutParams.apply {
                    height =
                        toolbarBaseHeight +
                            statusBars.top
                }

            toolbar.setPadding(
                toolbarInitialPaddingLeft,
                statusBars.top,
                toolbarInitialPaddingRight,
                toolbarInitialPaddingBottom
            )

            scroll.setPadding(
                scrollInitialPaddingLeft,
                scrollInitialPaddingTop,
                scrollInitialPaddingRight,
                scrollInitialPaddingBottom +
                    maxOf(
                        navigationBars.bottom,
                        ime.bottom
                    )
            )

            windowInsets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun configureToolbar() {
        findViewById<MaterialToolbar?>(
            R.id.editor_toolbar
        )?.apply {
            title = when {
                editingProfile != null -> {
                    getString(R.string.edit_profile)
                }

                importedProfile != null -> {
                    getString(R.string.import_profile)
                }

                else -> {
                    protocolDisplayName()
                }
            }
            setNavigationOnClickListener {
                finish()
            }
        }
    }

    private fun configureProtocolFields() {
        val primaryLayout =
            findViewById<TextInputLayout>(R.id.auth_primary_layout)

        val secondaryLayout =
            findViewById<TextInputLayout>(R.id.auth_secondary_layout)

        val flowLayout =
            findViewById<TextInputLayout>(R.id.vless_flow_layout)

        val methodLayout =
            findViewById<TextInputLayout>(
                R.id.shadowsocks_method_layout
            )

        val primaryInput =
            findViewById<TextInputEditText>(R.id.input_auth_primary)

        val secondaryInput =
            findViewById<TextInputEditText>(R.id.input_auth_secondary)

        primaryLayout.visibility = View.VISIBLE
        secondaryLayout.visibility = View.GONE
        flowLayout.visibility = View.GONE
        methodLayout.visibility = View.GONE

        primaryLayout.error = null
        secondaryLayout.error = null

        primaryInput.text?.clear()
        secondaryInput.text?.clear()

        when (protocol) {
            Protocol.VMESS -> {
                primaryLayout.setHint(R.string.uuid)
            }

            Protocol.VLESS -> {
                primaryLayout.setHint(R.string.uuid)
                flowLayout.visibility = View.VISIBLE
            }

            Protocol.TROJAN -> {
                primaryLayout.setHint(R.string.password)
            }

            Protocol.SHADOWSOCKS -> {
                primaryLayout.visibility = View.GONE
                methodLayout.visibility = View.VISIBLE
                secondaryLayout.setHint(R.string.password)
                secondaryLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun configureDropdowns() {
        configureDropdown(
            R.id.input_shadowsocks_method,
            resources.getStringArray(
                R.array.editor_shadowsocks_method
            ).toList(),
            "aes-256-gcm"
        )

        configureDropdown(
            R.id.input_vless_flow,
            resources.getStringArray(
                R.array.editor_vless_flow
            ).toList(),
            ""
        )

        configureDropdown(
            R.id.input_transport_mode,
            resources.getStringArray(
                R.array.editor_xhttp_mode
            ).toList(),
            "auto"
        )

        configureDropdown(
            R.id.input_alpn,
            resources.getStringArray(
                R.array.editor_alpn
            ).toList(),
            ""
        )

        configureDropdown(
            R.id.input_fingerprint,
            resources.getStringArray(
                R.array.editor_fingerprint
            ).toList(),
            ""
        )

        configureDropdown(
            R.id.input_transport,
            listOf(
                "TCP",
                "WebSocket",
                "gRPC",
                "HTTP",
                "HTTPUpgrade",
                "XHTTP"
            ),
            "TCP"
        )

        configureDropdown(
            R.id.input_security,
            listOf(
                "None",
                "TLS",
                "REALITY"
            ),
            "None"
        )
    }

    private fun configureDropdown(
        viewId: Int,
        values: List<String>,
        defaultValue: String
    ) {
        val view =
            findViewById<MaterialAutoCompleteTextView>(viewId)

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            values
        )

        view.setAdapter(adapter)
        view.setText(defaultValue, false)

        when (viewId) {
            R.id.input_transport -> {
                updateTransportFields(defaultValue)

                view.setOnItemClickListener { _, _, position, _ ->
                    updateTransportFields(values[position])
                }
            }

            R.id.input_security -> {
                updateSecurityFields(defaultValue)

                view.setOnItemClickListener { _, _, position, _ ->
                    updateSecurityFields(values[position])
                }
            }
        }
    }

    private fun updateTransportFields(value: String) {
        val card =
            findViewById<View>(R.id.transport_options_card)

        val host =
            findViewById<View>(R.id.transport_host_layout)

        val path =
            findViewById<View>(R.id.transport_path_layout)

        val service =
            findViewById<View>(R.id.service_name_layout)

        val mode =
            findViewById<View>(R.id.transport_mode_layout)

        host.visibility = View.GONE
        path.visibility = View.GONE
        service.visibility = View.GONE
        mode.visibility = View.GONE

        when (value) {
            "WebSocket", "HTTP", "HTTPUpgrade" -> {
                card.visibility = View.VISIBLE
                host.visibility = View.VISIBLE
                path.visibility = View.VISIBLE
            }

            "gRPC" -> {
                card.visibility = View.VISIBLE
                service.visibility = View.VISIBLE
            }

            "XHTTP" -> {
                card.visibility = View.VISIBLE
                host.visibility = View.VISIBLE
                path.visibility = View.VISIBLE
                mode.visibility = View.VISIBLE
            }

            else -> {
                card.visibility = View.GONE
            }
        }
    }

    private fun updateSecurityFields(value: String) {
        val card =
            findViewById<View>(R.id.security_options_card)

        val sni =
            findViewById<View>(R.id.sni_layout)

        val fingerprint =
            findViewById<View>(R.id.fingerprint_layout)

        val alpn =
            findViewById<View>(R.id.alpn_layout)

        val publicKey =
            findViewById<View>(R.id.public_key_layout)

        val shortId =
            findViewById<View>(R.id.short_id_layout)

        val spiderX =
            findViewById<View>(R.id.spider_x_layout)

        sni.visibility = View.GONE
        fingerprint.visibility = View.GONE
        alpn.visibility = View.GONE
        publicKey.visibility = View.GONE
        shortId.visibility = View.GONE
        spiderX.visibility = View.GONE

        when (value) {
            "TLS" -> {
                card.visibility = View.VISIBLE
                sni.visibility = View.VISIBLE
                fingerprint.visibility = View.VISIBLE
                alpn.visibility = View.VISIBLE
            }

            "REALITY" -> {
                card.visibility = View.VISIBLE
                sni.visibility = View.VISIBLE
                fingerprint.visibility = View.VISIBLE
                publicKey.visibility = View.VISIBLE
                shortId.visibility = View.VISIBLE
                spiderX.visibility = View.VISIBLE
            }

            else -> {
                card.visibility = View.GONE
            }
        }
    }

    private fun configureSave() {
        findViewById<View>(
            R.id.editor_toolbar_save
        ).setOnClickListener {
            saveProfile()
        }
    }

    private fun showVpnRunningProfileWarning() {
        MaterialAlertDialogBuilder(this)
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

    private fun saveProfile() {
        if (VpnRuntimeManager.isSessionActive()) {
            showVpnRunningProfileWarning()
            return
        }

        clearErrors()

        val portText = text(R.id.input_port)

        val port = portText.toIntOrNull()

        if (port == null) {
            showFieldError(
                ProfileValidation.Field.PORT,
                getString(
                    if (portText.isBlank()) {
                        R.string.validation_port_required
                    } else {
                        R.string.validation_port_number
                    }
                )
            )
            return
        }

        val profile = ServerProfile(
            id = editingProfile?.id
                ?: UUID.randomUUID().toString(),
            remarks = text(R.id.input_remarks),
            protocol = protocol,
            address = text(R.id.input_address),
            port = port,
            protocolOptions = createProtocolOptions(),
            transport = createTransport(),
            security = createSecurity()
        )

        when (
            val validation =
                ProfileValidator.validate(profile)
        ) {
            ProfileValidation.Valid -> {
                onProfileValid(profile)
            }

            is ProfileValidation.Invalid -> {
                showFieldError(
                    validation.field,
                    validationErrorMessage(
                        validation.error
                    )
                )
            }
        }
    }

    private fun createProtocolOptions(): ProtocolOptions {
        return when (protocol) {
            Protocol.VMESS -> {
                ProtocolOptions.VMess(
                    uuid = text(R.id.input_auth_primary)
                )
            }

            Protocol.VLESS -> {
                ProtocolOptions.VLess(
                    uuid = text(R.id.input_auth_primary),
                    flow = text(R.id.input_vless_flow)
                )
            }

            Protocol.TROJAN -> {
                ProtocolOptions.Trojan(
                    password = text(R.id.input_auth_primary)
                )
            }

            Protocol.SHADOWSOCKS -> {
                ProtocolOptions.Shadowsocks(
                    method = text(R.id.input_shadowsocks_method),
                    password = text(R.id.input_auth_secondary)
                )
            }
        }
    }

    private fun createTransport(): Transport {
        return when (text(R.id.input_transport)) {
            "WebSocket" -> {
                Transport.WebSocket(
                    host = text(R.id.input_transport_host),
                    path = text(R.id.input_transport_path)
                )
            }

            "gRPC" -> {
                Transport.Grpc(
                    serviceName = text(R.id.input_service_name)
                )
            }

            "HTTP" -> {
                Transport.Http(
                    host = text(R.id.input_transport_host),
                    path = text(R.id.input_transport_path)
                )
            }

            "HTTPUpgrade" -> {
                Transport.HttpUpgrade(
                    host = text(R.id.input_transport_host),
                    path = text(R.id.input_transport_path)
                )
            }

            "XHTTP" -> {
                Transport.XHttp(
                    host = text(R.id.input_transport_host),
                    path = text(R.id.input_transport_path),
                    mode = text(R.id.input_transport_mode)
                )
            }

            else -> Transport.Tcp
        }
    }

    private fun createSecurity(): Security {
        return when (text(R.id.input_security)) {
            "TLS" -> {
                Security.Tls(
                    serverName = text(R.id.input_sni),
                    fingerprint = text(R.id.input_fingerprint),
                    alpn = text(R.id.input_alpn)
                )
            }

            "REALITY" -> {
                Security.Reality(
                    serverName = text(R.id.input_sni),
                    fingerprint = text(R.id.input_fingerprint),
                    publicKey = text(R.id.input_public_key),
                    shortId = text(R.id.input_short_id),
                    spiderX = text(R.id.input_spider_x)
                )
            }

            else -> Security.None
        }
    }

    private fun onProfileValid(
        profile: ServerProfile
    ) {
        if (VpnRuntimeManager.isSessionActive()) {
            showVpnRunningProfileWarning()
            return
        }

        val result = if (editingProfile != null) {
            repository.updateProfile(profile)
        } else {
            repository.addProfile(profile)
        }

        when (result) {
            is ProfileRepositoryResult.Success -> {
                Toast.makeText(
                    this,
                    if (editingProfile != null) {
                        R.string.profile_updated
                    } else {
                        R.string.profile_saved
                    },
                    Toast.LENGTH_SHORT
                ).show()

                setResult(RESULT_OK)
                finish()
            }

            ProfileRepositoryResult.StorageReadFailed,
            ProfileRepositoryResult.StorageWriteFailed,
            ProfileRepositoryResult.ProfileNotFound -> {
                Toast.makeText(
                    this,
                    if (editingProfile != null) {
                        R.string.profile_update_failed
                    } else {
                        R.string.profile_save_failed
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun populateProfile(
        profile: ServerProfile
    ) {
        setText(
            R.id.input_remarks,
            profile.remarks
        )

        setText(
            R.id.input_address,
            profile.address
        )

        setText(
            R.id.input_port,
            profile.port.toString()
        )

        when (
            val options = profile.protocolOptions
        ) {
            is ProtocolOptions.VMess -> {
                setText(
                    R.id.input_auth_primary,
                    options.uuid
                )
            }

            is ProtocolOptions.VLess -> {
                setText(
                    R.id.input_auth_primary,
                    options.uuid
                )

                setText(
                    R.id.input_vless_flow,
                    options.flow
                )
            }

            is ProtocolOptions.Trojan -> {
                setText(
                    R.id.input_auth_primary,
                    options.password
                )
            }

            is ProtocolOptions.Shadowsocks -> {
                setText(
                    R.id.input_shadowsocks_method,
                    options.method
                )

                setText(
                    R.id.input_auth_secondary,
                    options.password
                )
            }
        }

        val transportName =
            when (val transport = profile.transport) {
                Transport.Tcp -> {
                    "TCP"
                }

                is Transport.WebSocket -> {
                    setText(
                        R.id.input_transport_host,
                        transport.host
                    )

                    setText(
                        R.id.input_transport_path,
                        transport.path
                    )

                    "WebSocket"
                }

                is Transport.Grpc -> {
                    setText(
                        R.id.input_service_name,
                        transport.serviceName
                    )

                    "gRPC"
                }

                is Transport.Http -> {
                    setText(
                        R.id.input_transport_host,
                        transport.host
                    )

                    setText(
                        R.id.input_transport_path,
                        transport.path
                    )

                    "HTTP"
                }

                is Transport.HttpUpgrade -> {
                    setText(
                        R.id.input_transport_host,
                        transport.host
                    )

                    setText(
                        R.id.input_transport_path,
                        transport.path
                    )

                    "HTTPUpgrade"
                }

                is Transport.XHttp -> {
                    setText(
                        R.id.input_transport_host,
                        transport.host
                    )

                    setText(
                        R.id.input_transport_path,
                        transport.path
                    )

                    setText(
                        R.id.input_transport_mode,
                        transport.mode
                    )

                    "XHTTP"
                }
            }

        val transportInput =
            findViewById<MaterialAutoCompleteTextView>(
                R.id.input_transport
            )

        transportInput.setText(
            transportName,
            false
        )

        updateTransportFields(
            transportName
        )

        val securityName =
            when (val security = profile.security) {
                Security.None -> {
                    "None"
                }

                is Security.Tls -> {
                    setText(
                        R.id.input_sni,
                        security.serverName
                    )

                    setText(
                        R.id.input_fingerprint,
                        security.fingerprint
                    )

                    setText(
                        R.id.input_alpn,
                        security.alpn
                    )

                    "TLS"
                }

                is Security.Reality -> {
                    setText(
                        R.id.input_sni,
                        security.serverName
                    )

                    setText(
                        R.id.input_fingerprint,
                        security.fingerprint
                    )

                    setText(
                        R.id.input_public_key,
                        security.publicKey
                    )

                    setText(
                        R.id.input_short_id,
                        security.shortId
                    )

                    setText(
                        R.id.input_spider_x,
                        security.spiderX
                    )

                    "REALITY"
                }
            }

        val securityInput =
            findViewById<MaterialAutoCompleteTextView>(
                R.id.input_security
            )

        securityInput.setText(
            securityName,
            false
        )

        updateSecurityFields(
            securityName
        )
    }

    private fun setText(
        viewId: Int,
        value: String
    ) {
        when (val view = findViewById<View>(viewId)) {
            is MaterialAutoCompleteTextView -> {
                view.setText(value, false)
            }

            is TextInputEditText -> {
                view.setText(value)
            }

            else -> {
                error("Unsupported editor field: $viewId")
            }
        }
    }

    private fun clearErrors() {
        listOf(
            R.id.remarks_layout,
            R.id.address_layout,
            R.id.port_layout,
            R.id.transport_layout,
            R.id.auth_primary_layout,
            R.id.auth_secondary_layout,
            R.id.public_key_layout,
            R.id.short_id_layout
        ).forEach { id ->
            findViewById<TextInputLayout>(id).error = null
        }
    }

    private fun validationErrorMessage(
        error: ProfileValidation.Error
    ): String {
        val resource = when (error) {
            ProfileValidation.Error.REMARKS_REQUIRED ->
                R.string.validation_remarks_required

            ProfileValidation.Error.ADDRESS_REQUIRED ->
                R.string.validation_address_required

            ProfileValidation.Error.PORT_RANGE ->
                R.string.validation_port_range

            ProfileValidation.Error.UUID_INVALID ->
                R.string.validation_uuid_invalid

            ProfileValidation.Error.PASSWORD_REQUIRED ->
                R.string.validation_password_required

            ProfileValidation.Error.METHOD_REQUIRED ->
                R.string.validation_method_required

            ProfileValidation.Error.PROTOCOL_OPTIONS_MISMATCH ->
                R.string.validation_protocol_options_mismatch

            ProfileValidation.Error.TRANSPORT_UNSUPPORTED ->
                R.string.validation_transport_unsupported

            ProfileValidation.Error
                .REALITY_TRANSPORT_UNSUPPORTED ->
                R.string.validation_reality_transport_unsupported

            ProfileValidation.Error
                .REALITY_PUBLIC_KEY_REQUIRED ->
                R.string.validation_reality_public_key_required

            ProfileValidation.Error
                .REALITY_SHORT_ID_REQUIRED ->
                R.string.validation_reality_short_id_required
        }

        return getString(resource)
    }

    private fun uriErrorMessage(
        error: ProfileUriDecodeResult.ErrorType
    ): String {
        val resource = when (error) {
            ProfileUriDecodeResult.ErrorType.EMPTY_URI ->
                R.string.uri_error_empty

            ProfileUriDecodeResult.ErrorType
                .UNSUPPORTED_PROTOCOL ->
                R.string.uri_error_unsupported_protocol

            ProfileUriDecodeResult.ErrorType.INVALID_URI ->
                R.string.uri_error_invalid

            ProfileUriDecodeResult.ErrorType
                .INVALID_SHADOWSOCKS_URI ->
                R.string.uri_error_shadowsocks_invalid

            ProfileUriDecodeResult.ErrorType
                .INVALID_SHADOWSOCKS_CREDENTIALS ->
                R.string.uri_error_shadowsocks_credentials

            ProfileUriDecodeResult.ErrorType
                .INVALID_SHADOWSOCKS_ENDPOINT ->
                R.string.uri_error_shadowsocks_endpoint

            ProfileUriDecodeResult.ErrorType
                .INVALID_ADDRESS ->
                R.string.uri_error_address

            ProfileUriDecodeResult.ErrorType
                .INVALID_PORT ->
                R.string.uri_error_port
        }

        return getString(resource)
    }

    private fun showFieldError(
        field: ProfileValidation.Field,
        message: String
    ) {
        val layoutId = when (field) {
            ProfileValidation.Field.REMARKS ->
                R.id.remarks_layout

            ProfileValidation.Field.ADDRESS ->
                R.id.address_layout

            ProfileValidation.Field.PORT ->
                R.id.port_layout

            ProfileValidation.Field.UUID ->
                R.id.auth_primary_layout

            ProfileValidation.Field.PASSWORD ->
                if (protocol == Protocol.SHADOWSOCKS) {
                    R.id.auth_secondary_layout
                } else {
                    R.id.auth_primary_layout
                }

            ProfileValidation.Field.METHOD ->
                R.id.auth_primary_layout

            ProfileValidation.Field.PROTOCOL_OPTIONS ->
                R.id.auth_primary_layout

            ProfileValidation.Field.TRANSPORT ->
                R.id.transport_layout

            ProfileValidation.Field.REALITY_PUBLIC_KEY ->
                R.id.public_key_layout

            ProfileValidation.Field.REALITY_SHORT_ID ->
                R.id.short_id_layout
        }

        val layout =
            findViewById<TextInputLayout>(layoutId)

        layout.error = message
        layout.requestFocus()
    }

    private fun text(id: Int): String {
        val view = findViewById<View?>(id)

        return when (view) {
            is TextInputEditText ->
                view.text?.toString()?.trim().orEmpty()

            is MaterialAutoCompleteTextView ->
                view.text?.toString()?.trim().orEmpty()

            else -> ""
        }
    }

    private fun parseProtocol(value: String?): Protocol {
        return when (value?.uppercase()) {
            "VMESS" -> Protocol.VMESS
            "TROJAN" -> Protocol.TROJAN
            "SHADOWSOCKS" -> Protocol.SHADOWSOCKS
            else -> Protocol.VLESS
        }
    }

    private fun protocolDisplayName(): String {
        return when (protocol) {
            Protocol.VMESS -> "VMess"
            Protocol.VLESS -> "Vless"
            Protocol.TROJAN -> "Trojan"
            Protocol.SHADOWSOCKS -> "Shadowsocks"
        }
    }

    private fun dp(
        value: Int
    ): Int {
        return (
            value *
                resources.displayMetrics.density
        ).toInt()
    }


    companion object {
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_IMPORT_URI = "import_uri"
    }
}
