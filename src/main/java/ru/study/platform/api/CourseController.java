package ru.study.platform.api;

import static ru.study.platform.api.dto.CatalogDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.study.platform.service.CatalogService;

@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Validated
public class CourseController {

    private final CatalogService service;

    @GetMapping
    PageView<CourseView> list(
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Positive Long tagId,
            @RequestParam(required = false) @Size(max = 200) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(categoryId, tagId, search, page, size);
    }

    @GetMapping("/{id}")
    CourseView get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PostMapping
    ResponseEntity<CourseView> create(@Valid @RequestBody CourseInput input) {
        CourseView result = service.create(input);
        return Responses.created("courses", result.id(), result);
    }

    @PutMapping("/{id}")
    CourseView update(@PathVariable @Positive long id, @Valid @RequestBody CourseInput input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable @Positive long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/curriculum")
    Curriculum curriculum(@PathVariable @Positive long id) {
        return service.curriculum(id);
    }

    @GetMapping("/{id}/tags")
    List<NamedView> tags(@PathVariable @Positive long id) {
        return service.tags(id);
    }

    @PutMapping("/{id}/tags")
    List<NamedView> replaceTags(@PathVariable @Positive long id, @Valid @RequestBody TagsInput input) {
        return service.replaceTags(id, input);
    }
}
