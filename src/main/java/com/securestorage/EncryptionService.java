package com.securestorage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class EncryptionService {

    @Value("${app.master-key}")
    private String masterKeyString;

    private SecretKey masterKey;

    // Helper to load the Master Key lazily
    private SecretKey getMasterKey() {
        if (masterKey == null) {
            byte[] decodedKey = Base64.getDecoder().decode(masterKeyString);
            masterKey = new SecretKeySpec(decodedKey, "AES");
        }
        return masterKey;
    }

    // 1. Generate a new AES Key for a specific file
    public SecretKey generateAesKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        return keyGen.generateKey();
    }

    // 2. Encrypt Data (File Content)
    public byte[] encryptData(byte[] data, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(data);
    }

    // 3. Decrypt Data (File Content)
    public byte[] decryptData(byte[] encryptedData, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(encryptedData);
    }

    // 4. Encrypt the File Key using the Master Key (Replaces KMS)
    public String encryptKeyLocally(SecretKey fileKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES"); // Standard AES Wrapping
        cipher.init(Cipher.WRAP_MODE, getMasterKey());
        byte[] wrappedKey = cipher.wrap(fileKey);
        return Base64.getEncoder().encodeToString(wrappedKey);
    }

    // 5. Decrypt the File Key using the Master Key (Replaces KMS)
    public SecretKey decryptKeyLocally(String encryptedKeyBase64) throws Exception {
        byte[] wrappedKey = Base64.getDecoder().decode(encryptedKeyBase64);
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.UNWRAP_MODE, getMasterKey());
        return (SecretKey) cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
    }

    public byte[] generateIv() {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}