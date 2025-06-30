package com.android.mdl.appreader.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.app.AlertDialog
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.appreader.transfer.QrConfirmationListener
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentDeviceEngagementBinding
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.util.TransferStatus
import com.android.mdl.appreader.util.logDebug
import com.android.mdl.appreader.util.logError
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.DecodeCallback
import com.stelau.cb2d.Cb2d
import com.stelau.cb2d.Cb2d.UserInfo

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class DeviceEngagementFragment : Fragment(), QrConfirmationListener {

    private val args: DeviceEngagementFragmentArgs by navArgs()

    private val appPermissions:List<String> get() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (android.os.Build.VERSION.SDK_INT >= 31) {
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        return permissions
    }

    private var _binding: FragmentDeviceEngagementBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private var mCodeScanner: CodeScanner? = null
    private lateinit var transferManager: TransferManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentDeviceEngagementBinding.inflate(inflater, container, false)
        transferManager = TransferManager.getInstance(requireContext())
        transferManager.initVerificationHelper()
        transferManager.setQrConfirmationListener(this)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // QR Code Engagement
        mCodeScanner = CodeScanner(requireContext(), binding.csScanner)
        mCodeScanner?.decodeCallback = DecodeCallback { result ->
            requireActivity().runOnUiThread {
                val qrText = result.text
                logDebug("qrText: $qrText")
                // The actual processing will happen in the onQrContentReady callback
                transferManager.setQrDeviceEngagement(qrText) {
                    // This will be called after user confirms the QR code
                    logDebug("QR code confirmed, continuing with verification...")
                }
            }
        }

        binding.csScanner.setOnClickListener { mCodeScanner?.startPreview() }

        transferManager.getTransferStatus().observe(viewLifecycleOwner) {
            when (it) {
                TransferStatus.ENGAGED -> {
                    logDebug("Device engagement received")
                    onDeviceEngagementReceived()
                }

                TransferStatus.CONNECTED -> {
                    logDebug("Device connected")
                    Toast.makeText(
                        requireContext(), "Error invalid callback connected",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(R.id.action_ScanDeviceEngagement_to_RequestOptions)
                }

                TransferStatus.RESPONSE -> {
                    logDebug("Device response received")
                    Toast.makeText(
                        requireContext(), "Error invalid callback response",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(R.id.action_ScanDeviceEngagement_to_RequestOptions)
                }

                TransferStatus.DISCONNECTED -> {
                    logDebug("Device disconnected")
                    Toast.makeText(
                        requireContext(), "Device disconnected",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(R.id.action_ScanDeviceEngagement_to_RequestOptions)
                }

                TransferStatus.ERROR -> {
                    // TODO: Pass and show the actual text of the exception here.
                    logDebug("Error received")
                    Toast.makeText(
                        requireContext(), "Error connecting to holder",
                        Toast.LENGTH_SHORT
                    ).show()
                    findNavController().navigate(R.id.action_ScanDeviceEngagement_to_RequestOptions)
                }
                else -> {}
            }
        }

        binding.btCancel.setOnClickListener {
            findNavController().navigate(R.id.action_ScanDeviceEngagement_to_RequestOptions)
        }
    }

    override fun onResume() {
        super.onResume()
        enableReader()
    }

    override fun onPause() {
        super.onPause()
        disableReader()
    }

    private fun enableReader() {
        if (isAllPermissionsGranted()) {
            mCodeScanner?.startPreview()
        } else {
            shouldRequestPermission()
        }
        val adapter = NfcAdapter.getDefaultAdapter(requireContext())
        if (adapter != null) {
            transferManager.setNdefDeviceEngagement(
                adapter,
                requireActivity()
            )
        } else {
            Toast.makeText(activity, "NFC adapter is not available", Toast.LENGTH_LONG).show()
        }
    }

    private fun disableReader() {
        mCodeScanner?.releaseResources()
    }

    private fun shouldRequestPermission() {
        val permissionsNeeded = appPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            permissionsLauncher.launch(
                permissionsNeeded.toTypedArray()
            )
        }
    }

    private fun isAllPermissionsGranted(): Boolean {
        // If any permission is not granted return false
        return appPermissions.none { permission ->
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }
    }

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                logDebug("permissionsLauncher ${it.key} = ${it.value}")

                // Open settings if user denied any required permission
                if (!it.value && !shouldShowRequestPermissionRationale(it.key)) {
                    openSettings()
                    return@registerForActivityResult
                }
            }
        }

    private fun openSettings() {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", requireContext().packageName, null)
        startActivity(intent)
    }

    private fun onDeviceEngagementReceived() {
        if (transferManager.availableMdocConnectionMethods?.size == 1) {
            findNavController().navigate(
                DeviceEngagementFragmentDirections.actionScanDeviceEngagementToTransfer(
                    args.requestDocumentList
                )
            )
        } else {
            findNavController().navigate(
                DeviceEngagementFragmentDirections.actionScanDeviceEngagementToSelectTransport(
                    args.requestDocumentList
                )
            )
        }
    }

    override fun onQrContentReady(qrContent: String, onConfirmed: () -> Unit) {
        try {
            val userInfo = Cb2d.UserInfo()
            val decodedUserInfo = Cb2d.decodeCb2d(qrContent, userInfo)

            val message = buildString {
                // Personal Information
                append("--- Personal Information ---\n")
                append("Last Name: ${decodedUserInfo.nom.ifEmpty { "Not provided" }}\n")
                append("Usage Name: ${decodedUserInfo.usage.ifEmpty { "Not provided" }}\n")
                append("First Names: ${decodedUserInfo.prenom.ifEmpty { "Not provided" }}\n")
                append("Date of Birth: ${decodedUserInfo.dateNaissance.ifEmpty { "Not provided" }}\n\n")

                // Document Information
                append("--- Document Information ---\n")
                append("Expiration: ${decodedUserInfo.expiration.ifEmpty { "Not provided" }}\n")
                append("Issuance Date: ${decodedUserInfo.issuanceDateTime.ifEmpty { "Not provided" }}\n")
                append("Issued: ${decodedUserInfo.issuanceElapsedTime.ifEmpty { "Not available" }}\n")

                // Terms of Use
                if (decodedUserInfo.tou.isNotEmpty()) {
                    append("\n=== Terms of Use ===\n")
                    append(decodedUserInfo.tou)
                }

                // Errors (if any)
                if (decodedUserInfo.errorMessage.isNotEmpty()) {
                    append("\n\n=== Errors ===\n")
                    append("Code: ${decodedUserInfo.errorCode}\n")
                    append("Message: ${decodedUserInfo.errorMessage}")
                }
            }

            AlertDialog.Builder(requireContext())
                .setTitle("Document Verification")
                .setMessage(message)
                .setPositiveButton(R.string.continue_text) { _, _ ->
                    onConfirmed()
                }
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener {
                    mCodeScanner?.startPreview()
                }
                .show()
        } catch (e: Exception) {
            logError("Error decoding VDS", e)
            AlertDialog.Builder(requireContext())
                .setTitle("Error")
                .setMessage("Failed to process document:\n${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}