package com.asknehru.interviewsimulator.codingtrainer.category;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CategoryDataSeeder implements CommandLineRunner {

    private final ManipulationCategoryRepository categoryRepository;
    private final ComplexityCategoryRepository complexityCategoryRepository;

    @Override
    public void run(String... args) {
        if (categoryRepository.count() == 0) {
            List<String> defaultCategories = Arrays.asList(
                    "String Manipulation",
                    "Array Manipulation",
                    "Object Manipulation",
                    "HashMap",
                    "HashSet",
                    "LinkedList",
                    "Queue",
                    "Stack",
                    "Tree",
                    "Graph",
                    "Dynamic Programming",
                    "Sorting"
            );

            defaultCategories.forEach(name -> {
                categoryRepository.save(ManipulationCategory.builder().name(name).build());
            });
        }

        if (complexityCategoryRepository.count() == 0) {
            List<String> defaultComplexityCategories = Arrays.asList(
                    "Recursion",
                    "Sorting",
                    "Nested Loops",
                    "Tree Traversal",
                    "Graph Traversal",
                    "Dynamic Programming",
                    "General"
            );

            defaultComplexityCategories.forEach(name -> {
                complexityCategoryRepository.save(ComplexityCategory.builder().name(name).build());
            });
        }
    }
}
