package com.android.mdl.appreader.fragment

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.android.mdl.appreader.R
import com.android.mdl.appreader.databinding.FragmentDeviceEngagementBinding
import com.android.mdl.appreader.transfer.QrConfirmationListener
import com.android.mdl.appreader.transfer.TransferManager
import com.android.mdl.appreader.util.TransferStatus
import com.android.mdl.appreader.util.logDebug
import com.android.mdl.appreader.util.logError
import com.budiyev.android.codescanner.CodeScanner
import com.budiyev.android.codescanner.CodeScanner.com.google.zxing.BarcodeFormat.QR_CODE
import com.budiyev.android.codescanner.DecodeCallback
import com.budiyev.android.codescanner.ScanMode
import com.google.zxing.BarcodeFormat
import com.google.zxing.BarcodeFormat.QR_CODE
import com.stelau.cb2d.Cb2d


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
        mCodeScanner?.formats = listOf(QR_CODE)
        mCodeScanner?.scanMode = ScanMode.SINGLE
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

            // Show VdsFragment with the verification data
            val vdsFragment = VdsFragment.newInstance(
                firstName = decodedUserInfo.prenom.ifEmpty { "" },
                lastName = decodedUserInfo.nom.ifEmpty { "" },
                commonName = decodedUserInfo.usage.ifEmpty { "" },
                dateOfBirth = decodedUserInfo.dateNaissance.ifEmpty { "" },
                issuanceDate = decodedUserInfo.issuanceDateTime.ifEmpty { "" },
                issuanceElapsedTime = decodedUserInfo.issuanceElapsedTime.ifEmpty { "" },
                certificateReference = decodedUserInfo.certificateReference.ifEmpty { "" },
                decipheredBlock = decodedUserInfo.decipheredBlock.ifEmpty { "" },


                errorMessage = if (decodedUserInfo.errorMessage.isNotEmpty()) 
                    getString(R.string.error_format, decodedUserInfo.errorMessage) 
                    else "",
                onContinue = {
                    onConfirmed()
                }
            )
            
            vdsFragment.show(parentFragmentManager, "VdsFragment")
            
        } catch (e: Exception) {
            logError("Error decoding VDS", e)
            // Show error in VdsFragment for consistency
            VdsFragment.newInstance(
                errorMessage = getString(R.string.error_processing_document, e.message ?: "Unknown error"),
                onContinue = {}
            ).show(parentFragmentManager, "VdsFragmentError")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}