package com.example.roombooking;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With ADMIN_PASSWORD set, the publicly known seed password must stop working. */
@SpringBootTest(properties = "app.admin.password=" + AdminPasswordFromEnvironmentTest.PASSWORD)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminPasswordFromEnvironmentTest {

    static final String PASSWORD = "a-much-better-secret";

    @Autowired MockMvc mvc;

    @Test
    void configuredPasswordReplacesSeedPassword() throws Exception {
        login("admin123").andExpect(status().isUnauthorized());
        login(PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"%s\"}".formatted(password)));
    }
}
