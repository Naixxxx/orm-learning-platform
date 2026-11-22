package ru.study.platform.service;

import static ru.study.platform.api.dto.CatalogDtos.*;
import static ru.study.platform.api.dto.LearningDtos.*;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.Course;
import ru.study.platform.domain.CourseModule;
import ru.study.platform.domain.Lesson;
import ru.study.platform.domain.LessonMaterial;
import ru.study.platform.domain.ScheduledLesson;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.CourseModuleRepository;
import ru.study.platform.repository.LessonMaterialRepository;
import ru.study.platform.repository.LessonRepository;
import ru.study.platform.repository.ScheduledLessonRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentService {

    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final LessonMaterialRepository materials;
    private final ScheduledLessonRepository schedule;
    private final CatalogService catalog;
    private final AccessService access;

    public List<ModuleView> modules(long courseId) {
        access.studentOrTeacher(catalog.require(courseId));
        return modules.findByCourseIdOrderByPosition(courseId).stream()
                .map(ModuleView::from)
                .toList();
    }

    public ModuleView module(long id) {
        CourseModule module = requireModule(id);
        access.studentOrTeacher(module.getCourse());
        return ModuleView.from(module);
    }

    @Transactional
    public ModuleView createModule(long courseId, ModuleInput input) {
        Course course = catalog.require(courseId);
        access.teacher(course);
        CourseModule module = new CourseModule();
        module.setCourse(course);
        apply(module, input);
        return ModuleView.from(modules.saveAndFlush(module));
    }

    @Transactional
    public ModuleView updateModule(long id, ModuleInput input) {
        CourseModule module = requireModule(id);
        access.teacher(module.getCourse());
        apply(module, input);
        return ModuleView.from(module);
    }

    @Transactional
    public void deleteModule(long id) {
        CourseModule module = requireModule(id);
        access.teacher(module.getCourse());
        modules.delete(module);
        modules.flush();
    }

    public List<LessonView> lessons(long moduleId) {
        access.studentOrTeacher(requireModule(moduleId).getCourse());
        return lessons.findByModuleIdOrderByPosition(moduleId).stream()
                .map(LessonView::from)
                .toList();
    }

    public LessonView lesson(long id) {
        Lesson lesson = requireLesson(id);
        access.studentOrTeacher(lesson.getModule().getCourse());
        return LessonView.from(lesson);
    }

    @Transactional
    public LessonView createLesson(long moduleId, LessonInput input) {
        CourseModule module = requireModule(moduleId);
        access.teacher(module.getCourse());
        Lesson lesson = new Lesson();
        lesson.setModule(module);
        apply(lesson, input);
        return LessonView.from(lessons.saveAndFlush(lesson));
    }

    @Transactional
    public LessonView updateLesson(long id, LessonInput input) {
        Lesson lesson = requireLesson(id);
        access.teacher(lesson.getModule().getCourse());
        apply(lesson, input);
        return LessonView.from(lesson);
    }

    @Transactional
    public void deleteLesson(long id) {
        Lesson lesson = requireLesson(id);
        access.teacher(lesson.getModule().getCourse());
        lessons.delete(lesson);
        lessons.flush();
    }

    public List<MaterialView> materials(long lessonId) {
        access.studentOrTeacher(requireLesson(lessonId).getModule().getCourse());
        return materials.findByLessonIdOrderById(lessonId).stream()
                .map(MaterialView::from)
                .toList();
    }

    public MaterialView material(long id) {
        LessonMaterial material = requireMaterial(id);
        access.studentOrTeacher(material.getLesson().getModule().getCourse());
        return MaterialView.from(material);
    }

    @Transactional
    public MaterialView createMaterial(long lessonId, MaterialInput input) {
        Lesson lesson = requireLesson(lessonId);
        access.teacher(lesson.getModule().getCourse());
        LessonMaterial material = new LessonMaterial();
        material.setLesson(lesson);
        material.setTitle(input.title().strip());
        material.setUrl(input.url());
        return MaterialView.from(materials.save(material));
    }

    @Transactional
    public MaterialView updateMaterial(long id, MaterialInput input) {
        LessonMaterial material = requireMaterial(id);
        access.teacher(material.getLesson().getModule().getCourse());
        material.setTitle(input.title().strip());
        material.setUrl(input.url());
        return MaterialView.from(material);
    }

    @Transactional
    public void deleteMaterial(long id) {
        LessonMaterial material = requireMaterial(id);
        access.teacher(material.getLesson().getModule().getCourse());
        materials.delete(material);
    }

    public List<ScheduleView> schedule(long courseId) {
        access.studentOrTeacher(catalog.require(courseId));
        return schedule.findByLessonModuleCourseIdOrderByStartsAt(courseId).stream()
                .map(ScheduleView::from)
                .toList();
    }

    public ScheduleView scheduledLesson(long id) {
        ScheduledLesson value = requireScheduled(id);
        access.studentOrTeacher(value.getLesson().getModule().getCourse());
        return ScheduleView.from(value);
    }

    @Transactional
    public ScheduleView createScheduledLesson(long lessonId, ScheduleInput input) {
        Lesson lesson = requireLesson(lessonId);
        access.teacher(lesson.getModule().getCourse());
        ScheduledLesson value = new ScheduledLesson();
        value.setLesson(lesson);
        apply(value, input);
        return ScheduleView.from(schedule.save(value));
    }

    @Transactional
    public ScheduleView updateScheduledLesson(long id, ScheduleInput input) {
        ScheduledLesson value = requireScheduled(id);
        access.teacher(value.getLesson().getModule().getCourse());
        apply(value, input);
        return ScheduleView.from(value);
    }

    @Transactional
    public void deleteScheduledLesson(long id) {
        ScheduledLesson value = requireScheduled(id);
        access.teacher(value.getLesson().getModule().getCourse());
        schedule.delete(value);
    }

    public CourseModule requireModule(long id) {
        return modules.findById(id).orElseThrow(() -> ApiException.notFound("Модуль"));
    }

    public Lesson requireLesson(long id) {
        return lessons.findById(id).orElseThrow(() -> ApiException.notFound("Урок"));
    }

    private LessonMaterial requireMaterial(long id) {
        return materials.findById(id).orElseThrow(() -> ApiException.notFound("Материал"));
    }

    private ScheduledLesson requireScheduled(long id) {
        return schedule.findById(id).orElseThrow(() -> ApiException.notFound("Занятие"));
    }

    private void apply(CourseModule module, ModuleInput input) {
        module.setTitle(input.title().strip());
        module.setDescription(input.description());
        module.setPosition(input.position());
    }

    private void apply(Lesson lesson, LessonInput input) {
        lesson.setTitle(input.title().strip());
        lesson.setContent(input.content());
        lesson.setPosition(input.position());
    }

    private void apply(ScheduledLesson value, ScheduleInput input) {
        if (!input.endsAt().isAfter(input.startsAt())) {
            throw ApiException.invalid("Окончание занятия должно быть позже начала");
        }
        value.setStartsAt(input.startsAt());
        value.setEndsAt(input.endsAt());
        value.setMeetingUrl(input.meetingUrl());
    }
}
