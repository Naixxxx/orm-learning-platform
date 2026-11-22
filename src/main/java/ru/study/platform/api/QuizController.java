package ru.study.platform.api;

import static ru.study.platform.api.dto.QuizDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.study.platform.service.QuizService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class QuizController {

    private final QuizService service;

    @GetMapping("/courses/{courseId}/quizzes")
    List<QuizView> quizzes(@PathVariable @Positive long courseId) {
        return service.list(courseId);
    }

    @PostMapping("/modules/{moduleId}/quiz")
    ResponseEntity<QuizView> create(@PathVariable @Positive long moduleId, @Valid @RequestBody QuizInput input) {
        QuizView result = service.create(moduleId, input);
        return Responses.created("quizzes", result.id(), result);
    }

    @GetMapping("/quizzes/{id}")
    QuizView quiz(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PutMapping("/quizzes/{id}")
    QuizView update(@PathVariable @Positive long id, @Valid @RequestBody QuizInput input) {
        return service.update(id, input);
    }

    @DeleteMapping("/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable @Positive long id) {
        service.delete(id);
    }

    @PostMapping("/quizzes/{quizId}/questions")
    ResponseEntity<QuestionView> addQuestion(
            @PathVariable @Positive long quizId, @Valid @RequestBody QuestionInput input) {
        QuestionView result = service.addQuestion(quizId, input);
        return Responses.created("questions", result.id(), result);
    }

    @GetMapping("/questions/{id}")
    QuestionView question(@PathVariable @Positive long id) {
        return service.question(id);
    }

    @PutMapping("/questions/{id}")
    QuestionView updateQuestion(@PathVariable @Positive long id, @Valid @RequestBody QuestionInput input) {
        return service.updateQuestion(id, input);
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteQuestion(@PathVariable @Positive long id) {
        service.deleteQuestion(id);
    }

    @PostMapping("/questions/{questionId}/options")
    ResponseEntity<OptionView> addOption(
            @PathVariable @Positive long questionId, @Valid @RequestBody OptionInput input) {
        OptionView result = service.addOption(questionId, input);
        return Responses.created("answer-options", result.id(), result);
    }

    @GetMapping("/answer-options/{id}")
    OptionView option(@PathVariable @Positive long id) {
        return service.option(id);
    }

    @PutMapping("/answer-options/{id}")
    OptionView updateOption(@PathVariable @Positive long id, @Valid @RequestBody OptionInput input) {
        return service.updateOption(id, input);
    }

    @DeleteMapping("/answer-options/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteOption(@PathVariable @Positive long id) {
        service.deleteOption(id);
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    ResponseEntity<ResultView> take(@PathVariable @Positive long quizId, @Valid @RequestBody AttemptInput input) {
        ResultView result = service.take(quizId, input);
        return Responses.created("quiz-results", result.id(), result);
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    List<ResultView> results(@PathVariable @Positive long quizId) {
        return service.results(quizId);
    }

    @GetMapping("/quiz-results/{id}")
    ResultView result(@PathVariable @Positive long id) {
        return service.result(id);
    }

    @GetMapping("/users/{studentId}/quiz-results")
    List<ResultView> studentResults(@PathVariable @Positive long studentId) {
        return service.studentResults(studentId);
    }
}
