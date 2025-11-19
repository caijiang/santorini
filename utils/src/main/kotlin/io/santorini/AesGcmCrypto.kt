package io.santorini

import java.security.SecureRandom
import java.util.*
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * TODO 注意会抛出 AEADBadTagException
 * @author CJ
 */
object AesGcmCrypto {

    private const val AES = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128 // bits
    private const val IV_LENGTH = 12       // bytes

    private val random = SecureRandom()

    /**
     * 加密字符串（输出为 Base64URL，无 padding）
     */
    fun encrypt(plaintext: String, keyBytes: ByteArray): String {
        val key: SecretKey = SecretKeySpec(keyBytes, AES)
        val iv = ByteArray(IV_LENGTH)
        random.nextBytes(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 拼接 IV + ciphertext
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)

        // Base64URL 编码（不含 = / +）
        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
    }

    /**
     * 解密 Base64URL 字符串
     */
    fun decrypt(encoded: String, keyBytes: ByteArray): String {
        val combined = Base64.getUrlDecoder().decode(encoded)
        val key: SecretKey = SecretKeySpec(keyBytes, AES)

        val iv = combined.copyOfRange(0, IV_LENGTH)
        val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val plaintext = cipher.doFinal(ciphertext)
        return plaintext.toString(Charsets.UTF_8)
    }

    /**
     * 随机生成 128-bit 密钥
     */
    fun generateKey(): ByteArray {
        val key = ByteArray(16)
        random.nextBytes(key)
        return key
    }
}
