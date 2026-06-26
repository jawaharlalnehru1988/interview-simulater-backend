package com.asknehru.interviewsimulator.test.category;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/manipulation-categories")
@RequiredArgsConstructor
public class ManipulationCategoryController {

    private final ManipulationCategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<List<ManipulationCategory>> getCategories() {
        return ResponseEntity.ok(categoryRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<ManipulationCategory> addCategory(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        ManipulationCategory category = ManipulationCategory.builder()
                .name(name.trim())
                .build();
                
        try {
            category = categoryRepository.save(category);
            return ResponseEntity.ok(category);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
