package ru.study.platform;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.study.platform.config.DemoDataInitializer;
import ru.study.platform.domain.Course;
import ru.study.platform.repository.EnrollmentRepository;

class PersistenceIntegrationTest extends IntegrationSupport {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    DemoDataInitializer demo;

    @Autowired
    EnrollmentRepository enrollments;

    @Test
    void migratesPostgresqlSchemaAndRegistersExactlyEighteenEntities() {
        assertThat(DATABASE.getDockerImageName()).contains("postgres");
        assertThat(entityManagerFactory.getMetamodel().getEntities()).hasSize(18);
        assertThat(jdbc.queryForObject("select count(*) from databasechangelog", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "select count(*) from information_schema.table_constraints where constraint_type = 'FOREIGN KEY' and table_schema = 'public'",
                        Integer.class))
                .isGreaterThanOrEqualTo(20);
    }

    @Test
    void cascadesPersistFromCourseToModuleAndLesson() {
        assertThat(courses.count()).isEqualTo(1);
        assertThat(modules.count()).isEqualTo(1);
        assertThat(lessons.count()).isEqualTo(1);
        var read = lessons.findById(lesson.getId()).orElseThrow();
        assertThat(read.getModule().getId()).isEqualTo(module.getId());
    }

    @Test
    void reproducesLazyInitializationExceptionOutsidePersistenceContext() {
        Course detached = courses.findById(course.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(detached.getModules())).isFalse();
        assertThatThrownBy(() -> detached.getModules().size()).isInstanceOf(LazyInitializationException.class);
    }

    @Test
    void joinFetchInitializesRequestedCollectionBeforeSessionCloses() {
        Course detached = courses.findWithModulesById(course.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(detached.getModules())).isTrue();
        assertThat(detached.getModules()).hasSize(1);
        assertThat(Hibernate.isInitialized(detached.getModules().getFirst().getLessons()))
                .isFalse();
    }

    @Test
    void transactionAllowsSafeNavigationWithoutGlobalEagerLoading() {
        var transaction = new TransactionTemplate(transactionManager);
        Integer size = transaction.execute(status -> courses.findById(course.getId())
                .orElseThrow()
                .getModules()
                .getFirst()
                .getLessons()
                .size());
        assertThat(size).isEqualTo(1);
    }

    @Test
    void orphanRemovalDeletesOwnedModuleAndLessons() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Course managed = courses.findById(course.getId()).orElseThrow();
            managed.getModules().clear();
        });
        assertThat(modules.count()).isZero();
        assertThat(lessons.count()).isZero();
        assertThat(courses.count()).isEqualTo(1);
    }

    @Test
    void constraintsRejectInvalidValuesEvenWithoutControllerValidation() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into enrollments(student_id, course_id, enrolled_at, status) values (?, ?, now(), 'INVALID')",
                        student.getId(),
                        course.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                        "insert into lesson_progress(student_id, lesson_id, completed_at) values (?, ?, now())",
                        student.getId(),
                        999999L))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void seedsAllEntityTypesAndDoesNotDuplicateOnSecondRun() throws Exception {
        demo.run(null);
        for (String table : java.util.List.of(
                "accounts",
                "profiles",
                "categories",
                "tags",
                "courses",
                "course_modules",
                "lessons",
                "enrollments",
                "assignments",
                "submissions",
                "quizzes",
                "questions",
                "answer_options",
                "quiz_submissions",
                "course_reviews",
                "lesson_materials",
                "scheduled_lessons",
                "lesson_progress")) {
            assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class))
                    .as(table + " has demo data")
                    .isPositive();
        }
        long before = accounts.count();
        demo.run(null);
        assertThat(accounts.count()).isEqualTo(before);
        request(
                        post("/api/auth/login"),
                        Map.of("email", "student@learning.local", "password", "test-demo-password-2026"),
                        null)
                .andExpect(status().isOk());
    }

    @Test
    void preventsDeletingCourseWithEnrollmentsAndRollsBackCascade() throws Exception {
        enroll(student);
        request(delete("/api/courses/" + course.getId()), null, teacher).andExpect(status().isConflict());
        assertThat(courses.count()).isEqualTo(1);
        assertThat(modules.count()).isEqualTo(1);
        assertThat(lessons.count()).isEqualTo(1);
        assertThat(enrollments.count()).isEqualTo(1);
    }
}
