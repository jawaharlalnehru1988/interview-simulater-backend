package com.asknehru.interviewsimulator.codingtrainer.category;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/complexity-categories")
public class ComplexityCategoryController {

    @Autowired
    private ComplexityCategoryRepository repository;

    @GetMapping
    public ResponseEntity<List<ComplexityCategory>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> addCategory(@RequestBody ComplexityCategory category) {
        if (repository.findByName(category.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("Category already exists");
        }
        return ResponseEntity.ok(repository.save(category));
    }
}
