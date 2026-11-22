package ru.study.platform.config;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.*;
import ru.study.platform.repository.*;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final CourseRepository courses;
    private final AssignmentRepository assignments;
    private final EnrollmentRepository enrollments;
    private final SubmissionRepository submissions;
    private final QuizRepository quizzes;
    private final QuizSubmissionRepository results;
    private final LessonMaterialRepository materials;
    private final ScheduledLessonRepository schedule;
    private final LessonProgressRepository progress;
    private final CourseReviewRepository reviews;
    private final PasswordEncoder passwords;
    private final Clock clock;

    @Value("${app.demo.password}")
    private String demoPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (accounts.existsByEmail("admin@learning.local")) {
            return;
        }
        if (demoPassword.length() < 10 || demoPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("DEMO_PASSWORD must be 10–72 characters and at most 72 UTF-8 bytes");
        }
        account("Администратор", "admin@learning.local", Role.ADMIN);
        Account teacher = account("Анна Смирнова", "teacher@learning.local", Role.TEACHER);
        Account student = account("Иван Петров", "student@learning.local", Role.STUDENT);
        Account second = account("Мария Орлова", "student2@learning.local", Role.STUDENT);
        Category category = categories.save(new Category("Разработка и базы данных"));
        Tag javaTag = tags.save(new Tag("Java"));
        Tag hibernate = tags.save(new Tag("Hibernate"));
        tags.save(new Tag("PostgreSQL"));

        Course course = new Course();
        course.setTitle("ORM и Hibernate: от модели к приложению");
        course.setDescription("Практический курс по JPA, связям сущностей, транзакциям и эффективной загрузке данных. "
                + "Создадим предметную модель и научимся находить ошибки ORM.");
        course.setTeacher(teacher);
        course.setCategory(category);
        course.setStartDate(java.time.LocalDate.now(clock).plusDays(7));
        course.getTags().addAll(List.of(javaTag, hibernate));
        CourseModule basics = module("Основы JPA", 1);
        CourseModule advanced = module("Загрузка и транзакции", 2);
        Lesson entities = lesson(
                "Сущности и жизненный цикл",
                "Entity — объект с устойчивой идентичностью. "
                        + "Разберите состояния transient, managed, detached и removed. "
                        + "Persistence context отслеживает изменения managed-сущностей.",
                1);
        Lesson relations = lesson(
                "Отношения между сущностями",
                "Владеющая сторона определяет обновление внешнего ключа. "
                        + "Для связей с дополнительными полями используйте отдельную сущность. "
                        + "Не применяйте CascadeType.REMOVE к общим справочникам.",
                2);
        Lesson lazy = lesson(
                "Ленивая загрузка без сюрпризов",
                "Коллекция LAZY загружается при обращении внутри сессии. "
                        + "После закрытия сессии неинициализированная коллекция вызывает LazyInitializationException. "
                        + "Используйте DTO, JOIN FETCH и EntityGraph под конкретный сценарий.",
                1);
        Lesson transactions = lesson(
                "Транзакции и блокировки",
                "Транзакция задаёт атомарную границу бизнес-операции. "
                        + "@Version помогает обнаружить конкурентное обновление. Уникальные ограничения БД "
                        + "защищают от дублирования даже при одновременных запросах.",
                2);
        basics.addLesson(entities);
        basics.addLesson(relations);
        advanced.addLesson(lazy);
        advanced.addLesson(transactions);
        course.addModule(basics);
        course.addModule(advanced);
        courses.saveAndFlush(course);
        enroll(student, course);
        enroll(second, course);

        Assignment first = assignment(
                entities,
                "Спроектируйте сущность курса",
                "Создайте Course с идентификатором, названием и связью с преподавателем. Обоснуйте ограничения.",
                100);
        Assignment completed = assignment(
                relations,
                "Связь с дополнительными полями",
                "Реализуйте Enrollment и объясните, почему здесь недостаточно обычного ManyToMany.",
                100);
        Submission work = new Submission();
        work.setAssignment(completed);
        work.setStudent(student);
        work.setContent(
                "Enrollment хранит дату и статус записи; уникальная пара student_id + course_id исключает дубли.");
        work.setSubmittedAt(clock.instant().minus(1, ChronoUnit.HOURS));
        work.setScore(95);
        work.setFeedback("Верно. Добавьте индекс для выборки студентов курса.");
        submissions.save(work);

        Quiz quiz = new Quiz();
        quiz.setModule(basics);
        quiz.setTitle("Проверяем основы JPA");
        quiz.setPassPercent(60);
        Question single = question("Какая аннотация объявляет JPA-сущность?", QuestionType.SINGLE_CHOICE, 1, 2);
        single.addOption(option("@Entity", true, 1));
        single.addOption(option("@Service", false, 2));
        single.addOption(option("@Repository", false, 3));
        Question multiple = question("Какие утверждения о связях верны?", QuestionType.MULTIPLE_CHOICE, 2, 3);
        multiple.addOption(option("OneToMany по умолчанию LAZY", true, 1));
        multiple.addOption(option("ManyToMany всегда требует CascadeType.REMOVE", false, 2));
        multiple.addOption(option("Enrollment может хранить дату записи", true, 3));
        quiz.addQuestion(single);
        quiz.addQuestion(multiple);
        quizzes.saveAndFlush(quiz);
        QuizSubmission result = new QuizSubmission();
        result.setQuiz(quiz);
        result.setStudent(second);
        result.setScore(5);
        result.setMaxScore(5);
        result.setPassed(true);
        result.setTakenAt(clock.instant());
        results.save(result);

        LessonMaterial material = new LessonMaterial();
        material.setLesson(entities);
        material.setTitle("Hibernate ORM User Guide");
        material.setUrl("https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html");
        materials.save(material);
        ScheduledLesson scheduled = new ScheduledLesson();
        scheduled.setLesson(entities);
        scheduled.setStartsAt(clock.instant().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS));
        scheduled.setEndsAt(scheduled.getStartsAt().plus(90, ChronoUnit.MINUTES));
        scheduled.setMeetingUrl("https://example.org/learning/jpa-intro");
        schedule.save(scheduled);
        LessonProgress done = new LessonProgress();
        done.setStudent(second);
        done.setLesson(entities);
        done.setCompletedAt(clock.instant());
        progress.save(done);
        CourseReview review = new CourseReview();
        review.setStudent(second);
        review.setCourse(course);
        review.setRating(5);
        review.setComment("Понятная структура и практические задания!");
        review.setCreatedAt(clock.instant());
        reviews.save(review);
        log.info("Demo dataset created: course={}, first assignment={}", course.getId(), first.getId());
    }

    private Account account(String name, String email, Role role) {
        Account account = accounts.save(new Account(name, email, passwords.encode(demoPassword), role));
        Profile profile = new Profile(account);
        profile.setBiography("Демонстрационная учётная запись");
        profiles.save(profile);
        return account;
    }

    private CourseModule module(String title, int position) {
        CourseModule module = new CourseModule();
        module.setTitle(title);
        module.setDescription("Теория и практика: " + title);
        module.setPosition(position);
        return module;
    }

    private Lesson lesson(String title, String content, int position) {
        Lesson lesson = new Lesson();
        lesson.setTitle(title);
        lesson.setContent(content);
        lesson.setPosition(position);
        return lesson;
    }

    private void enroll(Account student, Course course) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setCourse(course);
        enrollment.setEnrolledAt(clock.instant());
        enrollments.save(enrollment);
    }

    private Assignment assignment(Lesson lesson, String title, String description, int maxScore) {
        Assignment assignment = new Assignment();
        assignment.setLesson(lesson);
        assignment.setTitle(title);
        assignment.setDescription(description);
        assignment.setMaxScore(maxScore);
        assignment.setDueAt(clock.instant().plus(30, ChronoUnit.DAYS));
        return assignments.save(assignment);
    }

    private Question question(String text, QuestionType type, int position, int points) {
        Question question = new Question();
        question.setText(text);
        question.setType(type);
        question.setPosition(position);
        question.setPoints(points);
        return question;
    }

    private AnswerOption option(String text, boolean correct, int position) {
        AnswerOption option = new AnswerOption();
        option.setText(text);
        option.setCorrect(correct);
        option.setPosition(position);
        return option;
    }
}
