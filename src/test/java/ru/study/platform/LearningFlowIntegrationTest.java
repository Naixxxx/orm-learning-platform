package ru.study.platform;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class LearningFlowIntegrationTest extends IntegrationSupport {

    @Test
    void enrollsReadsUpdatesAndWithdrawsStudent() throws Exception {
        enroll(student);
        String path = "/api/courses/" + course.getId() + "/enrollments/" + student.getId();
        request(get(path), null, student).andExpect(status().isOk());
        request(get("/api/users/" + student.getId() + "/enrollments"), null, student)
                .andExpect(jsonPath("$[0].courseId").value(course.getId()));
        request(get("/api/courses/" + course.getId() + "/enrollments"), null, teacher)
                .andExpect(jsonPath("$[0].studentId").value(student.getId()));
        request(put(path), Map.of("status", "COMPLETED"), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        request(delete(path), null, student).andExpect(status().isNoContent());
        request(get(path), null, student).andExpect(status().isNotFound());
    }

    @Test
    void rejectsDuplicateAndUnauthorizedEnrollmentAndNonStudent() throws Exception {
        enroll(student);
        String path = "/api/courses/" + course.getId() + "/enrollments";
        request(post(path), Map.of("studentId", student.getId()), student).andExpect(status().isConflict());
        request(post(path), Map.of("studentId", otherStudent.getId()), student).andExpect(status().isForbidden());
        request(post(path), Map.of("studentId", teacher.getId()), admin).andExpect(status().isBadRequest());
        request(get(path), null, student).andExpect(status().isForbidden());
        request(get("/api/users/" + student.getId() + "/enrollments"), null, otherStudent)
                .andExpect(status().isForbidden());
    }

    @Test
    void preventsConcurrentDuplicateEnrollmentAtDatabaseLevel() throws Exception {
        Callable<Integer> enroll = () -> request(
                        post("/api/courses/" + course.getId() + "/enrollments"),
                        Map.of("studentId", student.getId()),
                        student)
                .andReturn()
                .getResponse()
                .getStatus();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(enroll);
            var second = executor.submit(enroll);
            assertThat(java.util.List.of(first.get(), second.get())).containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test
    void managesAssignmentCrudBeforeAnySubmission() throws Exception {
        var input = Map.of("title", "Model a course", "description", "Use JPA", "maxScore", 100);
        long id = create("/lessons/" + lesson.getId() + "/assignments", input, teacher);
        request(get("/api/assignments/" + id), null, teacher).andExpect(status().isOk());
        request(
                        put("/api/assignments/" + id),
                        Map.of("title", "Updated", "description", "Use Hibernate", "maxScore", 50),
                        teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxScore").value(50));
        request(get("/api/lessons/" + lesson.getId() + "/assignments"), null, teacher)
                .andExpect(jsonPath("$.length()").value(1));
        request(delete("/api/assignments/" + id), null, teacher).andExpect(status().isNoContent());
    }

    @Test
    void submitsReadsAndGradesAssignmentAndPreservesWorkAfterWithdrawal() throws Exception {
        enroll(student);
        var assignment = assignment();
        long id = create(
                "/assignments/" + assignment.getId() + "/submissions", Map.of("content", "My ORM solution"), student);
        request(get("/api/submissions/" + id), null, student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").isEmpty());
        request(get("/api/assignments/" + assignment.getId() + "/submissions"), null, teacher)
                .andExpect(jsonPath("$[0].content").value("My ORM solution"));
        request(put("/api/submissions/" + id + "/grade"), Map.of("score", 95, "feedback", "Good mapping"), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(95));
        request(delete("/api/courses/" + course.getId() + "/enrollments/" + student.getId()), null, student)
                .andExpect(status().isNoContent());
        request(get("/api/users/" + student.getId() + "/submissions"), null, student)
                .andExpect(jsonPath("$[0].feedback").value("Good mapping"));
        request(get("/api/submissions/" + id), null, student).andExpect(status().isOk());
    }

    @Test
    void rejectsUnenrolledDuplicateAndLateSubmissions() throws Exception {
        var assignment = assignment();
        String path = "/api/assignments/" + assignment.getId() + "/submissions";
        request(post(path), Map.of("content", "Solution"), student).andExpect(status().isForbidden());
        enroll(student);
        create("/assignments/" + assignment.getId() + "/submissions", Map.of("content", "First"), student);
        request(post(path), Map.of("content", "Duplicate"), student).andExpect(status().isConflict());
        var late = assignment();
        late.setDueAt(Instant.parse("2000-01-01T00:00:00Z"));
        assignments.saveAndFlush(late);
        request(post("/api/assignments/" + late.getId() + "/submissions"), Map.of("content", "Late"), student)
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidGradesAndAccessToOtherStudentsWork() throws Exception {
        enroll(student);
        long id = create(
                "/assignments/" + assignment().getId() + "/submissions", Map.of("content", "My solution"), student);
        request(get("/api/submissions/" + id), null, otherStudent).andExpect(status().isForbidden());
        request(put("/api/submissions/" + id + "/grade"), Map.of("score", 50), student)
                .andExpect(status().isForbidden());
        request(put("/api/submissions/" + id + "/grade"), Map.of("score", 50), otherTeacher)
                .andExpect(status().isForbidden());
        request(put("/api/submissions/" + id + "/grade"), Map.of("score", 101), teacher)
                .andExpect(status().isBadRequest());
        request(put("/api/submissions/" + id + "/grade"), Map.of("score", -1), teacher)
                .andExpect(status().isBadRequest());
        request(put("/api/submissions/" + id + "/grade"), Map.of("score", 0), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0));
    }

    @Test
    void protectsSubmissionHistoryAgainstParentDeletionAndAssignmentChanges() throws Exception {
        enroll(student);
        var assignment = assignment();
        long id = create("/assignments/" + assignment.getId() + "/submissions", Map.of("content", "Work"), student);
        request(
                        put("/api/assignments/" + assignment.getId()),
                        Map.of("title", "Changed", "description", "Changed", "maxScore", 10),
                        teacher)
                .andExpect(status().isConflict());
        request(delete("/api/assignments/" + assignment.getId()), null, teacher).andExpect(status().isConflict());
        request(delete("/api/lessons/" + lesson.getId()), null, teacher).andExpect(status().isConflict());
        request(delete("/api/courses/" + course.getId()), null, teacher).andExpect(status().isConflict());
        request(get("/api/submissions/" + id), null, student).andExpect(status().isOk());
        assertThat(lessons.existsById(lesson.getId())).isTrue();
    }

    @Test
    void allowsOnlyAuthorOrAdminToManageCourseReviews() throws Exception {
        enroll(student);
        long id = create(
                "/courses/" + course.getId() + "/reviews", Map.of("rating", 5, "comment", "Great course"), student);
        request(get("/api/reviews/" + id), null, teacher).andExpect(status().isOk());
        request(get("/api/courses/" + course.getId() + "/reviews"), null, teacher)
                .andExpect(jsonPath("$[0].rating").value(5));
        request(put("/api/reviews/" + id), Map.of("rating", 4, "comment", "More examples"), student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").value(4));
        request(delete("/api/reviews/" + id), null, teacher).andExpect(status().isForbidden());
        request(delete("/api/reviews/" + id), null, student).andExpect(status().isNoContent());
    }

    @Test
    void validatesReviewRatingAndPreventsDuplicates() throws Exception {
        enroll(student);
        String path = "/api/courses/" + course.getId() + "/reviews";
        request(post(path), Map.of("rating", 6, "comment", "Invalid"), student).andExpect(status().isBadRequest());
        request(post(path), Map.of("rating", 5, "comment", "Not enrolled"), otherStudent)
                .andExpect(status().isForbidden());
        create("/courses/" + course.getId() + "/reviews", Map.of("rating", 5, "comment", "Good"), student);
        request(post(path), Map.of("rating", 4, "comment", "Again"), student).andExpect(status().isConflict());
    }

    @Test
    void tracksProgressIdempotentlyAndComputesStatistics() throws Exception {
        enroll(student);
        String path = "/api/lessons/" + lesson.getId() + "/progress";
        var first = tree(request(put(path), null, student).andExpect(status().isOk()));
        request(put(path), null, student)
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.completedAt").value(first.get("completedAt").asText()));
        request(get("/api/courses/" + course.getId() + "/statistics?studentId=" + student.getId()), null, student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress").value(100.0))
                .andExpect(jsonPath("$.completedLessons").value(1));
        request(get("/api/courses/" + course.getId() + "/progress?studentId=" + student.getId()), null, student)
                .andExpect(jsonPath("$.length()").value(1));
        request(get("/api/courses/" + course.getId() + "/statistics"), null, teacher)
                .andExpect(jsonPath("$.students").value(1));
        request(delete(path), null, student).andExpect(status().isNoContent());
        request(get("/api/courses/" + course.getId() + "/statistics?studentId=" + student.getId()), null, student)
                .andExpect(jsonPath("$.progress").value(0.0));
    }

    @Test
    void protectsProgressPrivacyAndEnrollmentStatusFromStudentChanges() throws Exception {
        enroll(student);
        request(get("/api/courses/" + course.getId() + "/statistics?studentId=" + student.getId()), null, otherStudent)
                .andExpect(status().isForbidden());
        request(
                        put("/api/courses/" + course.getId() + "/enrollments/" + student.getId()),
                        Map.of("status", "COMPLETED"),
                        student)
                .andExpect(status().isForbidden());
        request(put("/api/lessons/" + lesson.getId() + "/progress"), null, otherStudent)
                .andExpect(status().isForbidden());
    }
}
