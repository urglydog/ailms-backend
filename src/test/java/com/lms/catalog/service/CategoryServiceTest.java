package com.lms.catalog.service;

import com.lms.catalog.dto.CategoryDto.*;
import com.lms.catalog.entity.Category;
import com.lms.catalog.repository.CategoryRepository;
import com.lms.catalog.repository.CourseRepository;
import com.lms.common.exception.ConflictException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm tra BR-COURSE-05: không xoá được danh mục còn khóa học tham chiếu. */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private CourseRepository courseRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void delete_throwsConflict_whenCategoryStillReferencedByCourse() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Tiếng Anh");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(courseRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(1L))
                .isInstanceOf(ConflictException.class);

        verify(categoryRepository, never()).delete(any(Category.class));
    }

    @Test
    void delete_succeeds_whenNoCourseReferencesCategory() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Tiếng Anh");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(courseRepository.existsByCategoryId(1L)).thenReturn(false);

        categoryService.delete(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void create_appendsSuffixWhenSlugAlreadyExists() {
        when(categoryRepository.existsBySlug("tieng-anh")).thenReturn(true);
        when(categoryRepository.existsBySlug("tieng-anh-2")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Res result = categoryService.create(new CreateReq("Tiếng Anh"));

        assertThat(result.slug()).isEqualTo("tieng-anh-2");
    }
}
