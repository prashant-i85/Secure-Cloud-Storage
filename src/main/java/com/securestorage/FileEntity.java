package com.securestorage;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@DynamoDbBean
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileEntity {
    private String fileId;
    private String ownerId;
    private String filename;
    private String s3Key;
    private String encryptedAesKey; // The AES key encrypted by KMS
    private String iv; // Initialization Vector
    private String contentType;

    @DynamoDbPartitionKey
    public String getFileId() { return fileId; }

    public String getOwnerId() { return ownerId; }
    public String getFilename() { return filename; }
    public String getS3Key() { return s3Key; }
    public String getEncryptedAesKey() { return encryptedAesKey; }
    public String getIv() { return iv; }
    public String getContentType() { return contentType; }
}