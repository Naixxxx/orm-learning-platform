package ru.study.platform;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuizIntegrationTest extends IntegrationSupport {

    long quizId;
    long questionId;
    long correct;
    long incorrect;

    @BeforeEach
    void quizFixture() throws Exception {
        quizId = create(
                "/modules/" + module.getId() + "/quiz", Map.of("title", "JPA basics", "passPercent", 60), teacher);
        questionId = create(
                "/quizzes/" + quizId + "/questions",
                Map.of("text", "Entity annotation?", "type", "SINGLE_CHOICE", "position", 1, "points", 2),
                teacher);
        correct = create(
                "/questions/" + questionId + "/options",
                Map.of("text", "@Entity", "correct", true, "position", 1),
                teacher);
        incorrect = create(
                "/questions/" + questionId + "/options",
                Map.of("text", "@Service", "correct", false, "position", 2),
                teacher);
    }

    Map<String, Object> answers(long option) {
        return Map.of("answers", List.of(Map.of("questionId", questionId, "optionIds", List.of(option))));
    }

    @Test
    void hidesAnswerKeyFromStudentButShowsItToCourseTeacher() throws Exception {
        enroll(student);
        request(get("/api/quizzes/" + quizId), null, student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].options[0].correct").doesNotExist());
        request(get("/api/quizzes/" + quizId), null, teacher)
                .andExpect(jsonPath("$.questions[0].options[0].correct").value(true));
        request(get("/api/answer-options/" + correct), null, student)
                .andExpect(jsonPath("$.correct").doesNotExist());
        request(get("/api/questions/" + questionId), null, student).andExpect(status().isOk());
        request(get("/api/courses/" + course.getId() + "/quizzes"), null, student)
                .andExpect(jsonPath("$[0].id").value(quizId));
    }

    @Test
    void computesAndPersistsSuccessfulAttemptAndSupportsResultsQueries() throws Exception {
        enroll(student);
        long id = create("/quizzes/" + quizId + "/attempts", answers(correct), student);
        request(get("/api/quiz-results/" + id), null, student)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(2))
                .andExpect(jsonPath("$.maxScore").value(2))
                .andExpect(jsonPath("$.passed").value(true));
        request(get("/api/quizzes/" + quizId + "/attempts"), null, teacher)
                .andExpect(jsonPath("$[0].studentId").value(student.getId()));
        request(get("/api/users/" + student.getId() + "/quiz-results"), null, student)
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void recordsWrongAnswerWithoutPassing() throws Exception {
        enroll(student);
        request(post("/api/quizzes/" + quizId + "/attempts"), answers(incorrect), student)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.passed").value(false));
    }

    @Test
    void preventsRepeatedAttemptAndFreezesQuizAfterSubmission() throws Exception {
        enroll(student);
        create("/quizzes/" + quizId + "/attempts", answers(correct), student);
        request(post("/api/quizzes/" + quizId + "/attempts"), answers(correct), student)
                .andExpect(status().isConflict());
        request(put("/api/quizzes/" + quizId), Map.of("title", "Changed", "passPercent", 50), teacher)
                .andExpect(status().isConflict());
        request(delete("/api/questions/" + questionId), null, teacher).andExpect(status().isConflict());
        request(delete("/api/answer-options/" + correct), null, teacher).andExpect(status().isConflict());
        request(delete("/api/quizzes/" + quizId), null, teacher).andExpect(status().isConflict());
    }

    @Test
    void rejectsUnenrolledAttemptsAndProtectsResultPrivacy() throws Exception {
        request(post("/api/quizzes/" + quizId + "/attempts"), answers(correct), student)
                .andExpect(status().isForbidden());
        enroll(student);
        long id = create("/quizzes/" + quizId + "/attempts", answers(correct), student);
        request(get("/api/quiz-results/" + id), null, otherStudent).andExpect(status().isForbidden());
        request(get("/api/quizzes/" + quizId + "/attempts"), null, student).andExpect(status().isForbidden());
    }

    @Test
    void validatesQuestionAndOptionOwnershipInSubmittedAnswers() throws Exception {
        enroll(student);
        request(post("/api/quizzes/" + quizId + "/attempts"), answers(999999L), student)
                .andExpect(status().isBadRequest());
        request(
                        post("/api/quizzes/" + quizId + "/attempts"),
                        Map.of("answers", List.of(Map.of("questionId", 999999L, "optionIds", List.of(correct)))),
                        student)
                .andExpect(status().isBadRequest());
        request(
                        post("/api/quizzes/" + quizId + "/attempts"),
                        Map.of(
                                "answers",
                                List.of(
                                        Map.of("questionId", questionId, "optionIds", List.of(correct)),
                                        Map.of("questionId", questionId, "optionIds", List.of(incorrect)))),
                        student)
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportsMultipleChoiceWithExactSetScoring() throws Exception {
        request(
                        put("/api/questions/" + questionId),
                        Map.of("text", "Choose JPA annotations", "type", "MULTIPLE_CHOICE", "position", 1, "points", 3),
                        teacher)
                .andExpect(status().isOk());
        long secondCorrect = create(
                "/questions/" + questionId + "/options",
                Map.of("text", "@Id", "correct", true, "position", 3),
                teacher);
        enroll(student);
        enroll(otherStudent);
        request(post("/api/quizzes/" + quizId + "/attempts"), answers(correct), student)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(0));
        request(
                        post("/api/quizzes/" + quizId + "/attempts"),
                        Map.of(
                                "answers",
                                List.of(Map.of(
                                        "questionId", questionId, "optionIds", List.of(correct, secondCorrect)))),
                        otherStudent)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(3));
    }

    @Test
    void rejectsInvalidSingleChoiceAndUnreadyQuiz() throws Exception {
        enroll(student);
        request(
                        post("/api/quizzes/" + quizId + "/attempts"),
                        Map.of(
                                "answers",
                                List.of(Map.of("questionId", questionId, "optionIds", List.of(correct, incorrect)))),
                        student)
                .andExpect(status().isBadRequest());
        request(delete("/api/answer-options/" + incorrect), null, teacher).andExpect(status().isNoContent());
        request(post("/api/quizzes/" + quizId + "/attempts"), answers(correct), student)
                .andExpect(status().isConflict());
    }

    @Test
    void managesQuizQuestionAndOptionCrudBeforeFirstAttempt() throws Exception {
        request(put("/api/quizzes/" + quizId), Map.of("title", "Updated quiz", "passPercent", 70), teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated quiz"));
        request(
                        put("/api/answer-options/" + correct),
                        Map.of("text", "Entity", "correct", true, "position", 1),
                        teacher)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Entity"));
        request(get("/api/answer-options/" + correct), null, teacher).andExpect(status().isOk());
        request(delete("/api/questions/" + questionId), null, teacher).andExpect(status().isNoContent());
        request(get("/api/answer-options/" + correct), null, teacher).andExpect(status().isNotFound());
        request(delete("/api/quizzes/" + quizId), null, teacher).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from answer_options", Long.class))
                .isZero();
    }

    @Test
    void enforcesOneQuizPerModuleAndTeacherOwnership() throws Exception {
        request(
                        post("/api/modules/" + module.getId() + "/quiz"),
                        Map.of("title", "Duplicate", "passPercent", 50),
                        teacher)
                .andExpect(status().isConflict());
        request(delete("/api/quizzes/" + quizId), null, otherTeacher).andExpect(status().isForbidden());
    }

    @Test
    void preservesResultsAfterWithdrawalAndProtectsParentDeletion() throws Exception {
        enroll(student);
        long id = create("/quizzes/" + quizId + "/attempts", answers(correct), student);
        request(delete("/api/courses/" + course.getId() + "/enrollments/" + student.getId()), null, student)
                .andExpect(status().isNoContent());
        request(get("/api/quiz-results/" + id), null, student).andExpect(status().isOk());
        request(delete("/api/modules/" + module.getId()), null, teacher).andExpect(status().isConflict());
        request(get("/api/quiz-results/" + id), null, teacher).andExpect(status().isOk());
    }
}
