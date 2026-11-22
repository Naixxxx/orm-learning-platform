package ru.study.platform.api;

import static ru.study.platform.api.dto.LearningDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.study.platform.service.EnrollmentService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class EnrollmentController {

    private final EnrollmentService service;

    @PostMapping("/courses/{courseId}/enrollments")
    ResponseEntity<EnrollmentView> enroll(
            @PathVariable @Positive long courseId, @Valid @RequestBody EnrollmentInput input) {
        EnrollmentView result = service.enroll(courseId, input.studentId());
        return ResponseEntity.created(URI.create("/api/courses/" + courseId + "/enrollments/" + input.studentId()))
                .body(result);
    }

    @GetMapping("/courses/{courseId}/enrollments")
    List<EnrollmentView> students(@PathVariable @Positive long courseId) {
        return service.students(courseId);
    }

    @GetMapping("/courses/{courseId}/enrollments/{studentId}")
    EnrollmentView get(@PathVariable @Positive long courseId, @PathVariable @Positive long studentId) {
        return service.get(courseId, studentId);
    }

    @PutMapping("/courses/{courseId}/enrollments/{studentId}")
    EnrollmentView update(
            @PathVariable @Positive long courseId,
            @PathVariable @Positive long studentId,
            @Valid @RequestBody EnrollmentUpdate input) {
        return service.update(courseId, studentId, input.status());
    }

    @DeleteMapping("/courses/{courseId}/enrollments/{studentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void withdraw(@PathVariable @Positive long courseId, @PathVariable @Positive long studentId) {
        service.withdraw(courseId, studentId);
    }

    @GetMapping("/users/{studentId}/enrollments")
    List<EnrollmentView> courses(@PathVariable @Positive long studentId) {
        return service.courses(studentId);
    }
}
