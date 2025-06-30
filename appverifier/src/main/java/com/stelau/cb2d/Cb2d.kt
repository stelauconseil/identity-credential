package com.stelau.cb2d

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.android.mdl.appreader.VerifierApp
import com.stelau.cb2d.utils.CevManager
import com.stelau.cbor.CborManager
import com.stelau.democb2d.utils.KeyManager
import com.stelau.democb2d.utils.KeyUtils
import fr.gouv.franceidentite.libs.cev.Cev
import kotlinx.coroutines.runBlocking
import nl.minvws.encoding.Base45
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.PublicKey
import java.text.Normalizer
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.spec.SecretKeySpec

object Cb2d {
    fun initializeCev() {
        val context = VerifierApp.getApplicationContext()
        CevManager.initializeCev(context)
    }

    private fun normalize(input: String?): String? {
        return if (input == null) null
        else
            Normalizer.normalize(input, Normalizer.Form.NFKD)
                .replace("\\p{M}".toRegex(), "")
                .uppercase()
                .replace(",", " ")
                .replace("  ", " ")
    }

    fun encodeCb2d(
        nom: String?,
        prenoms: String,
        nomUsage: String?,
        DoB: String,
        publicKey: PublicKey,
        selectedRecipients: Set<String>
    ): ByteArray {
        val keyUtils = KeyUtils()

        val data = HashMap<String, Any?>()
        data["nom"] = nom
        data["prénoms"] = prenoms
        data["nom d'usage"] = nomUsage
        data["date de naissance"] = DoB
        data["clé publique du mobile"] = KeyManager.deviceKeyPair!!.public.encoded

        val vds = runBlocking {
            CevManager.cevSginInstance!!.encode(
                Cev.Document.Type.DOC_105,
                byteArrayOf(0x4A, 0x49, 0x20),
                "TR1NTR1N",
                data
            )
        }

        // Générer la clé secrète AES
        val idEncKey = keyUtils.generateAESKey()

        // Chiffrer le VDS avec la clé AES
        val encResult = keyUtils.aesEncrypt(vds, idEncKey)

        // Générer le bi-clé éphémère du mobile pour la dérivation de clé
        val deviceEphKeyPair = keyUtils.generateECKeyPair()

        // Générer les clés partagées
        val sharedSecrets =
            selectedRecipients
                .map { recipient ->
                    val recipientPublicKey =
                        when (recipient) {
                            "SNCF" -> publicKey
                            "OUIGO" -> KeyManager.ouigoKeyPair!!.public
                            "RENFE" -> KeyManager.renfeKeyPair!!.public
                            "TRENITALIA" -> KeyManager.trenitaliaKeyPair!!.public
                            else ->
                                throw IllegalArgumentException(
                                    "Unknown recipient: $recipient"
                                )
                        }
                    recipient to
                            keyUtils.generateSharedSecret(
                                deviceEphKeyPair.private,
                                recipientPublicKey
                            )
                }
                .toMap()

        // Dérivation de la clé AES à partir du secret partagé
        val derivedKeys =
            sharedSecrets.mapValues { (_, sharedSecret) -> keyUtils.getAESKey(sharedSecret) }

        // Chiffrer les clés partagées avec les clés AES dérivées
        val cipheredKeys =
            derivedKeys.mapValues { (_, derivedKey) ->
                keyUtils.aesEncrypt(idEncKey.encoded, SecretKeySpec(derivedKey, "AES"))
            }

        val extData = mutableMapOf<String, Any?>()
        extData["état civil"] = encResult
        extData["Kenc"] = deviceEphKeyPair.public.encoded

        val recipientsMap =
            cipheredKeys.map { (recipient, cipheredKey) ->
                mapOf("destinataire" to recipient, "secret" to cipheredKey)
            }

        extData["destinataires"] = recipientsMap
        return runBlocking {
            CevManager.cevDeviceInstance!!.encode(
                Cev.Document.Type.DOC_105,
                byteArrayOf(0x4A, 0x49, 0x30),
                "00000000",
                extData
            )
        }
    }

//    fun decodeCb2d2(cb2d: String, userInfo: UserInfo): UserInfo {
//            // Log.d("VDS", cb2d)
//            val context: Context = VerifierApp.getApplicationContext()
//            if (cb2d.substring(0,5) == "mdoc:") {
//                val mdocContent = cb2d.substring(5)
//                // Log.d("VDS" ,mdocContent)
//                    val cborStruct = Base64.getUrlDecoder().decode(mdocContent)
//                // Log.d("VDS", Base64.getEncoder().encodeToString(cborStruct))
//                CevManager.initializeCev(context)
//                val cevData = CborManager.decode(cborStruct).cevData
//                // Log.d("VDS", cevData)
//                val cevDataBase45 = Base45.getEncoder().encode(cevData)
//                // Log.d("VDS", cevDataBase45)
//
//                runBlocking {
//                      try {
//                         CevManager.cevDecodeInstance?.decode(String(cevDataBase45, Charsets.UTF_8).toByteArray())
//                         userInfo
//                         } catch (e: Exception) {
//                             Log.e("VDS", "Error decoding CB2D", e);
//                         userInfo.copy(
//                                 errorCode = "Error decoding VDS",
//                                 errorMessage = e.toString()
//                             )
//                     }
//                }
//
//                if (res != null) {
//
//                    @Suppress("UNCHECKED_CAST")
//                    val extensions: Map<String, String> = (res?.extensions?.get("termsOfUse") as? Map<*, *>)?.let { it as Map<String, String> } ?: emptyMap()
//
//                    val issuanceDateTime =
//                        LocalDateTime.parse(
//                            res?.header?.get("signDateTimestamp")?.toString() ?: "",
//                            DateTimeFormatter.ISO_OFFSET_DATE_TIME
//                        )
//                    val now = ZonedDateTime.now(ZoneOffset.UTC)
//                    val duration = Duration.between(issuanceDateTime, now)
//
//                    val days = duration.toDays()
//                    val hours = duration.toHours() % 24
//                    val minutes = duration.toMinutes() % 60
//                    val seconds = duration.seconds % 60
//
//                    val elapsedTime =
//                        buildString {
//                            if (days > 0) append("$days days, ")
//                            if (hours > 0) append("$hours hour, ")
//                            if (minutes > 0) append("$minutes minutes, ")
//                            append("$seconds seconds ago")
//                        }
//                            .trimEnd(',', ' ')
//
//                        userInfo.copy(
//                            nom =
//                            if (res?.information?.get("nom") != null &&
//                                res?.information!!["nom"] != ""
//                            ) {
//                                res?.information!!["nom"].toString()
//                            } else "n/a",
//                            usage =
//                            if (res?.information?.get("nom d'usage") != null &&
//                                res?.information!!["nom d'usage"] != ""
//                            )
//                                res!!.information["nom d'usage"].toString()
//                            else "n/a",
//                            prenom = res!!.information["prénoms"].toString(),
//                            dateNaissance = res!!.information["date de naissance"].toString(),
//                            expiration = res!!.header["signDateTimestamp"].toString(),
//                            issuanceDateTime = issuanceDateTime.toString(),
//                            issuanceElapsedTime = elapsedTime,
//                            tou = extensions["en"] ?: "",
//                            errorCode = "",
//                            errorMessage = "")
//                }
//
//            }
//            return userInfo
//    }

