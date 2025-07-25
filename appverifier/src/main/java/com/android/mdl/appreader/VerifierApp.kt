package com.android.mdl.appreader

import android.app.Application
import android.content.Context
import org.multipaz.util.Logger
import androidx.preference.PreferenceManager
import org.multipaz.crypto.X509Cert
import org.multipaz.documenttype.DocumentTypeRepository
import org.multipaz.documenttype.knowntypes.DrivingLicense
import org.multipaz.documenttype.knowntypes.EUPersonalID
import org.multipaz.documenttype.knowntypes.VaccinationDocument
import org.multipaz.documenttype.knowntypes.VehicleRegistration
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.storage.GenericStorageEngine
import org.multipaz.storage.StorageEngine
import org.multipaz.trustmanagement.TrustManager
import org.multipaz.trustmanagement.TrustPoint
import com.android.mdl.appreader.settings.UserPreferences
import com.android.mdl.appreader.util.KeysAndCertificates
import com.google.android.material.color.DynamicColors
import com.stelau.democb2d.utils.KeyManager
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlinx.io.files.Path
import java.io.File
import com.stelau.cb2d.utils.CevManager
import com.stelau.cbor.CborManager

class VerifierApp : Application() {

    private val userPreferences by lazy {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        UserPreferences(sharedPreferences)
    }

    private val trustManager by lazy {
        TrustManager()
    }

    private val certificateStorageEngine by lazy {
        val certDir = getDir("Certificates", MODE_PRIVATE)
        val certFile = "imported_certs"
        GenericStorageEngine(Path(File(certDir, certFile).absolutePath))
    }

    private val documentTypeRepository by lazy {
        DocumentTypeRepository()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        KeyManager.loadKeys(this)
        CevManager.initializeCev(this)
        CborManager.initialize()


        // Do NOT add BouncyCastle here - we want to use the normal AndroidOpenSSL JCA provider
        DynamicColors.applyToActivitiesIfAvailable(this)
        userPreferencesInstance = userPreferences
        Logger.isDebugEnabled = userPreferences.isDebugLoggingEnabled()
        trustManagerInstance = trustManager
        certificateStorageEngineInstance = certificateStorageEngine
        certificateStorageEngineInstance.enumerate().forEach {
            val certificate = parseCertificate(certificateStorageEngineInstance.get(it)!!)
            trustManagerInstance.addTrustPoint(TrustPoint(X509Cert(certificate.encoded)))
        }
        KeysAndCertificates.getTrustedIssuerCertificates(this).forEach {
            trustManagerInstance.addTrustPoint(TrustPoint(X509Cert(it.encoded)))
        }
        val signedVical = SignedVical.parse(
            resources.openRawResource(R.raw.austroad_test_event_vical_20241002).readBytes()
        )
        for (certInfo in signedVical.vical.certificateInfos) {
            trustManagerInstance.addTrustPoint(
                TrustPoint(
                    certInfo.certificate,
                    null,
                    null
                )
            )
        }


        documentTypeRepositoryInstance = documentTypeRepository
        documentTypeRepositoryInstance.addDocumentType(DrivingLicense.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(VehicleRegistration.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(VaccinationDocument.getDocumentType())
        documentTypeRepositoryInstance.addDocumentType(EUPersonalID.getDocumentType())
    }

    companion object {

        private lateinit var instance: VerifierApp

        fun getApplicationContext(): Context {
            return instance.applicationContext
        }

        private lateinit var userPreferencesInstance: UserPreferences
        lateinit var trustManagerInstance: TrustManager
        lateinit var certificateStorageEngineInstance: StorageEngine
        lateinit var documentTypeRepositoryInstance: DocumentTypeRepository
        fun isDebugLogEnabled(): Boolean {
            return userPreferencesInstance.isDebugLoggingEnabled()
        }
    }

    /**
     * Parse a byte array as an X509 certificate
     */
    private fun parseCertificate(certificateBytes: ByteArray): X509Certificate {
        return CertificateFactory.getInstance("X509")
            .generateCertificate(ByteArrayInputStream(certificateBytes)) as X509Certificate
    }
}