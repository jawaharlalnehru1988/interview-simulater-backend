package com.asknehru.interviewsimulator.controller;

import com.asknehru.interviewsimulator.model.Document;
import com.asknehru.interviewsimulator.model.DocumentCategory;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.DocumentCategoryRepository;
import com.asknehru.interviewsimulator.repository.DocumentRepository;
import com.asknehru.interviewsimulator.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final UserRepository userRepository;

    private static final String UPLOAD_DIR = "uploads/documents/";

    @PostMapping("/upload/")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("name") String name,
                                    @RequestParam(value = "document_type", defaultValue = "OTHER") String type,
                                    @RequestParam(value = "category_id", required = false) Long categoryId,
                                    @RequestParam(value = "old_or_new", defaultValue = "NEW") String oldOrNew,
                                    @RequestParam(value = "description", required = false) String description) {
        
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        try {
            // Ensure upload directory exists
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) uploadDir.mkdirs();

            // Save file
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            String storedFilename = UUID.randomUUID().toString() + extension;
            Path path = Paths.get(UPLOAD_DIR + storedFilename);
            Files.write(path, file.getBytes());

            Document doc = Document.builder()
                    .user(user)
                    .name(name)
                    .documentType(Document.DocumentType.valueOf(type.toUpperCase()))
                    .oldOrNew(Document.OldOrNew.valueOf(oldOrNew.toUpperCase()))
                    .file(UPLOAD_DIR + storedFilename)
                    .description(description)
                    .build();

            if (categoryId != null) {
                categoryRepository.findById(categoryId).ifPresent(doc::setCategory);
            }

            Document saved = documentRepository.save(doc);
            return ResponseEntity.ok(saved);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Failed to store file: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid enum value: " + e.getMessage());
        }
    }

    @GetMapping("/")
    public ResponseEntity<?> list() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        List<Document> docs = documentRepository.findByUserOrderByCreatedAtDesc(user);
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/categories/")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @DeleteMapping("/{id}/")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        
        Document doc = documentRepository.findById(id).orElseThrow();
        if (!doc.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).build();
        }

        // Delete file
        try {
            Files.deleteIfExists(Paths.get(doc.getFile()));
        } catch (IOException ignored) {}

        documentRepository.delete(doc);
        return ResponseEntity.noContent().build();
    }
}
