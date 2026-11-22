package ru.study.platform;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.Test;
import ru.study.platform.domain.Role;

class AuthIntegrationTest extends IntegrationSupport {

    @Test
    void registersStudentWithProfileAndHashedPassword() throws Exception {
        long id = create(
                "/auth/register",
                Map.of("name", "Alice", "email", "ALICE@Test.local", "password", "safe-password-2026"),
                null);
        var saved = accounts.findById(id).orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.STUDENT);
        assertThat(saved.getEmail()).isEqualTo("alice@test.local");
        assertThat(passwords.matches("safe-password-2026", saved.getPasswordHash()))
                .isTrue();
        assertThat(profiles.findByAccountId(id)).isPresent();
    }

    @Test
    void issuesRealJwtAndAcceptsItForProtectedEndpoint() throws Exception {
        var result = tree(request(
                        post("/api/auth/login"),
                        Map.of("email", student.getEmail(), "password", "test-password-2026"),
                        null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist()));
        mvc.perform(get("/api/auth/me")
                        .header(
                                "Authorization",
                                "Bearer " + result.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(student.getId()));
    }

    @Test
    void rejectsIncorrectCredentialsWithoutDisclosingWhichFieldWasWrong() throws Exception {
        request(post("/api/auth/login"), Map.of("email", student.getEmail(), "password", "incorrect"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверная почта или пароль"));
        request(post("/api/auth/login"), Map.of("email", "missing@test.local", "password", "incorrect"), null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Неверная почта или пароль"));
    }

    @Test
    void rejectsTamperedTokenAndMissingAuthentication() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid.token.signature"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() throws Exception {
        request(
                        post("/api/auth/register"),
                        Map.of(
                                "name",
                                "Copy",
                                "email",
                                student.getEmail().toUpperCase(java.util.Locale.ROOT),
                                "password",
                                "new-password-2026"),
                        null)
                .andExpect(status().isConflict());
    }

    @Test
    void validatesRegistrationAndRejectsRoleEscalation() throws Exception {
        request(post("/api/auth/register"), Map.of("name", "", "email", "bad", "password", "123"), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists())
                .andExpect(jsonPath("$.fields.email").exists())
                .andExpect(jsonPath("$.fields.password").exists());
        request(
                        post("/api/auth/register"),
                        Map.of(
                                "name",
                                "Hacker",
                                "email",
                                "hacker@test.local",
                                "password",
                                "new-password-2026",
                                "role",
                                "ADMIN"),
                        null)
                .andExpect(status().isBadRequest());
    }

    @Test
    void restrictsAccountCreationAndPrivateDataToAuthorizedUsers() throws Exception {
        var input = Map.of(
                "name",
                "New teacher",
                "email",
                "newteacher@test.local",
                "password",
                "new-password-2026",
                "role",
                "TEACHER");
        request(post("/api/users"), input, student).andExpect(status().isForbidden());
        long id = create("/users", input, admin);
        request(get("/api/users/" + id), null, student).andExpect(status().isForbidden());
        request(get("/api/users/" + id), null, admin).andExpect(status().isOk());
        request(get("/api/users"), null, admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(6));
    }

    @Test
    void updatesAccountAndProfileWithoutExposingPassword() throws Exception {
        request(put("/api/users/" + student.getId()), Map.of("name", "Updated", "email", "updated@test.local"), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        request(
                        put("/api/users/" + student.getId() + "/profile"),
                        Map.of("biography", "Learning ORM", "avatarUrl", "https://example.org/avatar.png"),
                        student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biography").value("Learning ORM"));
        request(get("/api/users/" + student.getId() + "/profile"), null, otherStudent)
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesUtf8PasswordByteLimitWithoutCrashingBcrypt() throws Exception {
        String tooLongInBytes = "я".repeat(40);
        request(
                        post("/api/auth/register"),
                        Map.of("name", "Unicode", "email", "unicode@test.local", "password", tooLongInBytes),
                        null)
                .andExpect(status().isBadRequest());
        request(post("/api/auth/login"), Map.of("email", student.getEmail(), "password", tooLongInBytes), null)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedJsonAndInvalidResourceParameters() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        request(get("/api/users?size=0"), null, admin).andExpect(status().isBadRequest());
    }

    @Test
    void deletesUnusedAccountAndItsProfileButProtectsCurrentAdmin() throws Exception {
        request(delete("/api/users/" + otherStudent.getId()), null, admin).andExpect(status().isNoContent());
        assertThat(accounts.existsById(otherStudent.getId())).isFalse();
        assertThat(profiles.findByAccountId(otherStudent.getId())).isEmpty();
        request(delete("/api/users/" + admin.getId()), null, admin).andExpect(status().isConflict());
    }
}
