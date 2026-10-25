# 🛡️ Secure Cloud Storage

[![Build](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Backend](https://img.shields.io/badge/Backend-Java%2017%20%7C%20Spring%20Boot%203.2-6db33f.svg?logo=spring)]()
[![Cryptography](https://img.shields.io/badge/Security-AES--256--GCM%20%2B%20Key--Wrap-blue.svg)]()
[![Cloud](https://img.shields.io/badge/Infrastructure-AWS%20(S3%20%2F%20DynamoDB%20%2F%20Cognito)-ff9900.svg?logo=amazon-aws)]()
[![Author](https://img.shields.io/badge/Maintainer-prashant--i85-darkblue.svg?logo=github)](https://github.com/prashant-i85)

**Secure Cloud Storage** is a full-stack, cloud-native file storage system built with **Java 17**, **Spring Boot 3**, and **AWS**. It implements an application-layer **hybrid envelope encryption** model, ensuring that file contents are fully encrypted *before* they ever touch cloud storage tiers.

---

## 📑 Core Navigation

* [1. System Overview](#1-system-overview)
* [2. Threat Model & Security Guarantees](#2-threat-model--security-guarantees)
* [3. Cryptographic Pipeline](#3-cryptographic-pipeline)
* [4. Architecture & System Flow](#4-architecture--system-flow)
* [5. Application Modules & Code Hierarchy](#5-application-modules--code-hierarchy)
* [6. RESTful Service Endpoints](#6-restful-service-endpoints)
* [7. Data Model & Persistence Specification](#7-data-model--persistence-specification)
* [8. Operational Deployment Guide](#8-operational-deployment-guide)
* [9. Local Execution Runbook](#9-local-execution-runbook)

---

## 1. System Overview

Standard cloud storage services store user files directly on disk or rely exclusively on server-managed keys. **Secure Cloud Storage** adopts a **Zero-Knowledge** posture using application-layer hybrid encryption:

* **Per-Object Ephemeral Keying**: Every file ingestion creates a dedicated, dynamically generated 256-bit AES symmetric key and unique initialization vector (IV).
* **Authenticated Encryption**: Employs AES in **Galois/Counter Mode (GCM)** to guarantee confidentiality alongside tamper detection via built-in authentication tags.
* **Key Wrapping Shield**: The ephemeral file key is wrapped using a protected master key before metadata persistence. S3 retains only raw ciphertext blobs; DynamoDB retains only wrapped key bytes.

---

## 2. Threat Model & Security Guarantees

| Security Objective | Threat Addressed | Mitigation |
|---|---|---|
| **Zero-Knowledge Cloud** | S3 bucket compromise / insider threat | Objects are fully encrypted before transmission to S3. Plaintext files never reside in AWS storage. |
| **Tamper Resistance** | Man-in-the-middle bit flipping | GCM authentication tags validate payload integrity during decryption. |
| **Tenant Isolation** | Cross-account data leakage | All queries strictly filter on the JWT `sub` identifier issued by Amazon Cognito. |
| **Ephemeral Key Lifecycle** | Key re-use exhaustion | AES keys are strictly single-use and destroyed from memory immediately following stream processing. |

---

## 3. Cryptographic Pipeline

```
  [ INCOMING FILE ]
         │
         ▼
  Generate Random 256-bit AES Key (K_file) + 12-byte IV
         │
         ├───► Encrypt File Payload ──► AES-256-GCM ──► Ciphertext Blob (Stored in S3)
         │
         └───► Wrap K_file with Master Key ──► Wrapped Key (Stored in DynamoDB)
```

1. **Payload Cipher**: `AES/GCM/NoPadding` with 128-bit authentication tag length.
2. **Key Encapsulation**: AES Key Wrap mode (`Cipher.WRAP_MODE` / `Cipher.UNWRAP_MODE`) leveraging a securely provisioned master key.
3. **Decryption Verification**: Decryption unwraps the per-file key, streams the ciphertext from S3, checks the GCM tag, and pipes plaintext directly into the response stream.

---

## 4. Architecture & System Flow

```
┌─────────────────────────────────────────────────────────────┐
│                      Client Interface                       │
│        Dynamic SPA • Drag-and-Drop Uploader • Media Player  │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS (Cognito JWT Bearer)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│              Secure Cloud Storage Application Core          │
│                     Spring Boot 3.2.x                       │
│                                                             │
│   [ SecurityFilterChain ] ──► [ FileController ]            │
│                                     │                       │
│                              [ FileService ]                │
│                                     │                       │
│                         [ EncryptionService ]               │
└──────────────┬──────────────────────┬───────────────────────┘
               │                      │
       Encrypted Blobs           Metadata & Wrapped Keys
               ▼                      ▼
┌───────────────────────────┐  ┌──────────────────────────────┐
│         Amazon S3         │  │       Amazon DynamoDB        │
│   Private Storage Bucket  │  │   SecureFiles Metadata Table │
└───────────────────────────┘  └──────────────────────────────┘
```

---

## 5. Application Modules & Code Hierarchy

```
SecureCloudStorage/
├── .platform/
│   └── nginx/conf.d/proxy.conf     # Nginx ingress tuning (1GB upload threshold)
├── src/main/java/com/securestorage/
│   ├── SecureStorageApplication.java # Service bootstrapping
│   ├── SecurityConfig.java           # Stateless OAuth2 JWT filter & CORS rules
│   ├── AwsConfig.java                # S3Client & DynamoDbEnhancedClient factories
│   ├── EncryptionService.java        # GCM symmetric cipher & master key wrapper
│   ├── FileService.java              # File ingestion, retrieval, and S3 lifecycle
│   ├── FileController.java           # Storage REST controllers
│   ├── ProfileController.java        # User avatar management
│   └── FileEntity.java               # DynamoDB document schema
├── src/main/resources/
│   ├── application.properties        # Externalized runtime parameters
│   └── static/index.html             # Client presentation layer
├── pom.xml                           # Dependency manifest
└── README.md
```

---

## 6. RESTful Service Endpoints

All secure routes require an `Authorization: Bearer <JWT>` header provided during Cognito authentication.

### File Controller (`/api/files`)

* `POST /api/files/upload`
  * **Payload**: `multipart/form-data` containing `file`
  * **Function**: Encrypts payload, wraps generated key, saves S3 blob, registers DynamoDB entry.
* `GET /api/files`
  * **Response**: Collection of `FileEntity` metadata associated with the authenticated user ID.
* `GET /api/files/{id}/download`
  * **Response**: Decrypted binary file stream with `Content-Disposition: attachment`.
* `DELETE /api/files/{id}`
  * **Function**: Deletes corresponding object from S3 and metadata record from DynamoDB.

### Profile Controller (`/api/profile`)

* `POST /api/profile/upload`: Uploads user profile photo to dedicated user avatar S3 path.
* `GET /api/profile/photo`: Fetches binary avatar image for authenticated user.

---

## 7. Data Model & Persistence Specification

Metadata records are managed in Amazon DynamoDB under the table name **`SecureFiles`**.

```json
{
  "fileId": "692c89be-91c6-4b68-8ce2-cba928a39151",
  "ownerId": "cognito-sub-uuid",
  "filename": "document.pdf",
  "contentType": "application/pdf",
  "fileSize": 1048576,
  "uploadDate": "2026-10-25T18:00:00Z",
  "s3Key": "5a9d8c32-b7b5-4a7b-a256-4cfbca77d3f1",
  "encryptedAesKey": "Base64WrappedKeyString==",
  "iv": "Base6412ByteIV=="
}
```

---

## 8. Operational Deployment Guide

### AWS Elastic Beanstalk
1. Package the target artifact:
   ```bash
   mvn clean package -DskipTests
   ```
2. Deploy the generated `.jar` along with the `.platform/` configuration bundle.
3. The `.platform/nginx/conf.d/proxy.conf` file automatically sets `client_max_body_size 1G;` to prevent HTTP 413 errors when ingesting larger payloads.
4. Set required environment variables directly in the Elastic Beanstalk Configuration panel.

---

## 9. Local Execution Runbook

### Requirements
* **JDK 17** or higher
* **Apache Maven 3.8+**
* An active **AWS Account** (S3, DynamoDB, Cognito User Pool)

### Quickstart

1. **Clone the project:**
   ```bash
   git clone https://github.com/prashant-i85/Secure-Cloud-Storage.git
   cd Secure-Cloud-Storage
   ```

2. **Supply AWS credentials:**
   ```powershell
   $env:AWS_ACCESS_KEY_ID="YOUR_ACCESS_KEY"
   $env:AWS_SECRET_ACCESS_KEY="YOUR_SECRET_KEY"
   $env:AWS_REGION="ap-south-1"
   ```

3. **Build & Run:**
   ```bash
   mvn clean package -DskipTests
   java -jar target/secure-cloud-storage-1.0.0.jar
   ```

4. **Access the application:**
   Navigate to `http://localhost:5000` in your web browser.

---

## 👨‍💻 Maintainer

* **Prashant Maurya** ([@prashant-i85](https://github.com/prashant-i85))

## 📜 License

Distributed under the [MIT License](LICENSE).
