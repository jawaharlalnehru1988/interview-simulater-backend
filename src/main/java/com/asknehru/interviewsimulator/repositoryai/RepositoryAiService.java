package com.asknehru.interviewsimulator.repositoryai;

import com.asknehru.interviewsimulator.ai.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class RepositoryAiService {

    private final RepositoryAnalysisRepository analysisRepository;
    private final RepositoryChatRepository chatRepository;
    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final VectorStore vectorStore;

    @Async
    public void analyzeRepository(Long analysisId, String githubUrl) {
        RepositoryAnalysis analysis = analysisRepository.findById(analysisId).orElseThrow();
        analysis.setStatus("ANALYZING");
        analysisRepository.save(analysis);

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("repo_");
            cloneRepository(githubUrl, tempDir.toFile());

            List<String> fileTree = generateFileTree(tempDir);
            String fileTreeJson = objectMapper.writeValueAsString(fileTree);
            analysis.setFileTreeData(fileTreeJson);

            List<Document> documents = new ArrayList<>();
            for (String relativePath : fileTree) {
                try {
                    String content = Files.readString(tempDir.resolve(relativePath));
                    if (content.isBlank()) continue;
                    
                    Document doc = new Document(content);
                    doc.getMetadata().put("repositoryAnalysisId", analysisId.toString());
                    doc.getMetadata().put("filePath", relativePath);
                    documents.add(doc);
                } catch (Exception e) {
                    log.warn("Could not read file: {}", relativePath);
                }
            }

            if (!documents.isEmpty()) {
                TokenTextSplitter splitter = new TokenTextSplitter();
                List<Document> chunkedDocs = splitter.apply(documents);
                vectorStore.add(chunkedDocs);
            }

            String coreFilesContent = readCoreFiles(tempDir);

            String prompt = "You are an expert software architect. Analyze the following repository structure and core files, and provide a high-level summary including:\n" +
                    "- Technology stack\n" +
                    "- Folder structure\n" +
                    "- Major modules\n" +
                    "- API layers (if apparent)\n" +
                    "- Database layers (if apparent)\n" +
                    "- Design patterns identified (if apparent)\n\n" +
                    "File Tree:\n" + String.join("\n", fileTree) + "\n\n" +
                    "Core Files Content:\n" + coreFilesContent + "\n\n" +
                    "Please format the response in Markdown.";

            String summary = llmService.generate(prompt);
            analysis.setSummaryData(summary);
            analysis.setStatus("COMPLETED");

        } catch (Exception e) {
            log.error("Failed to analyze repository {}", githubUrl, e);
            analysis.setStatus("FAILED");
            analysis.setSummaryData("Analysis failed: " + e.getMessage());
        } finally {
            analysisRepository.save(analysis);
            if (tempDir != null) {
                try {
                    FileUtils.deleteDirectory(tempDir.toFile());
                } catch (IOException e) {
                    log.warn("Failed to delete temp dir {}", tempDir, e);
                }
            }
        }
    }

    public String handleChat(Long analysisId, String userMessage) {
        RepositoryAnalysis analysis = analysisRepository.findById(analysisId).orElseThrow();

        // Save user message
        RepositoryChat userChat = new RepositoryChat();
        userChat.setRepositoryAnalysisId(analysisId);
        userChat.setRole("USER");
        userChat.setContent(userMessage);
        chatRepository.save(userChat);

        // Search vector DB for relevant context
        SearchRequest searchRequest = SearchRequest.query(userMessage)
                .withTopK(5)
                .withFilterExpression("repositoryAnalysisId == '" + analysisId + "'");
        
        List<Document> similarDocs = new ArrayList<>();
        try {
            similarDocs = vectorStore.similaritySearch(searchRequest);
        } catch (Exception e) {
            log.warn("Vector search failed, falling back to general context: {}", e.getMessage());
        }
        
        StringBuilder filesContent = new StringBuilder();
        for (Document doc : similarDocs) {
            filesContent.append("--- ").append(doc.getMetadata().get("filePath")).append(" ---\n");
            filesContent.append(doc.getContent()).append("\n\n");
        }

        // Answer question
        List<RepositoryChat> history = chatRepository.findByRepositoryAnalysisIdOrderByCreatedAtAsc(analysisId);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", "You are an AI assistant helping a developer understand a code repository. You are provided with relevant code snippets from the repository. Use them to answer the user's question accurately."));

        for (RepositoryChat chat : history) {
            if (!chat.getId().equals(userChat.getId())) {
                String apiRole = chat.getRole().equals("AI") ? "assistant" : "user";
                messages.add(Map.of("role", apiRole, "content", chat.getContent()));
            }
        }

        String userPrompt = "User Question: " + userMessage;
        if (filesContent.length() > 0) {
            userPrompt += "\n\nRelevant Code Snippets:\n" + filesContent.toString();
        } else {
            userPrompt += "\n\n(No specific file contents could be fetched. Answer based on the general context or the file tree: " + analysis.getFileTreeData() + ")";
        }

        messages.add(Map.of("role", "user", "content", userPrompt));

        String aiResponse = llmService.generateWithMessages(messages);

        // Save AI message
        RepositoryChat aiChat = new RepositoryChat();
        aiChat.setRepositoryAnalysisId(analysisId);
        aiChat.setRole("AI");
        aiChat.setContent(aiResponse);
        chatRepository.save(aiChat);

        return aiResponse;
    }

    private void cloneRepository(String githubUrl, File destDir) throws IOException, InterruptedException {
        // Clean URL if user pasted a path to a folder/branch (e.g., https://github.com/user/repo/tree/master/backend)
        String repoUrl = githubUrl;
        if (githubUrl != null && githubUrl.contains("github.com")) {
            String[] parts = githubUrl.split("/");
            if (parts.length >= 5) {
                repoUrl = parts[0] + "//" + parts[2] + "/" + parts[3] + "/" + parts[4];
                // Remove trailing .git if present just to normalize, though git clone handles it
                if (repoUrl.endsWith(".git")) {
                    repoUrl = repoUrl.substring(0, repoUrl.length() - 4);
                }
            }
        }
        
        ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", repoUrl, ".");
        pb.directory(destDir);
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Git clone failed with exit code " + exitCode);
        }
    }

    private List<String> generateFileTree(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(this::isValidFile)
                    .map(p -> dir.relativize(p).toString())
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }

    private boolean isValidFile(Path p) {
        String path = p.toString().toLowerCase();
        if (Files.isDirectory(p)) return false;
        if (path.contains(".git") || path.contains("node_modules") || path.contains("venv") || 
            path.contains("build/") || path.contains("target/") || path.contains(".idea")) {
            return false;
        }
        if (path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") || 
            path.endsWith(".gif") || path.endsWith(".ico") || path.endsWith(".svg") ||
            path.endsWith(".jar") || path.endsWith(".zip") || path.endsWith(".tar") || 
            path.endsWith(".gz") || path.endsWith(".class") || path.endsWith(".pdf") ||
            path.endsWith("package-lock.json") || path.endsWith("yarn.lock")) {
            return false;
        }
        return true;
    }

    private String readCoreFiles(Path dir) throws IOException {
        String[] coreFiles = {"README.md", "pom.xml", "package.json", "build.gradle", "docker-compose.yml", "Dockerfile"};
        StringBuilder sb = new StringBuilder();
        for (String file : coreFiles) {
            Path path = dir.resolve(file);
            if (Files.exists(path)) {
                sb.append("--- ").append(file).append(" ---\n");
                sb.append(Files.readString(path)).append("\n\n");
            }
        }
        return sb.toString();
    }
}
