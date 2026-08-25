package com.thanhnguyen.ecommercebackend.product.service;

import com.thanhnguyen.ecommercebackend.product.dto.CategoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.CategoryResponse;
import com.thanhnguyen.ecommercebackend.product.entity.Category;
import com.thanhnguyen.ecommercebackend.product.exception.CategoryNotFoundException;
import com.thanhnguyen.ecommercebackend.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Category category = new Category();
        category.setName(request.getName());
        category.setParent(resolveParent(request.getParentId()));

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));

        category.setName(request.getName());
        category.setParent(resolveParent(request.getParentId()));

        Category saved = categoryRepository.save(category);
        return toResponse(saved);
    }

    @Override
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private Category resolveParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new CategoryNotFoundException(parentId));
    }

    private CategoryResponse toResponse(Category category) {
        Long parentId = category.getParent() != null ? category.getParent().getId() : null;
        return new CategoryResponse(category.getId(), category.getName(), parentId);
    }
}
