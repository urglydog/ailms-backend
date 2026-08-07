package com.lms.catalog.service;

import com.lms.catalog.dto.CategoryDto.*;
import com.lms.catalog.entity.Category;
import com.lms.catalog.repository.CategoryRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.catalog.util.SlugGenerator;
import com.lms.common.exception.ConflictException;
import com.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Quản lý danh mục môn học (UC43). Chỉ Admin tạo/sửa/xoá (BR-COURSE-05); Giảng viên
 * chỉ đọc để chọn khi tạo khóa học.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CourseRepository courseRepository;

    @Transactional(readOnly = true)
    public List<Res> getAll() {
        return categoryRepository.findAll().stream()
                .map(this::mapToRes)
                .collect(Collectors.toList());
    }

    @Transactional
    public Res create(CreateReq req) {
        Category category = new Category();
        category.setName(req.name());
        category.setSlug(generateUniqueSlug(req.name()));
        return mapToRes(categoryRepository.save(category));
    }

    @Transactional
    public Res update(Long id, UpdateReq req) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        category.setName(req.name());
        return mapToRes(categoryRepository.save(category));
    }

    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        if (courseRepository.existsByCategoryId(id)) {
            throw new ConflictException("Không thể xoá danh mục đang có khóa học sử dụng (BR-COURSE-05)");
        }
        categoryRepository.delete(category);
    }

    /** Slug sinh 1 lần lúc tạo, không đổi lại khi sửa tên (giữ URL ổn định). */
    private String generateUniqueSlug(String name) {
        String base = SlugGenerator.slugify(name);
        String candidate = base;
        int suffix = 2;
        while (categoryRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private Res mapToRes(Category category) {
        return new Res(category.getId(), category.getName(), category.getSlug());
    }
}
