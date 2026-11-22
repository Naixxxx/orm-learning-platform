package ru.study.platform;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogIntegrationTest extends IntegrationSupport {

    @Test
    void providesPublicCatalogWithPaginationAndSearch() throws Exception {
        request(get("/api/courses"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
        request(get("/api/courses?search=HIBER&size=1"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].teacher").value(teacher.getName()));
        request(get("/api/courses?search=missing"), null, null)
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void treatsSearchWildcardsAsLiteralUserInput() throws Exception {
        request(get("/api/courses").param("search", "%"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        create("/courses", courseInput("100% ORM"), teacher);
        request(get("/api/courses").param("search", "%"), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void createsReadsUpdatesAndDeletesCourse() throws Exception {
        long id = create("/courses", courseInput("New course"), teacher);
        request(get("/api/courses/" + id), null, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New course"));
        request(put("/api/courses/" + id), courseInput("Updated course"), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated course"));
        request(delete("/api/courses/" + id), null, teacher).andExpect(status().isNoContent());
        request(get("/api/courses/" + id), null, null).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidTeacherAndUnauthorizedCourseChanges() throws Exception {
        request(post("/api/courses"), courseInput("Forbidden"), student).andExpect(status().isForbidden());
        request(put("/api/courses/" + course.getId()), courseInput("Stolen"), otherTeacher)
                .andExpect(status().isForbidden());
        request(
                        post("/api/courses"),
                        Map.of(
                                "title",
                                "Invalid",
                                "description",
                                "Invalid teacher",
                                "categoryId",
                                category.getId(),
                                "teacherId",
                                student.getId()),
                        admin)
                .andExpect(status().isBadRequest());
    }

    @Test
    void managesCategoryAndTagCrudAndProtectsReferencedCategory() throws Exception {
        long id = create("/categories", Map.of("name", "Databases"), admin);
        request(put("/api/categories/" + id), Map.of("name", "SQL"), admin).andExpect(status().isOk());
        request(get("/api/categories/" + id), null, null)
                .andExpect(jsonPath("$.name").value("SQL"));
        request(delete("/api/categories/" + id), null, admin).andExpect(status().isNoContent());
        request(delete("/api/categories/" + category.getId()), null, admin).andExpect(status().isConflict());
        long tag = create("/tags", Map.of("name", "JPA"), admin);
        request(put("/api/tags/" + tag), Map.of("name", "Java"), admin).andExpect(status().isOk());
        request(get("/api/tags/" + tag), null, null)
                .andExpect(jsonPath("$.name").value("Java"));
        request(delete("/api/tags/" + tag), null, admin).andExpect(status().isNoContent());
    }

    @Test
    void attachesFiltersAndDetachesTagsWithoutDeletingSharedTag() throws Exception {
        long tag = create("/tags", Map.of("name", "Java"), admin);
        request(put("/api/courses/" + course.getId() + "/tags"), Map.of("tagIds", List.of(tag)), teacher)
                .andExpect(status().isOk());
        request(get("/api/courses?tagId=" + tag + "&categoryId=" + category.getId()), null, null)
                .andExpect(jsonPath("$.totalElements").value(1));
        request(get("/api/courses/" + course.getId() + "/tags"), null, teacher)
                .andExpect(jsonPath("$[0].id").value(tag));
        request(delete("/api/tags/" + tag), null, admin).andExpect(status().isConflict());
        request(put("/api/courses/" + course.getId() + "/tags"), Map.of("tagIds", List.of(999L)), teacher)
                .andExpect(status().isBadRequest());
        request(delete("/api/courses/" + course.getId()), null, teacher).andExpect(status().isNoContent());
        request(get("/api/tags/" + tag), null, null).andExpect(status().isOk());
    }

    @Test
    void managesModulesAndLessonsWithOrderedCurriculum() throws Exception {
        long moduleId =
                create("/courses/" + course.getId() + "/modules", Map.of("title", "Advanced", "position", 2), teacher);
        long lessonId = create(
                "/modules/" + moduleId + "/lessons",
                Map.of("title", "Fetching", "content", "Lazy", "position", 1),
                teacher);
        request(put("/api/modules/" + moduleId), Map.of("title", "Transactions", "position", 2), teacher)
                .andExpect(status().isOk());
        request(
                        put("/api/lessons/" + lessonId),
                        Map.of("title", "Updated lesson", "content", "Updated", "position", 1),
                        teacher)
                .andExpect(status().isOk());
        request(get("/api/courses/" + course.getId() + "/curriculum"), null, teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modules[1].module.title").value("Transactions"))
                .andExpect(jsonPath("$.modules[1].lessons[0].title").value("Updated lesson"));
        request(get("/api/modules/" + moduleId), null, teacher).andExpect(status().isOk());
        request(get("/api/modules/" + moduleId + "/lessons"), null, teacher).andExpect(status().isOk());
        request(delete("/api/modules/" + moduleId), null, teacher).andExpect(status().isNoContent());
        request(get("/api/lessons/" + lessonId), null, teacher).andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicatePositionAndAccessToUnenrolledContent() throws Exception {
        request(
                        post("/api/courses/" + course.getId() + "/modules"),
                        Map.of("title", "Duplicate", "position", 1),
                        teacher)
                .andExpect(status().isConflict());
        request(get("/api/courses/" + course.getId() + "/curriculum"), null, student)
                .andExpect(status().isForbidden());
        enroll(student);
        request(get("/api/lessons/" + lesson.getId()), null, student).andExpect(status().isOk());
        request(delete("/api/modules/" + module.getId()), null, student).andExpect(status().isForbidden());
    }

    @Test
    void managesMaterialsAndRejectsUnsafeUrls() throws Exception {
        long id = create(
                "/lessons/" + lesson.getId() + "/materials",
                Map.of("title", "Guide", "url", "https://example.org/guide"),
                teacher);
        request(get("/api/materials/" + id), null, teacher).andExpect(status().isOk());
        request(put("/api/materials/" + id), Map.of("title", "New guide", "url", "https://example.org/new"), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("New guide"));
        request(
                        post("/api/lessons/" + lesson.getId() + "/materials"),
                        Map.of("title", "Unsafe", "url", "javascript:alert(1)"),
                        teacher)
                .andExpect(status().isBadRequest());
        request(delete("/api/materials/" + id), null, teacher).andExpect(status().isNoContent());
    }

    @Test
    void managesScheduleAndValidatesChronologicalOrder() throws Exception {
        var input = Map.of(
                "startsAt",
                "2030-01-10T10:00:00Z",
                "endsAt",
                "2030-01-10T11:00:00Z",
                "meetingUrl",
                "https://example.org/meeting");
        long id = create("/lessons/" + lesson.getId() + "/schedule", input, teacher);
        request(get("/api/courses/" + course.getId() + "/schedule"), null, teacher)
                .andExpect(jsonPath("$[0].lessonTitle").value("Entities"));
        request(get("/api/schedule/" + id), null, teacher).andExpect(status().isOk());
        request(put("/api/schedule/" + id), input, teacher).andExpect(status().isOk());
        request(
                        put("/api/schedule/" + id),
                        Map.of(
                                "startsAt",
                                "2030-01-10T12:00:00Z",
                                "endsAt",
                                "2030-01-10T11:00:00Z",
                                "meetingUrl",
                                "https://example.org/meeting"),
                        teacher)
                .andExpect(status().isBadRequest());
        request(delete("/api/schedule/" + id), null, teacher).andExpect(status().isNoContent());
    }

    @Test
    void cascadesCourseDeletionToOwnedStructureOnly() throws Exception {
        create(
                "/lessons/" + lesson.getId() + "/materials",
                Map.of("title", "Notes", "url", "https://example.org"),
                teacher);
        assignment();
        request(delete("/api/courses/" + course.getId()), null, teacher).andExpect(status().isNoContent());
        assertThat(modules.count()).isZero();
        assertThat(lessons.count()).isZero();
        assertThat(assignments.count()).isZero();
        assertThat(accounts.existsById(teacher.getId())).isTrue();
        assertThat(categories.existsById(category.getId())).isTrue();
    }

    @Test
    void providesConsistentValidationAndMissingResourceErrors() throws Exception {
        request(get("/api/courses/-1"), null, null).andExpect(status().isBadRequest());
        request(get("/api/courses/99999"), null, null)
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        request(get("/api/courses?size=1000"), null, null).andExpect(status().isBadRequest());
        request(post("/api/courses"), Map.of("title", "", "description", "", "teacherId", 0, "categoryId", 0), teacher)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists());
    }

    @Test
    void servesUserInterfaceAndOpenApiDefinition() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }
}
