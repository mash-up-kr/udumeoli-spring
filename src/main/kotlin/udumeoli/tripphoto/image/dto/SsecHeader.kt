package udumeoli.tripphoto.image.dto

import java.security.MessageDigest
import java.util.Base64

data class SsecHeader(
    val algorithm: String = "AES256",
    val keyBase64: String,
    val keyMd5Base64: String,
) {
    fun toHttpHeaders(): List<HttpHeader> =
        listOf(
            HttpHeader("x-amz-server-side-encryption-customer-algorithm", algorithm),
            HttpHeader("x-amz-server-side-encryption-customer-key", keyBase64),
            HttpHeader("x-amz-server-side-encryption-customer-key-MD5", keyMd5Base64),
        )

    companion object {
        fun fromDek(dek: ByteArray): SsecHeader {
            val dekBase64 = Base64.getEncoder().encodeToString(dek)
            val md5Bytes = MessageDigest.getInstance("MD5").digest(dek)
            val md5Base64 = Base64.getEncoder().encodeToString(md5Bytes)
            return SsecHeader(keyBase64 = dekBase64, keyMd5Base64 = md5Base64)
        }
    }
}
