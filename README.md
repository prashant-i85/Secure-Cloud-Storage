# 🔐 Secure Cloud Storage with AES/RSA Encryption

A secure file storage application built with \*\*Java Spring Boot\*\* and \*\*AWS\*\*. This project implements hybrid envelope encryption in Java so files are encrypted \*before\* they touch the cloud storage.

## 🚀 Features

* \*\*Hybrid Encryption (Java)\*\*: Files are encrypted locally using \*\*AES-256-GCM\*\*.
* \*\*Key Protection\*\*: AES keys are encrypted using \*\*RSA-OAEP (SHA-256)\*\*; RSA keys are managed server-side (not committed).
* \*\*Secure Storage\*\*: Encrypted files are stored in \*\*AWS S3\*\*.
* \*\*Metadata Management\*\*: File details (name, type, encrypted AES key, IV, size) are stored in \*\*AWS DynamoDB\*\*.
* \*\*Authentication\*\*: User login and session management via \*\*AWS Cognito\*\* (if configured).

## 🛠️ Tech Stack

* \*\*Backend:\*\* Java 17, Spring Boot 3.x
* \*\*Cloud Provider:\*\* AWS (S3, DynamoDB)
* \*\*Database:\*\* Amazon DynamoDB (Enhanced Client)
* \*\*Security:\*\* Spring Security, AWS Cognito, hybrid RSA\/AES encryption implemented in Java (no KMS)
* \*\*Frontend:\*\* HTML5, Bootstrap 5, Vanilla JavaScript

## 🏗️ Architecture (updated)

1.  **Upload:** User selects file -> Backend generates ephemeral AES-256 key + IV -> Encrypts file with AES-256-GCM -> Encrypts AES key with server RSA public key (RSA-OAEP SHA-256) -> Uploads encrypted file to S3 & metadata to DynamoDB.
2.  **Download:** Backend fetches metadata -> Decrypts AES key using server RSA private key -> Downloads encrypted file -> Decrypts file with AES-256-GCM -> Streams plaintext to user.

## 🔐 How encryption is implemented (high level)

* AES algorithm: AES\/GCM\/NoPadding (AES-256-GCM).
* RSA algorithm: RSA\/ECB\/OAEPWithSHA-256AndMGF1Padding (RSA-OAEP with SHA-256).
* Per-upload flow:
    1. Generate a random 256-bit AES key and 12-byte GCM IV on the backend at upload time.
    2. Encrypt the file payload with AES-256-GCM.
    3. Encrypt the AES key with the server RSA public key using RSA-OAEP (SHA-256).
    4. Store the encrypted object in S3 and save metadata in DynamoDB containing:
        * `encryptedAesKey` (Base64)
        * `iv` (Base64)
        * `s3Key`
        * `contentType`
        * `originalFilename`
        * `ownerId`
        * `fileSize`
* On download, the server uses its RSA private key to decrypt the AES key and then decrypts the payload with AES-GCM.

## Key handling & security notes

* Do not commit RSA private keys or AWS credentials. Keep them out of the repository and add local secret files to `.gitignore`.
* Store RSA private keys in a secure server-side store (encrypted DB field, HSM, or a secrets manager). Consider key rotation and strict access controls.
* AES keys are ephemeral and are not persisted in plaintext.
* Validate and sanitize filenames and all user input on both frontend and backend.
* Use HTTPS and enforce authentication and CORS policies in production.

## ⚙️ Setup & Configuration

### Prerequisites
* Java 17+ installed.
* AWS Account with active credentials.
* Maven installed.

### AWS Resources Required
1.  **S3 Bucket:** Private bucket for storage.
2.  **DynamoDB Table:** Named `SecureFiles` with Partition Key `fileId`.
3.  **KMS Key:** Symmetric Customer Managed Key.
4.  **Cognito User Pool:** With an App Client (Implicit Grant flow enabled).

### Environment Variables
To run this project, you must set the following configurations in `application.properties` or as Environment Variables:

```properties
aws.region=ap-south-1
aws.s3.bucket=YOUR_BUCKET_NAME
aws.dynamodb.table=SecureFiles
aws.kms.key-id=YOUR_KMS_KEY_ID
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=


In index.html:
redirect uri=
Cognito Domain=
Client ID=



