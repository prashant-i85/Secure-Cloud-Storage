package com.securestorage;

import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.Key; // <--- This was missing
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class FileService {

    private final S3Client s3Client;
    private final DynamoDbEnhancedClient dynamoDb;
    private final EncryptionService encryptionService;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.dynamodb.table}")
    private String tableName;

    public FileService(S3Client s3Client, DynamoDbEnhancedClient dynamoDb, EncryptionService encryptionService) {
        this.s3Client = s3Client;
        this.dynamoDb = dynamoDb;
        this.encryptionService = encryptionService;
    }

    // 1. Define the limit (200 MB in bytes)
    private static final long MAX_STORAGE_LIMIT = 200 * 1024 * 1024; // 200MB

    public void uploadFile(MultipartFile file, String ownerId) throws Exception {
        // --- CHECK STORAGE LIMIT START ---
        long newFileSize = file.getSize();
        long currentUsedStorage = getUsedStorage(ownerId);

        if (currentUsedStorage + newFileSize > MAX_STORAGE_LIMIT) {
            throw new RuntimeException("Storage Limit Exceeded! You have used "
                    + (currentUsedStorage / 1024 / 1024) + "MB of your 200MB limit.");
        }
        // --- CHECK STORAGE LIMIT END ---

        // 1. Prepare Metadata
        String fileId = UUID.randomUUID().toString();
        String s3Key = ownerId + "/" + fileId;

        // 2. Encryption Logic
        SecretKey aesKey = encryptionService.generateAesKey();
        byte[] iv = encryptionService.generateIv();
        byte[] encryptedContent = encryptionService.encryptData(file.getBytes(), aesKey, iv);
        String encryptedAesKey = encryptionService.encryptKeyWithKms(aesKey);

        // 3. Upload Encrypted File to S3
        s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(s3Key).build(),
                RequestBody.fromBytes(encryptedContent));

        // 4. Save Metadata to DynamoDB (WITH SIZE)
        FileEntity entity = new FileEntity(
                fileId,
                ownerId,
                file.getOriginalFilename(),
                s3Key,
                encryptedAesKey,
                Base64.getEncoder().encodeToString(iv),
                file.getContentType(),
                newFileSize // <--- Saving the size now
        );

        dynamoDb.table(tableName, TableSchema.fromBean(FileEntity.class)).putItem(entity);
    }

    // Helper method to calculate total storage used by a user
    public long getUsedStorage(String ownerId) {
        // Get all files belonging to this user
        List<FileEntity> userFiles = listFiles(ownerId);

        // Sum up their file sizes
        return userFiles.stream()
                .mapToLong(file -> file.getFileSize() == null ? 0 : file.getFileSize())
                .sum();
    }

    public byte[] downloadFile(String fileId) throws Exception {
        // 1. Create Key Object (FIXED THIS PART)
        // CORRECTED LINE: Use .partitionValue() instead of .partitionKey()
        Key key = Key.builder()
                .partitionValue(AttributeValue.builder().s(fileId).build())
                .build();

        // 2. Get Metadata
        FileEntity entity = dynamoDb.table(tableName, TableSchema.fromBean(FileEntity.class))
                .getItem(key);

        if(entity == null) throw new RuntimeException("File not found");

        // 3. Download Encrypted Content from S3
        byte[] encryptedContent = s3Client.getObject(req -> req.bucket(bucketName).key(entity.getS3Key()))
                .readAllBytes();

        // 4. Decrypt Key via KMS
        SecretKey aesKey = encryptionService.decryptKeyWithKms(entity.getEncryptedAesKey());
        byte[] iv = Base64.getDecoder().decode(entity.getIv());

        // 5. Decrypt Content
        return encryptionService.decryptData(encryptedContent, aesKey, iv);
    }

    // Update this method to accept ownerId
    public List<FileEntity> listFiles(String ownerId) {
        // We scan the table and filter by the Owner ID in Java (Simple and effective for this scale)
        return dynamoDb.table(tableName, TableSchema.fromBean(FileEntity.class))
                .scan()
                .items()
                .stream()
                .filter(file -> file.getOwnerId().equals(ownerId)) // <--- The Security Fix
                .collect(Collectors.toList());
    }


    // Add these imports at the top if missing:
    // import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

    public void deleteFile(String fileId, String ownerId) {
        // 1. Get Metadata to find the S3 Key
        FileEntity entity = getFileMetadata(fileId);

        // Security check: Ensure the user owns this file
        if (!entity.getOwnerId().equals(ownerId)) {
            throw new RuntimeException("Unauthorized access");
        }

        // 2. Delete from S3
        s3Client.deleteObject(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(entity.getS3Key())
                .build());

        // 3. Delete from DynamoDB
        Key key = Key.builder()
                .partitionValue(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(fileId).build())
                .build();
        dynamoDb.table(tableName, TableSchema.fromBean(FileEntity.class)).deleteItem(key);
    }

    // Add this new method so the Controller can get the filename
    public FileEntity getFileMetadata(String fileId) {
        Key key = Key.builder()
                .partitionValue(software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(fileId).build())
                .build();
        return dynamoDb.table(tableName, TableSchema.fromBean(FileEntity.class)).getItem(key);
    }
}