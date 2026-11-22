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
import ru.study.platform.service.ActivityService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class ActivityController {

    private final ActivityService service;

    @GetMapping("/courses/{courseId}/reviews")
    List<ReviewView> reviews(@PathVariable @Positive long courseId) {
        return service.reviews(courseId);
    }

    @PostMapping("/courses/{courseId}/reviews")
    ResponseEntity<ReviewView> createReview(
            @PathVariable @Positive long courseId, @Valid @RequestBody ReviewInput input) {
        ReviewView result = service.createReview(courseId, input);
        return Responses.created("reviews", result.id(), result);
    }

    @GetMapping("/reviews/{id}")
    ReviewView review(@PathVariable @Positive long id) {
        return service.review(id);
    }

    @PutMapping("/reviews/{id}")
    ReviewView updateReview(@PathVariable @Positive long id, @Valid @RequestBody ReviewInput input) {
        return service.updateReview(id, input);
    }

    @DeleteMapping("/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteReview(@PathVariable @Positive long id) {
        service.deleteReview(id);
    }

    @PutMapping("/lessons/{lessonId}/progress")
    ProgressView complete(@PathVariable @Positive long lessonId) {
        return service.complete(lessonId);
    }

    @DeleteMapping("/lessons/{lessonId}/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reset(@PathVariable @Positive long lessonId) {
        service.reset(lessonId);
    }

    @GetMapping("/courses/{courseId}/progress")
    List<ProgressView> progress(@PathVariable @Positive long courseId, @RequestParam @Positive long studentId) {
        return service.progress(courseId, studentId);
    }

    @GetMapping("/courses/{courseId}/statistics")
    CourseStatistics statistics(
            @PathVariable @Positive long courseId, @RequestParam(required = false) @Positive Long studentId) {
        return service.statistics(courseId, studentId);
    }
}
