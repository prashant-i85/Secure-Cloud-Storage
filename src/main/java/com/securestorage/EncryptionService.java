package com.securestorage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    private final KmsClient kmsClient;

    @Value("${aws.kms.key-id}")
    private String kmsKeyId;

    public EncryptionService(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    // 1. Generate a new AES Key
    public SecretKey generateAesKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    // 2. Encrypt Data with AES (Local)
    public byte[] encryptData(byte[] data, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(data);
    }

    // 3. Decrypt Data with AES (Local)
    public byte[] decryptData(byte[] encryptedData, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(encryptedData);
    }

    // 4. Encrypt the AES Key using KMS (Cloud)
    public String encryptKeyWithKms(SecretKey aesKey) {
        SdkBytes bytes = SdkBytes.fromByteArray(aesKey.getEncoded());
        EncryptRequest req = EncryptRequest.builder()
                .keyId(kmsKeyId)
                .plaintext(bytes)
                .build();
        return Base64.getEncoder().encodeToString(kmsClient.encrypt(req).ciphertextBlob().asByteArray());
    }

    // 5. Decrypt the AES Key using KMS (Cloud)
    public SecretKey decryptKeyWithKms(String encryptedKeyBase64) {
        SdkBytes bytes = SdkBytes.fromByteArray(Base64.getDecoder().decode(encryptedKeyBase64));
        DecryptRequest req = DecryptRequest.builder().ciphertextBlob(bytes).build();
        byte[] decryptedBytes = kmsClient.decrypt(req).plaintext().asByteArray();
        return new SecretKeySpec(decryptedBytes, "AES");
    }

    public byte[] generateIv() {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}