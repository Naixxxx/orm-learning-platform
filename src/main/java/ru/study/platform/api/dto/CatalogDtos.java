package ru.study.platform.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import ru.study.platform.domain.Course;
import ru.study.platform.domain.CourseModule;
import ru.study.platform.domain.Lesson;

public final class CatalogDtos {

    private CatalogDtos() {}

    public record NameInput(@NotBlank @Size(max = 60) String name) {}

    public record NamedView(long id, String name) {}

    public record CourseInput(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String description,
            @Positive long categoryId,
            @Positive long teacherId,
            LocalDate startDate) {}

    public record CourseView(
            long id,
            String title,
            String description,
            long categoryId,
            String category,
            long teacherId,
            String teacher,
            LocalDate startDate) {

        public static CourseView from(Course course) {
            return new CourseView(
                    course.getId(),
                    course.getTitle(),
                    course.getDescription(),
                    course.getCategory().getId(),
                    course.getCategory().getName(),
                    course.getTeacher().getId(),
                    course.getTeacher().getName(),
                    course.getStartDate());
        }
    }

    public record TagsInput(@NotNull @Size(max = 30) Set<@NotNull @Positive Long> tagIds) {}

    public record ModuleInput(
            @NotBlank @Size(max = 200) String title, @Size(max = 4000) String description, @Positive int position) {}

    public record ModuleView(long id, long courseId, String title, String description, int position) {

        public static ModuleView from(CourseModule module) {
            return new ModuleView(
                    module.getId(),
                    module.getCourse().getId(),
                    module.getTitle(),
                    module.getDescription(),
                    module.getPosition());
        }
    }

    public record LessonInput(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 30000) String content,
            @Positive int position) {}

    public record LessonView(long id, long moduleId, String title, String content, int position) {

        public static LessonView from(Lesson lesson) {
            return new LessonView(
                    lesson.getId(),
                    lesson.getModule().getId(),
                    lesson.getTitle(),
                    lesson.getContent(),
                    lesson.getPosition());
        }
    }

    public record ModuleTree(ModuleView module, List<LessonView> lessons) {}

    public record Curriculum(CourseView course, List<ModuleTree> modules) {}

    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageView<T> from(org.springframework.data.domain.Page<T> page) {
            return new PageView<>(
                    page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        }
    }
}
