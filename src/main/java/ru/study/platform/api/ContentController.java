package ru.study.platform.api;

import static ru.study.platform.api.dto.CatalogDtos.*;
import static ru.study.platform.api.dto.LearningDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.study.platform.service.ContentService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ContentController {

    private final ContentService service;

    @GetMapping("/courses/{courseId}/modules")
    List<ModuleView> modules(@PathVariable @Positive long courseId) {
        return service.modules(courseId);
    }

    @GetMapping("/modules/{id}")
    ModuleView module(@PathVariable @Positive long id) {
        return service.module(id);
    }

    @PostMapping("/courses/{courseId}/modules")
    ResponseEntity<ModuleView> createModule(
            @PathVariable @Positive long courseId, @Valid @RequestBody ModuleInput input) {
        ModuleView result = service.createModule(courseId, input);
        return Responses.created("modules", result.id(), result);
    }

    @PutMapping("/modules/{id}")
    ModuleView updateModule(@PathVariable @Positive long id, @Valid @RequestBody ModuleInput input) {
        return service.updateModule(id, input);
    }

    @DeleteMapping("/modules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteModule(@PathVariable @Positive long id) {
        service.deleteModule(id);
    }

    @GetMapping("/modules/{moduleId}/lessons")
    List<LessonView> lessons(@PathVariable @Positive long moduleId) {
        return service.lessons(moduleId);
    }

    @PostMapping("/modules/{moduleId}/lessons")
    ResponseEntity<LessonView> createLesson(
            @PathVariable @Positive long moduleId, @Valid @RequestBody LessonInput input) {
        LessonView result = service.createLesson(moduleId, input);
        return Responses.created("lessons", result.id(), result);
    }

    @GetMapping("/lessons/{id}")
    LessonView lesson(@PathVariable @Positive long id) {
        return service.lesson(id);
    }

    @PutMapping("/lessons/{id}")
    LessonView updateLesson(@PathVariable @Positive long id, @Valid @RequestBody LessonInput input) {
        return service.updateLesson(id, input);
    }

    @DeleteMapping("/lessons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLesson(@PathVariable @Positive long id) {
        service.deleteLesson(id);
    }

    @GetMapping("/lessons/{lessonId}/materials")
    List<MaterialView> materials(@PathVariable @Positive long lessonId) {
        return service.materials(lessonId);
    }

    @PostMapping("/lessons/{lessonId}/materials")
    ResponseEntity<MaterialView> createMaterial(
            @PathVariable @Positive long lessonId, @Valid @RequestBody MaterialInput input) {
        MaterialView result = service.createMaterial(lessonId, input);
        return Responses.created("materials", result.id(), result);
    }

    @GetMapping("/materials/{id}")
    MaterialView material(@PathVariable @Positive long id) {
        return service.material(id);
    }

    @PutMapping("/materials/{id}")
    MaterialView updateMaterial(@PathVariable @Positive long id, @Valid @RequestBody MaterialInput input) {
        return service.updateMaterial(id, input);
    }

    @DeleteMapping("/materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteMaterial(@PathVariable @Positive long id) {
        service.deleteMaterial(id);
    }

    @GetMapping("/courses/{courseId}/schedule")
    List<ScheduleView> schedule(@PathVariable @Positive long courseId) {
        return service.schedule(courseId);
    }

    @PostMapping("/lessons/{lessonId}/schedule")
    ResponseEntity<ScheduleView> createScheduled(
            @PathVariable @Positive long lessonId, @Valid @RequestBody ScheduleInput input) {
        ScheduleView result = service.createScheduledLesson(lessonId, input);
        return Responses.created("schedule", result.id(), result);
    }

    @GetMapping("/schedule/{id}")
    ScheduleView scheduled(@PathVariable @Positive long id) {
        return service.scheduledLesson(id);
    }

    @PutMapping("/schedule/{id}")
    ScheduleView updateScheduled(@PathVariable @Positive long id, @Valid @RequestBody ScheduleInput input) {
        return service.updateScheduledLesson(id, input);
    }

    @DeleteMapping("/schedule/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteScheduled(@PathVariable @Positive long id) {
        service.deleteScheduledLesson(id);
    }
}