    fun decodeCb2d(cb2d: String, userInfo: UserInfo): UserInfo {
        if (!cb2d.startsWith("mdoc:")) {
            return userInfo.copy(
                errorCode = "Invalid format",
                errorMessage = "CB2D does not start with 'mdoc:'"
            )
        }

        return try {
            val mdocContent = cb2d.substring(5)
            val cborStruct = Base64.getUrlDecoder().decode(mdocContent)
            CevManager.initializeCev(VerifierApp.getApplicationContext())

            val cevData = CborManager.decode(cborStruct).cevData
            val cevDataBase45 = Base45.getEncoder().encode(cevData)

            val document = runBlocking {
                try {
                    CevManager.cevDestInstanceMap["SNCF"]?.decode(String(cevDataBase45, Charsets.UTF_8).toByteArray())
                } catch (e: Exception) {
                    Log.e("VDS", "Error decoding CB2D", e)
                    userInfo.copy(
                        errorCode = "Decode Error",
                        errorMessage = "Failed to decode document: ${e.message}"
                    )
                }
            } ?: return userInfo.copy(
                errorCode = "Null Result",
                errorMessage = "Received null document from decoder"
            )
            Log.d("VDS", "Document: $document")
            
            // Debug log all available properties
            document.javaClass.declaredFields.forEach { field ->
                field.isAccessible = true
                Log.d("VDS", "Field: ${field.name} = ${field.get(document)}")
            }

            // Safely get extensions
            @Suppress("UNCHECKED_CAST")
            val extensions = try {
                val extensionsField = document.javaClass.getDeclaredField("extensions")
                extensionsField.isAccessible = true
                val extensionsMap = extensionsField.get(document) as? Map<*, *>
                val termsOfUse = extensionsMap?.get("termsOfUse")
                if (termsOfUse is Map<*, *>) {
                    termsOfUse as? Map<String, String> ?: emptyMap()
                } else {
                    emptyMap()
                }
            } catch (e: Exception) {
                Log.e("VDS", "Error getting extensions", e)
                emptyMap()
            }
            
            // Safely get sign date timestamp
            val signDateTimestamp = try {
                val headerField = document.javaClass.getDeclaredField("header")
                headerField.isAccessible = true
                val header = headerField.get(document) as? Map<*, *>
                header?.get("signDateTimestamp")?.toString() ?: ""
            } catch (e: Exception) {
                Log.e("VDS", "Error getting sign date timestamp", e)
                ""
            }
            
            val issuanceDateTime = if (signDateTimestamp.isNotEmpty()) {
                try {
                    LocalDateTime.parse(signDateTimestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                } catch (e: Exception) {
                    Log.e("VDS", "Error parsing date", e)
                    LocalDateTime.now()
                }
            } else {
                LocalDateTime.now()
            }

            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val duration = Duration.between(issuanceDateTime, now)
            val days = duration.toDays()
            val hours = duration.toHours() % 24
            val minutes = duration.toMinutes() % 60
            val seconds = duration.seconds % 60

            val elapsedTime = buildString {
                if (days > 0) append("$days day${if (days > 1) "s" else ""}, ")
                if (hours > 0) append("$hours hour${if (hours > 1) "s" else ""}, ")
                if (minutes > 0) append("$minutes minute${if (minutes > 1) "s" else ""}, ")
                append("$seconds second${if (seconds != 1L) "s" else ""}")
            }.trimEnd(',', ' ')

            // Safely get certificateReference
            val certificateReference = try {
                val headerField = document.javaClass.getDeclaredField("header")
                headerField.isAccessible = true
                val header = headerField.get(document) as? Map<*, *>
                val certRef = header?.get("certificateReference") as? Map<*, *>

                certRef?.let { ref ->
                    val staticBlock = ref["staticBlock"]?.toString() ?: ""
                    val dynamicBlock = ref["dynamicBlock"]?.toString() ?: ""
                    "certificateReference={staticBlock=$staticBlock, dynamicBlock=$dynamicBlock}"
                } ?: "certificateReference={}"
            } catch (e: Exception) {
                Log.e("VDS", "Error getting certificate Reference", e)
                "certificateReference={error}"
            }

            // Safely get sign certificateReference
            val decipheredBlock: Boolean = try {
                val headerField = document.javaClass.getDeclaredField("header")
                headerField.isAccessible = true
                val header = headerField.get(document) as? Map<*, *>
                header?.get("decipheredBlock") as? Boolean ?: false
            } catch (e: Exception) {
                Log.e("VDS", "Error getting deciphered Block", e)
                false
            }

            // Safely get information map
            val information = try {
                val infoField = document.javaClass.getDeclaredField("information")
                infoField.isAccessible = true
                infoField.get(document) as? Map<*, *> ?: emptyMap<String, Any>()
            } catch (e: Exception) {
                Log.e("VDS", "Error getting information map", e)
                emptyMap<String, Any>()
            }
            
            // Safely get header map
            val header = try {
                val headerField = document.javaClass.getDeclaredField("header")
                headerField.isAccessible = true
                headerField.get(document) as? Map<*, *> ?: emptyMap<String, Any>()
            } catch (e: Exception) {
                Log.e("VDS", "Error getting header map", e)
                emptyMap<String, Any>()
            }
            
            // Helper function to safely get string from map
            fun getStringSafely(map: Map<*, *>, key: String, default: String = ""): String {
                return try {
                    (map[key]?.toString() ?: default).takeIf { it.isNotEmpty() } ?: default
                } catch (e: Exception) {
                    Log.e("VDS", "Error getting value for key $key", e)
                    default
                }
            }
            
            userInfo.copy(
                nom = getStringSafely(information, "nom"),
                usage = getStringSafely(information, "nom d'usage"),
                prenom = getStringSafely(information, "prénoms"),
                dateNaissance = getStringSafely(information, "date de naissance"),
                expiration = getStringSafely(header, "signDateTimestamp"),
                issuanceDateTime = issuanceDateTime.toString(),
                issuanceElapsedTime = elapsedTime,
                tou = extensions["en"] ?: "",
                certificateReference = getStringSafely(header, "certificateReference"),
                decipheredBlock = getStringSafely(header, "decipheredBlock"),
                errorCode = "",
                errorMessage = ""
            )

        } catch (e: Exception) {
            Log.e("VDS", "Error in decodeCb2d", e)
            userInfo.copy(
                errorCode = "Processing Error",
                errorMessage = e.message ?: "Unknown error occurred"
            )
        }
    }


    data class UserInfo(
        var nom: String = "",
        var usage: String = "",
        var prenom: String = "",
        var dateNaissance: String = "",
        var expiration: String = "",
        val issuanceDateTime: String = "",
        val expirationDateTime: String = "",
        val issuanceElapsedTime: String = "",
        val tou: String = "",
        val certificateReference : String = "",
        val decipheredBlock : String = "",
        var errorCode: String = "",
        var errorMessage: String = "",
    )

    fun addPictoPng(qr: ImageBitmap, orientation: String = "portrait"): ImageBitmap {
        val format = Bitmap.CompressFormat.PNG
        val qrBorderRatio = 0.1125
        val ratioPictosHeight = 0.213
        val ratioPictoHeightPadding = 0.556
        val leftPictoWidthHeightRatio = 0.689

        val qrImg = BitmapFactory.decodeStream(ByteArrayInputStream(qr))

        val oldQrSize = qrImg.width to qrImg.height
        val deltaWidth = (oldQrSize.first * qrBorderRatio).toInt()
        val deltaHeight = (oldQrSize.second * qrBorderRatio).toInt()
        val borderWidth = deltaWidth / 2
        val borderHeight = deltaHeight / 2

        val paddedQrCodeImg = Bitmap.createBitmap(
            oldQrSize.first + deltaWidth,
            oldQrSize.second + deltaHeight,
            Bitmap.Config.ARGB_8888
        ).apply {
            Canvas(this).apply {
                drawColor(Color.WHITE)
                drawBitmap(
                    qrImg,
                    borderWidth.toFloat(),
                    borderHeight.toFloat(),
                    null
                )
            }
        }

        val pictosBlockHeight = (paddedQrCodeImg.height * ratioPictosHeight).toInt()
        val pictosBlockWidth = paddedQrCodeImg.width
        val pictosWidthNoPadding = pictosBlockWidth - deltaWidth
        val leftQuadrantWidth = (pictosWidthNoPadding / 3.0).toInt()
        val rightQuadrantWidth = leftQuadrantWidth

        val pictosImg =
            Bitmap.createBitmap(pictosBlockWidth, pictosBlockHeight, Bitmap.Config.ARGB_8888)
                .apply { Canvas(this).apply { drawColor(Color.WHITE) } }

        val paint = Paint()

        val context = VerifierApp.getApplicationContext()

        try {
            val logoWidth = (leftQuadrantWidth * 0.6).toInt()
            val logoHeight = (logoWidth * leftPictoWidthHeightRatio).toInt()
            val logo = loadPicto(context, "icon_eu_fr_blue", logoWidth, logoHeight, "picto", format)
            val xOffset = borderWidth
            val yOffset = (pictosBlockHeight / 2.0 - logoHeight / 2.0).toInt()
            if (logo != null) {
                Canvas(pictosImg).drawBitmap(logo, xOffset.toFloat(), yOffset.toFloat(), paint)
            }
        } catch (e: Exception) {
            Log.e("addPictoPng", "Error loading logo picto", e)
        }

        /*try {
            val networkPictoWidth = (leftQuadrantWidth * 0.4).toInt()
            val networkPicto = loadPicto(
                context,
                "default_offline",
                networkPictoWidth,
                networkPictoWidth,
                "picto",
                format
            )
            val xOffset = borderWidth + (leftQuadrantWidth * 0.6).toInt()
            val yOffset = (pictosBlockHeight / 2.0 - networkPictoWidth / 2.0).toInt()
            if (networkPicto != null) {
                Canvas(pictosImg).drawBitmap(networkPicto, xOffset.toFloat(), yOffset.toFloat(), paint)
            }
        }   catch (e: Exception) {
            Log.e("addPictoPng", "Error loading network picto", e)
        }*/

        val rightPictoWidth = (rightQuadrantWidth * 0.5).toInt()
        try {
            val rightPicto =
                loadPicto(
                    context,
                    "export_white",
                    rightPictoWidth,
                    rightPictoWidth,
                    "picto",
                    format
                )
            val xOffset = pictosBlockWidth - rightQuadrantWidth - borderWidth
            val yOffset = (borderHeight * ratioPictoHeightPadding).toInt()
            if (rightPicto != null) {
                Canvas(pictosImg)
                    .drawBitmap(rightPicto, xOffset.toFloat(), yOffset.toFloat(), paint)
            }
        } catch (e: Exception) {
            Log.e("addPictoPng", "Error loading right picto", e)
        }

        try {
            val rightmostPicto =
                loadPicto(
                    context,
                    "id_white",
                    rightPictoWidth,
                    rightPictoWidth,
                    "picto",
                    format
                )
            val xOffset = pictosBlockWidth - rightQuadrantWidth / 2 - borderWidth
            val yOffset = (borderHeight * ratioPictoHeightPadding).toInt()
            if (rightmostPicto != null) {
                Canvas(pictosImg)
                    .drawBitmap(rightmostPicto, xOffset.toFloat(), yOffset.toFloat(), paint)
            }
        } catch (e: Exception) {
            Log.e("addPictoPng", "Error loading rightmost picto", e)
        }

        val combinedImg =
            Bitmap.createBitmap(
                paddedQrCodeImg.width,
                paddedQrCodeImg.height + pictosBlockHeight,
                Bitmap.Config.ARGB_8888
            )
                .apply {
                    Canvas(this).apply {
                        drawColor(Color.WHITE)
                        drawBitmap(paddedQrCodeImg, 0f, 0f, paint)
                        drawBitmap(pictosImg, 0f, paddedQrCodeImg.height.toFloat(), paint)
                    }
                }

        return combinedImg.asImageBitmap()
    }

    private fun ByteArrayInputStream(qr: ImageBitmap): ByteArrayInputStream {
        return ByteArrayInputStream(qr.toByteArray())
    }

    private fun ImageBitmap.toByteArray(): ByteArray {
        val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawBitmap(this.asAndroidBitmap(), 0f, 0f, null)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun loadPicto(
        context: Context,
        name: String,
        width: Int,
        height: Int,
        folder: String,
        format: Bitmap.CompressFormat
    ): Bitmap? {
        val resId = context.resources.getIdentifier(name, "drawable", context.packageName)
        if (resId == 0) {
            Log.e("loadPicto", "Resource not found: $name")
            return null
        }
        return try {
            val inputStream = context.resources.openRawResource(resId)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } catch (e: Exception) {
            Log.e("loadPicto", "Error loading SVG: $name", e)
            null
        }
    }
}
