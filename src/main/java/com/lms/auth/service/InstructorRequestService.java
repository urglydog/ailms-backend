package com.lms.auth.service;

import com.lms.auth.dto.InstructorRequestDto.*;
import com.lms.auth.entity.InstructorRequest;
import com.lms.auth.entity.User;
import com.lms.auth.repository.InstructorRequestRepository;
import com.lms.auth.repository.UserRepository;
import com.lms.common.enums.RequestStatus;
import com.lms.common.enums.Role;
import com.lms.common.exception.BusinessRuleViolationException;
import com.lms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InstructorRequestService {
    private final InstructorRequestRepository requestRepository;
    private final UserRepository userRepository;

    @Transactional
    public Res createRequest(String email, CreateReq req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));

        if (user.getRole() == Role.INSTRUCTOR) {
            throw new BusinessRuleViolationException("Bạn đã là Giảng viên rồi.");
        }

        if (requestRepository.existsByUserIdAndStatus(user.getId(), RequestStatus.PENDING)) {
            throw new BusinessRuleViolationException("Bạn đang có một yêu cầu chờ duyệt.");
        }

        InstructorRequest newReq = new InstructorRequest();
        newReq.setUser(user);
        newReq.setMotivation(req.motivation());
        newReq.setCredentialUrl(req.credentialUrl());
        newReq.setStatus(RequestStatus.PENDING);

        return mapToRes(requestRepository.save(newReq));
    }

    @Transactional(readOnly = true)
    public List<Res> getRequestsByStatus(RequestStatus status) {
        return requestRepository.findByStatus(status).stream()
                .map(this::mapToRes)
                .collect(Collectors.toList());
    }

    @Transactional
    public Res updateStatus(Long id, UpdateStatusReq req) {
        InstructorRequest request = requestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("InstructorRequest", id));

        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessRuleViolationException("Chỉ có thể duyệt yêu cầu đang ở trạng thái PENDING");
        }

        request.setStatus(req.status());
        if (req.status() == RequestStatus.REJECTED) {
            request.setRejectReason(req.adminNotes());
        } else if (req.status() == RequestStatus.APPROVED) {
            User user = request.getUser();
            user.setRole(Role.INSTRUCTOR);
            userRepository.save(user);
        }

        return mapToRes(requestRepository.save(request));
    }

    private Res mapToRes(InstructorRequest request) {
        return new Res(
                request.getId(),
                request.getUser().getId(),
                request.getMotivation(),
                request.getCredentialUrl(),
                request.getStatus(),
                request.getRejectReason(),
                request.getCreatedAt()
        );
    }
}
