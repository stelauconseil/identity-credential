package com.stelau.democb2d.utils

import android.content.Context
import android.content.res.Resources
import java.io.InputStreamReader
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import org.bouncycastle.crypto.agreement.kdf.ConcatenationKDFGenerator
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.KDFParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter

class KeyUtils {
    init {
        setupBouncyCastle()
    }

    private fun loadPrivateKeyPemKeys(context: Context, keyPath: String): PrivateKey {
        val inputStream =
            context.resources.openRawResource(
                context.resources.getIdentifier(keyPath, "raw", context.packageName)
            )
        val pemReader = PEMParser(InputStreamReader(inputStream))
        val pemObject = pemReader.readObject()
        val converter = JcaPEMKeyConverter().setProvider(BouncyCastleProvider())
        return when (pemObject) {
            is org.bouncycastle.openssl.PEMKeyPair ->
                converter.getPrivateKey(pemObject.privateKeyInfo)
            is org.bouncycastle.asn1.pkcs.PrivateKeyInfo -> converter.getPrivateKey(pemObject)
            else ->
                throw IllegalArgumentException(
                    "Invalid PEM object: ${pemObject::class.java.name}"
                )
        }
    }


    private fun loadPublicKeyPemKeys(context: Context, keyPath: String): PublicKey {
        val inputStream =
            context.resources.openRawResource(
                context.resources.getIdentifier(keyPath, "raw", context.packageName)
            )
        val pemReader = PEMParser(InputStreamReader(inputStream))
        val pemObject = pemReader.readPemObject()
        val keySpec = X509EncodedKeySpec(pemObject.content)
        val keyFactory = KeyFactory.getInstance("EC", "BC")
        return keyFactory.generatePublic(keySpec)
    }

    fun loadKeys(context: Context, keyName: String): KeyPair {
            val privateKeyPath = "${keyName}private"
            val publicKeyPath = "${keyName}public"
            return KeyPair(
                loadPublicKeyPemKeys(context, publicKeyPath),
                loadPrivateKeyPemKeys(context, privateKeyPath)
            )
    }

    fun generateECKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC")
        keyPairGenerator.initialize(ECGenParameterSpec("P-256"), SecureRandom())

        return keyPairGenerator.generateKeyPair()
    }

    fun generateAESKey(keySize: Int = 256): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(keySize)

        return keyGenerator.generateKey()
    }

    fun aesEncrypt(data: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        val ivParameterSpec = IvParameterSpec(ByteArray(16)) // 16 bytes IV (all zeros)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParameterSpec)

        return cipher.doFinal(data)
    }

    fun generateSharedSecret(privateKey: PrivateKey, publicKey: PublicKey): ByteArray {
        val keyAgreement = KeyAgreement.getInstance("ECDH", "BC")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)

        return keyAgreement.generateSecret()
    }

    // Taille de la clé dérivée 256 bits (32 octets)
    fun getAESKey(sharedSecret: ByteArray): ByteArray {
        val digest = SHA256Digest()
        val kdfGenerator = ConcatenationKDFGenerator(digest)
        val otherInfo = ByteArray(1)

        val kdfParameters = KDFParameters(sharedSecret, otherInfo)
        kdfGenerator.init(kdfParameters)

        val derivedKey = ByteArray(32)
        kdfGenerator.generateBytes(derivedKey, 0, 32)

        return derivedKey
    }
}

object KeyManager {
    var deviceKeyPair: KeyPair? = null
    var sncfKeyPair: KeyPair? = null
    var sncfKeyPair2: KeyPair? = null
    var ouigoKeyPair: KeyPair? = null
    var sginPrivateKey: KeyPair? = null
    var renfeKeyPair: KeyPair? = null
    var trenitaliaKeyPair: KeyPair? = null

    fun loadKeys(context: Context) {
        val keyUtils = KeyUtils()
            deviceKeyPair = keyUtils.loadKeys(context, "device")
            sncfKeyPair = keyUtils.loadKeys(context, "sncf")
            sncfKeyPair2 = keyUtils.loadKeys(context, "sncf2")
            ouigoKeyPair = keyUtils.loadKeys(context, "ouigo")
            sginPrivateKey = keyUtils.loadKeys(context, "tr1ntr1n")
            renfeKeyPair = keyUtils.loadKeys(context, "renfe")
            trenitaliaKeyPair = keyUtils.loadKeys(context, "trenitalia")
    }
}

private fun setupBouncyCastle() {
    val provider =
        Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            ?: // Web3j will set up the provider lazily when it's first used.
            return
    if (provider.javaClass == BouncyCastleProvider::class.java) {
        // BC with same package name, shouldn't happen in real life.
        return
    }
    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
    Security.insertProviderAt(BouncyCastleProvider(), 1)
}
