package udumeoli.tripphoto.image.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Suppress("MagicNumber")
@Service
class KmsService(
    @Value("\${app.security.kms-master-key}") private val masterKeyString: String,
) {
    private val masterKey: SecretKeySpec =
        masterKeyString.toByteArray(Charsets.UTF_8).let { keyBytes ->
            require(keyBytes.size == 32) { "KMS Master Key must be 32 bytes (256-bit)" }
            SecretKeySpec(keyBytes, "AES")
        }
    private val secureRandom = SecureRandom()

    /**
     * 무작위 256-bit (32 bytes) 대칭키(DEK)를 생성합니다.
     */
    fun generateDek(): ByteArray {
        val dek = ByteArray(32)
        secureRandom.nextBytes(dek)
        return dek
    }

    /**
     * 평문 DEK를 마스터 키를 사용해 AES/GCM으로 암호화하여 Base64 문자열로 반환합니다.
     */
    fun encryptDek(dek: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec)

        val cipherText = cipher.doFinal(dek)

        // IV + CipherText 를 이어붙여서 Base64 인코딩
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)

        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * Base64 인코딩된 암호문(IV + CipherText)을 받아 마스터 키로 복호화하여 평문 DEK를 반환합니다.
     */
    fun decryptDek(encryptedDekBase64: String): ByteArray {
        val combined = Base64.getDecoder().decode(encryptedDekBase64)
        val iv = ByteArray(12)
        System.arraycopy(combined, 0, iv, 0, 12)

        val cipherText = ByteArray(combined.size - 12)
        System.arraycopy(combined, 12, cipherText, 0, cipherText.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, masterKey, spec)

        return cipher.doFinal(cipherText)
    }
}
