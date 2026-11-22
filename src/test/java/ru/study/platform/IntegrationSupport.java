package ru.study.platform;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import ru.study.platform.domain.*;
import ru.study.platform.repository.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationSupport {

    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        DATABASE.start();
    }

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper mapper;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccountRepository accounts;

    @Autowired
    ProfileRepository profiles;

    @Autowired
    CourseRepository courses;

    @Autowired
    CourseModuleRepository modules;

    @Autowired
    LessonRepository lessons;

    @Autowired
    CategoryRepository categories;

    @Autowired
    AssignmentRepository assignments;

    @Autowired
    PasswordEncoder passwords;

    Account admin;
    Account teacher;
    Account student;
    Account otherStudent;
    Account otherTeacher;
    Category category;
    Course course;
    CourseModule module;
    Lesson lesson;

    @BeforeEach
    void fixtures() {
        jdbc.execute("TRUNCATE TABLE accounts, categories, tags, courses RESTART IDENTITY CASCADE");
        admin = account("Admin", "admin@test.local", Role.ADMIN);
        teacher = account("Teacher", "teacher@test.local", Role.TEACHER);
        student = account("Student", "student@test.local", Role.STUDENT);
        otherStudent = account("Other student", "other@test.local", Role.STUDENT);
        otherTeacher = account("Other teacher", "teacher2@test.local", Role.TEACHER);
        category = categories.save(new Category("Programming"));
        course = new Course();
        course.setTitle("Hibernate");
        course.setDescription("ORM course");
        course.setCategory(category);
        course.setTeacher(teacher);
        module = new CourseModule();
        module.setTitle("JPA");
        module.setPosition(1);
        lesson = new Lesson();
        lesson.setTitle("Entities");
        lesson.setContent("Persistence context");
        lesson.setPosition(1);
        module.addLesson(lesson);
        course.addModule(module);
        courses.saveAndFlush(course);
    }

    Account account(String name, String email, Role role) {
        Account value = accounts.save(new Account(name, email, passwords.encode("test-password-2026"), role));
        profiles.save(new Profile(value));
        return value;
    }

    Assignment assignment() {
        Assignment value = new Assignment();
        value.setLesson(lesson);
        value.setTitle("Map a course");
        value.setDescription("Use JPA annotations");
        value.setMaxScore(100);
        return assignments.save(value);
    }

    Map<String, Object> courseInput(String title) {
        return Map.of(
                "title",
                title,
                "description",
                "Updated description",
                "categoryId",
                category.getId(),
                "teacherId",
                teacher.getId());
    }

    ResultActions request(MockHttpServletRequestBuilder request, Object body, Account actor) throws Exception {
        if (actor != null) {
            request.with(jwt().jwt(token -> token.subject(actor.getId().toString())));
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        }
        return mvc.perform(request);
    }

    long create(String path, Object body, Account actor) throws Exception {
        return tree(request(post("/api" + path), body, actor)
                        .andExpect(status().isCreated())
                        .andExpect(header().exists("Location")))
                .get("id")
                .asLong();
    }

    JsonNode tree(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsByteArray());
    }

    void enroll(Account account) throws Exception {
        create("/courses/" + course.getId() + "/enrollments", Map.of("studentId", account.getId()), account);
    }
}
