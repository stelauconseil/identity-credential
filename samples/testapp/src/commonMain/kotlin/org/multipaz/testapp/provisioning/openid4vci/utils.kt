package org.multipaz.testapp.provisioning.openid4vci

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.decodeURLPart
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.provisioning.evidence.Openid4VciCredentialOffer
import org.multipaz.provisioning.evidence.Openid4VciCredentialOfferAuthorizationCode
import org.multipaz.provisioning.evidence.Openid4VciCredentialOfferGrantless
import org.multipaz.provisioning.evidence.Openid4VciCredentialOfferPreauthorizedCode
import org.multipaz.provisioning.evidence.Openid4VciTxCode
import org.multipaz.util.Logger

// TODO: this is useful function, find a good home for it
fun String.decodeUrlQuery(): Map<String, String> {
    return split('&').associate {
        val index = it.indexOf('=')
        if (index < 0) {
            Pair(
                it.decodeURLPart(),
                ""
            )
        } else {
            Pair(
                it.decodeURLPart(0, index),
                it.decodeURLPart(index + 1)
            )
        }
    }
}

/**
 * Parse the Url Query component of an OID4VCI credential offer Url (from a deep link or Qr scan)
 * and return a [Openid4VciCredentialOffer] containing the
 * Credential Issuer Uri and Credential (Config) Id that are used for initiating the
 * OpenId4VCI Credential Offer Issuance flow using [ProvisioningModel.start] call.
 */
suspend fun extractCredentialIssuerData(
    urlQueryComponent: String
): Openid4VciCredentialOffer? {
    try {
        val params = urlQueryComponent.decodeUrlQuery()
        var credentialOfferString = params["credential_offer"]
        if (credentialOfferString == null) {
            val url = params["credential_offer_uri"]
            if (url == null) {
                Logger.e("CredentialOffer", "Could not parse offer")
                return null
            }
            val response = HttpClient().get(url) {}
            if (response.status != HttpStatusCode.OK) {
                Logger.e("CredentialOffer", "Error retrieving '$url'")
                return null
            }
            credentialOfferString = response.readBytes().decodeToString()
        }
        val json = Json.parseToJsonElement(credentialOfferString).jsonObject
        return Openid4VciCredentialOffer.parse(json)
    } catch (err: CancellationException) {
        throw err
    } catch (err: Exception) {
        Logger.e("CredentialOffer", "Parsing error", err)
        return null
    }
}

fun Openid4VciCredentialOffer.Companion.parse(json: JsonObject): Openid4VciCredentialOffer {
    val credentialIssuerUri = json["credential_issuer"]!!.jsonPrimitive.content
    val credentialConfigurationIds = json["credential_configuration_ids"]!!.jsonArray
    // Right now only use the first configuration id
    val credentialConfigurationId = credentialConfigurationIds[0].jsonPrimitive.content
    if (!json.containsKey("grants")) {
        return Openid4VciCredentialOfferGrantless(credentialIssuerUri, credentialConfigurationId)
    }
    val grants = json["grants"]!!.jsonObject
    val preAuthGrant = grants["urn:ietf:params:oauth:grant-type:pre-authorized_code"]
    if (preAuthGrant != null) {
        val grant = preAuthGrant.jsonObject
        val preauthorizedCode = grant["pre-authorized_code"]!!.jsonPrimitive.content
        val authorizationServer = grant["authorization_server"]?.jsonPrimitive?.content
        val txCode = extractTxCode(grant["tx_code"])
        return Openid4VciCredentialOfferPreauthorizedCode(
            issuerUri = credentialIssuerUri,
            configurationId = credentialConfigurationId,
            authorizationServer = authorizationServer,
            preauthorizedCode = preauthorizedCode,
            txCode = txCode
        )
    } else {
        val grant = grants["authorization_code"]?.jsonObject
        if (grant != null) {
            val issuerState = grant["issuer_state"]?.jsonPrimitive?.content
            val authorizationServer = grant["authorization_server"]?.jsonPrimitive?.content
            return Openid4VciCredentialOfferAuthorizationCode(
                issuerUri = credentialIssuerUri,
                configurationId = credentialConfigurationId,
                authorizationServer = authorizationServer,
                issuerState = issuerState
            )
        }
    }
    throw IllegalArgumentException("Could not parse credential offer")
}

private fun extractTxCode(txCodeJson: JsonElement?): Openid4VciTxCode? {
    return if (txCodeJson == null) {
        null
    } else {
        val obj = txCodeJson.jsonObject
        Openid4VciTxCode(
            description = obj["description"]?.jsonPrimitive?.content ?:
            "Enter transaction code that was previously communicated to you",
            length = obj["length"]?.jsonPrimitive?.intOrNull ?: Int.MAX_VALUE,
            isNumeric = obj["input_mode"]?.jsonPrimitive?.content != "text"
        )
    }
}