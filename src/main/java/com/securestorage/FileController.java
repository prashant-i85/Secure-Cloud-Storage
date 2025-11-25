package com.securestorage;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt principal) throws Exception {
        fileService.uploadFile(file, principal.getSubject());
        return "Upload successful!";
    }

    @GetMapping
    public List<FileEntity> list() {
        return fileService.listFiles();
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) throws Exception {
        // 1. Get the decrypted data
        byte[] data = fileService.downloadFile(id);

        // 2. Get the file details (so we know the name)
        FileEntity entity = fileService.getFileMetadata(id);

        // 3. Return the file with the correct name
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + entity.getFilename() + "\"")
                .body(data);
    }
}