package com.asknehru.interviewsimulator.service;

import com.asknehru.interviewsimulator.model.Document;
import com.asknehru.interviewsimulator.model.User;
import com.asknehru.interviewsimulator.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final String uploadDir = "media/documents/";

    public Document uploadDocument(User user, String name, Document.DocumentType type, MultipartFile file) throws IOException {
        Path path = Paths.get(uploadDir);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }

        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = path.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);

        Document document = Document.builder()
                .user(user)
                .name(name)
                .documentType(type)
                .file(filePath.toString())
                .build();

        return documentRepository.save(document);
    }

    public List<Document> getUserDocuments(User user) {
        return documentRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public void deleteDocument(Long id) throws IOException {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Document not found"));

        Path filePath = Paths.get(document.getFile());
        Files.deleteIfExists(filePath);
        documentRepository.delete(document);
    }
}
