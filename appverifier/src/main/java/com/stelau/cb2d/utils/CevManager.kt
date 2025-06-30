package com.stelau.cb2d.utils

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import com.stelau.democb2d.utils.KeyManager.deviceKeyPair
import com.stelau.democb2d.utils.KeyManager.ouigoKeyPair
import com.stelau.democb2d.utils.KeyManager.renfeKeyPair
import com.stelau.democb2d.utils.KeyManager.sginPrivateKey
import com.stelau.democb2d.utils.KeyManager.sncfKeyPair
import com.stelau.democb2d.utils.KeyManager.sncfKeyPair2
import com.stelau.democb2d.utils.KeyManager.trenitaliaKeyPair
import fr.gouv.franceidentite.libs.cev.Cev
import fr.gouv.franceidentite.libs.cev.Cev.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.Base64

object CevManager {
    var cevDeviceInstance: Cev? = null
    var cevSginInstance: Cev? = null

    val cevDestInstanceMap = mutableMapOf<String, Cev>()

    fun initializeCev(context: Context) {
        val resourceProvider =
            object : Cev.ResourceProvider {

                private fun loadCertificateFromName(context: Context, name: String): String {
                    val resourceId =
                        context.resources.getIdentifier(name, "raw", context.packageName)
                    val inputStream = context.resources.openRawResource(resourceId)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    return reader.use { it.readText() }
                }

                private fun loadKeyFromName(context: Context, name: String): String {
                    val resourceId =
                        context.resources.getIdentifier(name, "raw", context.packageName)
                    val inputStream = context.resources.openRawResource(resourceId)
                    val reader = BufferedReader(InputStreamReader(inputStream))
                    return reader.use { it.readText() }
                }
                override suspend fun getCertificate(name: String): String {
                    return loadCertificateFromName(context, name.lowercase())
                }

                override suspend fun getKey(name: String): String {
                    val newName: String = name.lowercase() + "private"
                    return loadKeyFromName(context, newName)
                }
            }

        //         Dans le version de prod ne pas oublier de positionner validityPeriod à une valeur
        //         inférieur par exemple 350
        val sncfMultiKeyConfiguration =
            Configuration(604800, "SNCF", listOf(sncfKeyPair!!.private, sncfKeyPair2!!.private))
        cevDestInstanceMap["SNCF"] = Cev.create(sncfMultiKeyConfiguration, resourceProvider)

        val renfeConfiguration = Configuration(604800, "RENFE", listOf(renfeKeyPair!!.private))
        cevDestInstanceMap["RENFE"] = Cev.create(renfeConfiguration, resourceProvider)

        val trenitaliaConfiguration =
            Configuration(604800, "TRENITALIA", listOf(trenitaliaKeyPair!!.private))
        cevDestInstanceMap["TRENITALIA"] = Cev.create(trenitaliaConfiguration, resourceProvider)

        val ouigoConfiguration = Configuration(604800, "OUIGO", listOf(ouigoKeyPair!!.private))
        cevDestInstanceMap["OUIGO"] = Cev.create(ouigoConfiguration, resourceProvider)

        val deviceConfiguration = Configuration(60, "device", listOf(deviceKeyPair!!.private))
        cevDeviceInstance = Cev.create(deviceConfiguration)

        val sginConfiguration = Configuration(60, "sgin", listOf(sginPrivateKey!!.private))
        cevSginInstance = Cev.create(sginConfiguration, resourceProvider)
    }
}
    suspend fun getCB2D_IDD(lastname: String, firstname: String, dob: String, devicePubKey: ByteArray): String {
        val devicePubKeyBase64 = Base64.getEncoder().encodeToString(devicePubKey)
        val body =
            "{\"cev_standard\": \"105\",\"manifest\": \"4A4920\",\"get_image\": \"\", \"data\": {\"nom\": \"${lastname}\",\"prénoms\": \"${firstname}\",\"date de naissance\": \"${dob}\",\"clé publique du mobile\": \"${devicePubKeyBase64}\"}}"
                .toRequestBody("application/json".toMediaTypeOrNull())

        return withContext(Dispatchers.IO) {
            try {
                sendRequest(
                    "https://developpement.forge.france-identite.fr/cev-101-105/api/v1/encode",
                    body
                ) ?: ""
            } catch (e: IOException) {
                ""
            }
        }
    }

    suspend fun getDecoration(image: Bitmap, manifest: String, format: String): String {
        val baos = ByteArrayOutputStream()
        image.compress(Bitmap.CompressFormat.PNG, 100, baos)
        val imageBytes = baos.toByteArray()
        val encodedImage: String = Base64.getEncoder().encodeToString(imageBytes)

        val body = "{\"manifest\": \"$manifest\",\"format\": \"$format\",\"qrImage\": \"$encodedImage\"}"
            .toRequestBody("application/json".toMediaTypeOrNull())

        return withContext(Dispatchers.IO) {
            try {
                sendRequest(
                    "https://api.vds-verify.stelau.com/api/v1/decorate",
                    body
                ) ?: ""
            } catch (e: IOException) {
                ""
            }
        }
    }

    private fun sendRequest(host: String, body: RequestBody): String? {
        val client = OkHttpClient()
        val request =
            Request.Builder()
                .url(host)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build()
        return try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.string()
            } else {
                throw IOException("Unexpected code $response")
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

