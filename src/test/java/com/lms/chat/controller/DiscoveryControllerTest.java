package com.lms.chat.controller;

import com.lms.auth.entity.User;
import com.lms.auth.repository.UserRepository;
import com.lms.auth.security.AccountRestrictionService;
import com.lms.chat.service.DiscoveryQuotaService;
import com.lms.common.config.AiWorkerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Discovery Chat: tài khoản INSTRUCTOR (kể cả học viên vừa được duyệt lên giảng viên) phải dùng được
 * như Student; Admin vẫn bị chặn; Guest dùng quota theo IP.
 */
@ExtendWith(MockitoExtension.class)
class DiscoveryControllerTest {

    @Mock private RestTemplate restTemplate;
    @Mock private AiWorkerConfig aiWorkerConfig;
    @Mock private UserRepository userRepository;
    @Mock private AccountRestrictionService accountRestrictionService;
    @Mock private DiscoveryQuotaService discoveryQuotaService;
    @InjectMocks private DiscoveryController controller;

    private static UsernamePasswordAuthenticationToken auth(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority(role)));
    }

    private static Map<String, Object> body(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", message);
        return m;
    }

    @Test
    @SuppressWarnings("unchecked")
    void instructor_isAllowed_asStudentWithStudentQuota() {
        User user = org.mockito.Mockito.mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(userRepository.findByEmail("gv@lms.local")).thenReturn(Optional.of(user));
        when(aiWorkerConfig.getBaseUrl()).thenReturn("http://ai:8000");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok((Object) Map.of("reply", "ok")));

        ResponseEntity<?> res = controller.chat(auth("gv@lms.local", "ROLE_INSTRUCTOR"), body("khoá học tiếng anh"),
                new MockHttpServletRequest());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(accountRestrictionService).requireAiAllowed(user);
        verify(discoveryQuotaService).consumeStudent(7L);
        verify(discoveryQuotaService, never()).consumeGuest(anyString());

        ArgumentCaptor<HttpEntity<Map<String, Object>>> sent = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://ai:8000/api/v1/discovery/chat"), sent.capture(), eq(Object.class));
        assertThat(sent.getValue().getBody()).containsEntry("caller_role", "STUDENT");
        assertThat(sent.getValue().getBody()).containsEntry("message", "khoá học tiếng anh");
    }

    @Test
    void admin_isStillForbidden() {
        ResponseEntity<?> res = controller.chat(auth("ad@lms.local", "ROLE_ADMIN"), body("x"), new MockHttpServletRequest());

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(discoveryQuotaService, restTemplate, accountRestrictionService);
    }

    @Test
    void guest_usesIpQuota_andGuestRole() {
        when(aiWorkerConfig.getBaseUrl()).thenReturn("http://ai:8000");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(ResponseEntity.ok((Object) Map.of("reply", "ok")));
        MockHttpServletRequest req = new MockHttpServletRequest();
        // Proxy nối IP thật vào CUỐI; phần đầu do client tự gửi nên không được tin.
        req.addHeader("X-Forwarded-For", "1.2.3.4, 9.9.9.9");

        ResponseEntity<?> res = controller.chat(null, body("hi"), req);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(discoveryQuotaService).consumeGuest("9.9.9.9");
        verify(discoveryQuotaService, never()).consumeStudent(any());
        verifyNoInteractions(userRepository, accountRestrictionService);
    }
}
