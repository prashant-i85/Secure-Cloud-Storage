# 🔐 Secure Cloud Storage with AES/RSA Encryption

A secure file storage application built with **Java Spring Boot** and **AWS**. This project implements "Hybrid Envelope Encryption" to ensure files are encrypted *before* they touch the cloud storage.

## 🚀 Features

* **Hybrid Encryption:** Files are encrypted locally using **AES-256-GCM**.
* **Key Protection:** The AES keys are encrypted using **RSA** via **AWS KMS** (Envelope Encryption).
* **Secure Storage:** Encrypted files are stored in **AWS S3**.
* **Metadata Management:** File details (name, type, encryption IV) are stored in **AWS DynamoDB**.
* **Authentication:** User login and session management via **AWS Cognito**.
* **Zero-Knowledge Cloud:** AWS only sees encrypted data; they cannot read the files without the KMS key.

## 🛠️ Tech Stack

* **Backend:** Java 17, Spring Boot 3.x
* **Cloud Provider:** AWS (SDK v2)
* **Database:** Amazon DynamoDB (Enhanced Client)
* **Security:** Spring Security, AWS Cognito, AWS KMS
* **Frontend:** HTML5, Bootstrap 5, Vanilla JavaScript

## 🏗️ Architecture

1.  **Upload:** User selects file -> Backend generates random AES Key -> Encrypts File -> Encrypts AES Key with KMS -> Uploads Encrypted File to S3 & Metadata to DynamoDB.
2.  **Download:** Backend fetches Metadata -> Decrypts AES Key using KMS -> Downloads Encrypted File -> Decrypts File -> Streams to User.

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