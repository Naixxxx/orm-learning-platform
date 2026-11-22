package ru.study.platform.service;

import static ru.study.platform.api.dto.CatalogDtos.*;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.Account;
import ru.study.platform.domain.Course;
import ru.study.platform.domain.Role;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.CourseModuleRepository;
import ru.study.platform.repository.CourseRepository;
import ru.study.platform.repository.LessonRepository;
import ru.study.platform.repository.TagRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final TagRepository tags;
    private final TaxonomyService taxonomy;
    private final AccountService accounts;
    private final AccessService access;

    public PageView<CourseView> list(Long categoryId, Long tagId, String search, int page, int size) {
        String pattern = "%"
                + Objects.requireNonNullElse(search, "")
                        .toLowerCase(Locale.ROOT)
                        .replace("!", "!!")
                        .replace("%", "!%")
                        .replace("_", "!_")
                + "%";
        return PageView.from(courses.search(categoryId, tagId, pattern, PageRequest.of(page, size, Sort.by("id")))
                .map(CourseView::from));
    }

    public CourseView get(long id) {
        return CourseView.from(require(id));
    }

    @Transactional
    public CourseView create(CourseInput input) {
        Account actor = access.current();
        if (actor.getRole() != Role.ADMIN && (actor.getRole() != Role.TEACHER || actor.getId() != input.teacherId())) {
            throw ApiException.forbidden();
        }
        Course course = new Course();
        apply(course, input);
        courses.save(course);
        log.info(
                "Created course id={} teacher={}",
                course.getId(),
                course.getTeacher().getId());
        return CourseView.from(course);
    }

    @Transactional
    public CourseView update(long id, CourseInput input) {
        Course course = require(id);
        access.teacher(course);
        if (!course.getTeacher().getId().equals(input.teacherId())
                && access.current().getRole() != Role.ADMIN) {
            throw ApiException.forbidden();
        }
        apply(course, input);
        return CourseView.from(course);
    }

    @Transactional
    public void delete(long id) {
        Course course = require(id);
        access.teacher(course);
        courses.delete(course);
        courses.flush();
        log.info("Deleted inactive course id={}", id);
    }

    public Curriculum curriculum(long id) {
        Course course = require(id);
        access.studentOrTeacher(course);
        Map<Long, List<LessonView>> byModule = lessons.findByModuleCourseIdOrderByPosition(id).stream()
                .map(LessonView::from)
                .collect(Collectors.groupingBy(LessonView::moduleId));
        List<ModuleTree> tree = modules.findByCourseIdOrderByPosition(id).stream()
                .map(module ->
                        new ModuleTree(ModuleView.from(module), byModule.getOrDefault(module.getId(), List.of())))
                .toList();
        return new Curriculum(CourseView.from(course), tree);
    }

    public List<NamedView> tags(long id) {
        Course course = courses.findDetailedById(id).orElseThrow(() -> ApiException.notFound("Курс"));
        return course.getTags().stream()
                .map(tag -> new NamedView(tag.getId(), tag.getName()))
                .sorted(Comparator.comparing(NamedView::name))
                .toList();
    }

    @Transactional
    public List<NamedView> replaceTags(long id, TagsInput input) {
        Course course = require(id);
        access.teacher(course);
        var found = tags.findAllById(input.tagIds());
        if (found.size() != input.tagIds().size()) {
            throw ApiException.invalid("Один или несколько тегов не существуют");
        }
        course.getTags().clear();
        course.getTags().addAll(found);
        return found.stream()
                .map(tag -> new NamedView(tag.getId(), tag.getName()))
                .toList();
    }

    public Course require(long id) {
        return courses.findById(id).orElseThrow(() -> ApiException.notFound("Курс"));
    }

    private void apply(Course course, CourseInput input) {
        Account teacher = accounts.require(input.teacherId());
        if (teacher.getRole() != Role.TEACHER) {
            throw ApiException.invalid("Преподавателем курса может быть только пользователь с ролью TEACHER");
        }
        course.setTitle(input.title().strip());
        course.setDescription(input.description().strip());
        course.setCategory(taxonomy.requireCategory(input.categoryId()));
        course.setTeacher(teacher);
        course.setStartDate(input.startDate());
    }
}
