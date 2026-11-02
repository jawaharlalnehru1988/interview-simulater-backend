package com.asknehru.interviewsimulator.core;

import com.asknehru.interviewsimulator.document.Document;
import com.asknehru.interviewsimulator.document.DocumentCategory;
import com.asknehru.interviewsimulator.document.FamilyMember;
import com.asknehru.interviewsimulator.auth.User;
import com.asknehru.interviewsimulator.document.DocumentCategoryRepository;
import com.asknehru.interviewsimulator.document.DocumentRepository;
import com.asknehru.interviewsimulator.document.FamilyMemberRepository;
import com.asknehru.interviewsimulator.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentCategoryRepository categoryRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;

    private static final String UPLOAD_DIR = "uploads/documents/";

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof User) {
            return (User) auth.getPrincipal();
        }
        String name = auth.getName();
        return userRepository.findByUsername(name)
                .or(() -> userRepository.findByEmail(name))
                .orElse(null);
    }

    private boolean isAdmin(User user) {
        if (user == null) return false;
        String email = user.getEmail() != null ? user.getEmail().toLowerCase().trim() : "";
        String uname = user.getUsername() != null ? user.getUsername().toLowerCase().trim() : "";
        return email.equals("nehru00123@gmail.com")
                || email.equals("jawaharlalnehruelumalai@gmail.com")
                || uname.equals("narasimha")
                || uname.equals("nehru00123@gmail.com")
                || uname.equals("jawaharlalnehruelumalai@gmail.com")
                || user.isSuperuser()
                || user.isStaff();
    }

    @PostMapping("/upload/")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("name") String name,
                                    @RequestParam(value = "document_type", defaultValue = "OTHER") String type,
                                    @RequestParam(value = "category_id", required = false) Long categoryId,
                                    @RequestParam(value = "category_name", required = false) String categoryName,
                                    @RequestParam(value = "family_member", required = false, defaultValue = "Self") String familyMember,
                                    @RequestParam(value = "old_or_new", defaultValue = "NEW") String oldOrNew,
                                    @RequestParam(value = "description", required = false) String description) {

        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        try {
            // Ensure upload directory exists
            File uploadDir = new File(UPLOAD_DIR);
            if (!uploadDir.exists()) uploadDir.mkdirs();

            // Save file
            String originalFilename = file.getOriginalFilename();
            String extension = (originalFilename != null && originalFilename.contains("."))
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String storedFilename = UUID.randomUUID().toString() + extension;
            Path path = Paths.get(UPLOAD_DIR + storedFilename);
            Files.write(path, file.getBytes());

            String member = (familyMember != null && !familyMember.isBlank()) ? familyMember.trim() : "Self";

            Document doc = Document.builder()
                    .user(user)
                    .name(name)
                    .documentType(Document.DocumentType.valueOf(type.toUpperCase()))
                    .oldOrNew(Document.OldOrNew.valueOf(oldOrNew.toUpperCase()))
                    .file(UPLOAD_DIR + storedFilename)
                    .description(description)
                    .familyMember(member)
                    .build();

            if (categoryId != null) {
                categoryRepository.findById(categoryId).ifPresent(doc::setCategory);
            } else if (categoryName != null && !categoryName.isBlank()) {
                String trimmedCat = categoryName.trim();
                DocumentCategory cat = categoryRepository.findByName(trimmedCat)
                        .orElseGet(() -> categoryRepository.save(DocumentCategory.builder().name(trimmedCat).build()));
                doc.setCategory(cat);
            }

            // Ensure non-Self family member is recorded in family members list
            if (!member.equalsIgnoreCase("Self")) {
                String finalMember = member;
                familyMemberRepository.findByNameAndUser(finalMember, user)
                        .orElseGet(() -> familyMemberRepository.save(
                                FamilyMember.builder()
                                        .name(finalMember)
                                        .user(user)
                                        .build()
                        ));
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
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        List<Document> docs;
        if (isAdmin(user)) {
            // Admins can see all documents across the platform
            docs = documentRepository.findAllByOrderByCreatedAtDesc();
        } else {
            // Multi-tenant: standard users can only see their own documents
            docs = documentRepository.findByUserOrderByCreatedAtDesc(user);
        }
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/categories/")
    public ResponseEntity<?> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @PostMapping("/categories/")
    public ResponseEntity<?> createCategory(@RequestBody Map<String, String> body) {
        String name = body != null ? body.get("name") : null;
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Category name is required");
        }
        String trimmed = name.trim();
        DocumentCategory cat = categoryRepository.findByName(trimmed)
                .orElseGet(() -> categoryRepository.save(DocumentCategory.builder().name(trimmed).build()));
        return ResponseEntity.ok(cat);
    }

    @GetMapping("/family-members/")
    public ResponseEntity<?> getFamilyMembers() {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        List<FamilyMember> members;
        if (isAdmin(user)) {
            members = familyMemberRepository.findAllByOrderByNameAsc();
        } else {
            members = familyMemberRepository.findByUserOrderByNameAsc(user);
        }
        return ResponseEntity.ok(members);
    }

    @PostMapping("/family-members/")
    public ResponseEntity<?> createFamilyMember(@RequestBody Map<String, String> body) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        String name = body != null ? body.get("name") : null;
        String relation = body != null ? body.get("relationship") : null;
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Member name is required");
        }
        String trimmed = name.trim();
        FamilyMember member = familyMemberRepository.findByNameAndUser(trimmed, user)
                .orElseGet(() -> familyMemberRepository.save(
                        FamilyMember.builder()
                                .name(trimmed)
                                .relationship(relation != null ? relation.trim() : null)
                                .user(user)
                                .build()
                ));
        return ResponseEntity.ok(member);
    }

    @PatchMapping("/{id}/")
    public ResponseEntity<?> updateDocument(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Document doc = documentRepository.findById(id).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }

        if (!isAdmin(user) && (doc.getUser() == null || !doc.getUser().getId().equals(user.getId()))) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        if (updates.containsKey("name") && updates.get("name") != null) {
            doc.setName(updates.get("name").toString().trim());
        }
        if (updates.containsKey("description")) {
            doc.setDescription(updates.get("description") != null ? updates.get("description").toString() : null);
        }
        if (updates.containsKey("familyMember")) {
            String fm = updates.get("familyMember") != null ? updates.get("familyMember").toString().trim() : "Self";
            doc.setFamilyMember(fm.isEmpty() ? "Self" : fm);
        }
        if (updates.containsKey("categoryId")) {
            Object catVal = updates.get("categoryId");
            if (catVal != null) {
                Long catId = Long.valueOf(catVal.toString());
                categoryRepository.findById(catId).ifPresent(doc::setCategory);
            } else {
                doc.setCategory(null);
            }
        }

        return ResponseEntity.ok(documentRepository.save(doc));
    }

    @DeleteMapping("/{id}/")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }

        Document doc = documentRepository.findById(id).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }

        // Admins can delete any document; standard users can only delete their own
        if (!isAdmin(user) && (doc.getUser() == null || !doc.getUser().getId().equals(user.getId()))) {
            return ResponseEntity.status(403).body("Forbidden");
        }

        // Delete physical file if exists
        try {
            Files.deleteIfExists(Paths.get(doc.getFile()));
        } catch (IOException ignored) {}

        documentRepository.delete(doc);
        return ResponseEntity.noContent().build();
    }
}
