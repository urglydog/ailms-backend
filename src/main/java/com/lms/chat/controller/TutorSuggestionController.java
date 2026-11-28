package com.lms.chat.controller;

import com.lms.chat.dto.TutorSuggestionDto.SuggestionRes;
import com.lms.chat.service.TutorSuggestionService;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Thẻ câu hỏi gợi ý ở trạng thái rỗng của Gia sư AI (26/11/2026). */
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class TutorSuggestionController {

    private final TutorSuggestionService suggestionService;

    @GetMapping("/api/v1/lessons/{lessonId}/tutor-suggestions")
    public ResponseEntity<List<SuggestionRes>> suggestions(Principal principal, @PathVariable Long lessonId) {
        return ResponseEntity.ok(suggestionService.suggestions(principal.getName(), lessonId));
    }
}
