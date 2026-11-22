package ru.study.platform.api;

import static ru.study.platform.api.dto.LearningDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.study.platform.service.AssignmentService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class AssignmentController {

    private final AssignmentService service;

    @GetMapping("/lessons/{lessonId}/assignments")
    List<AssignmentView> assignments(@PathVariable @Positive long lessonId) {
        return service.list(lessonId);
    }

    @PostMapping("/lessons/{lessonId}/assignments")
    ResponseEntity<AssignmentView> create(
            @PathVariable @Positive long lessonId, @Valid @RequestBody AssignmentInput input) {
        AssignmentView result = service.create(lessonId, input);
        return Responses.created("assignments", result.id(), result);
    }

    @GetMapping("/assignments/{id}")
    AssignmentView get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PutMapping("/assignments/{id}")
    AssignmentView update(@PathVariable @Positive long id, @Valid @RequestBody AssignmentInput input) {
        return service.update(id, input);
    }

    @DeleteMapping("/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable @Positive long id) {
        service.delete(id);
    }

    @PostMapping("/assignments/{assignmentId}/submissions")
    ResponseEntity<SubmissionView> submit(
            @PathVariable @Positive long assignmentId, @Valid @RequestBody WorkInput input) {
        SubmissionView result = service.submit(assignmentId, input);
        return Responses.created("submissions", result.id(), result);
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    List<SubmissionView> submissions(@PathVariable @Positive long assignmentId) {
        return service.submissions(assignmentId);
    }

    @GetMapping("/submissions/{id}")
    SubmissionView submission(@PathVariable @Positive long id) {
        return service.submission(id);
    }

    @PutMapping("/submissions/{id}/grade")
    SubmissionView grade(@PathVariable @Positive long id, @Valid @RequestBody GradeInput input) {
        return service.grade(id, input);
    }

    @GetMapping("/users/{studentId}/submissions")
    List<SubmissionView> studentSubmissions(@PathVariable @Positive long studentId) {
        return service.studentSubmissions(studentId);
    }
}
