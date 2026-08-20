package com.lms.community.service;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.catalog.entity.Lesson;
import com.lms.catalog.repository.LessonRepository;
import com.lms.community.dto.ChatMessageDto;
import com.lms.community.entity.LessonChat;
import com.lms.community.repository.LessonChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LessonChatService {

    private final LessonChatRepository chatRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getChatHistory(Long lessonId) {
        return chatRepository.findByLessonIdOrderByCreatedAtAsc(lessonId).stream()
                .map(chat -> new ChatMessageDto(
                        chat.getUser().getId().toString(),
                        chat.getUserName(),
                        chat.getContent(),
                        chat.getCreatedAt().toString(),
                        chat.getParent() != null ? chat.getParent().getId() : null
                ))
                .toList();
    }

    @Transactional
    public void saveMessage(Long lessonId, Long userId, String userName, String content, String parentId) {
        Lesson lesson = lessonRepository.getReferenceById(lessonId);
        User user = userRepository.getReferenceById(userId);

        LessonChat chat = new LessonChat();
        chat.setLesson(lesson);
        chat.setUser(user);
        chat.setUserName(userName);
        chat.setContent(content);

        if (parentId != null && !parentId.isBlank()) {
            LessonChat parent = chatRepository.findById(parentId).orElse(null);
            chat.setParent(parent);
        }

        chatRepository.save(chat);
    }
}
