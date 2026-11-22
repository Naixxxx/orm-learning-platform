package ru.study.platform.api;

import static ru.study.platform.api.dto.CatalogDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.study.platform.service.TaxonomyService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class TaxonomyController {

    private final TaxonomyService service;

    @GetMapping("/categories")
    List<NamedView> categories() {
        return service.categories();
    }

    @GetMapping("/categories/{id}")
    NamedView category(@PathVariable @Positive long id) {
        return service.category(id);
    }

    @PostMapping("/categories")
    ResponseEntity<NamedView> createCategory(@Valid @RequestBody NameInput input) {
        NamedView result = service.saveCategory(null, input);
        return Responses.created("categories", result.id(), result);
    }

    @PutMapping("/categories/{id}")
    NamedView updateCategory(@PathVariable @Positive long id, @Valid @RequestBody NameInput input) {
        return service.saveCategory(id, input);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteCategory(@PathVariable @Positive long id) {
        service.deleteCategory(id);
    }

    @GetMapping("/tags")
    List<NamedView> tags() {
        return service.tags();
    }

    @GetMapping("/tags/{id}")
    NamedView tag(@PathVariable @Positive long id) {
        return service.tag(id);
    }

    @PostMapping("/tags")
    ResponseEntity<NamedView> createTag(@Valid @RequestBody NameInput input) {
        NamedView result = service.saveTag(null, input);
        return Responses.created("tags", result.id(), result);
    }

    @PutMapping("/tags/{id}")
    NamedView updateTag(@PathVariable @Positive long id, @Valid @RequestBody NameInput input) {
        return service.saveTag(id, input);
    }

    @DeleteMapping("/tags/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteTag(@PathVariable @Positive long id) {
        service.deleteTag(id);
    }
}
