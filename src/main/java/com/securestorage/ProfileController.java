package com.securestorage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public ProfileController(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    // Upload Profile Photo
    @PostMapping("/upload")
    public ResponseEntity<String> uploadProfilePhoto(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt principal) {
        try {
            String userId = principal.getSubject();
            String s3Key = "profiles/" + userId; // e.g., profiles/user-123

            // Upload to S3 (Standard Storage)
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(s3Key)
                            .contentType(file.getContentType())
                            .build(),
                    RequestBody.fromBytes(file.getBytes()));

            return ResponseEntity.ok("Profile photo updated");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error uploading photo: " + e.getMessage());
        }
    }

    // Get Profile Photo
    @GetMapping("/photo")
    public ResponseEntity<byte[]> getProfilePhoto(@AuthenticationPrincipal Jwt principal) {
        try {
            String userId = principal.getSubject();
            String s3Key = "profiles/" + userId;

            // Fetch from S3
            byte[] imageBytes = s3Client.getObject(GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build()).readAllBytes();

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG) // Default to JPEG/PNG
                    .body(imageBytes);

        } catch (NoSuchKeyException e) {
            // User hasn't uploaded a photo yet
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}