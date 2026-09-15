package com.lms;

import com.lms.auth.service.AuthService;
import com.lms.material.controller.InstructorMaterialController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import java.security.Principal;

@SpringBootTest
public class ManualTest {
    @Autowired
    InstructorMaterialController controller;

    @Test
    public void test() {
        Principal p = new UsernamePasswordAuthenticationToken("student1@lms.local", null);
        ResponseEntity<?> res = controller.getMaterialsForCourse(p, 1L);
        System.out.println("TEST_OUTPUT: " + res.getBody());
    }
}
