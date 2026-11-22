package ru.study.platform.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import ru.study.platform.api.dto.QuizDtos.Answer;
import ru.study.platform.domain.AnswerOption;
import ru.study.platform.domain.Question;
import ru.study.platform.domain.QuestionType;
import ru.study.platform.error.ApiException;

@Component
public class QuizScorer {

    public record Score(int earned, int maximum, boolean passed) {}

    public Score evaluate(List<Question> questions, List<Answer> answers, int passPercent) {
        if (questions.isEmpty()) {
            throw ApiException.conflict("В тесте нет вопросов");
        }
        Map<Long, Set<Long>> selected = new HashMap<>();
        for (Answer answer : answers) {
            if (selected.putIfAbsent(answer.questionId(), answer.optionIds()) != null) {
                throw ApiException.invalid("Один вопрос передан несколько раз");
            }
        }
        Set<Long> expectedQuestions = questions.stream().map(Question::getId).collect(Collectors.toSet());
        if (!expectedQuestions.equals(selected.keySet())) {
            throw ApiException.invalid("Необходимо передать ответы на все вопросы этого теста");
        }
        int earned = 0;
        int maximum = 0;
        for (Question question : questions) {
            Set<Long> correct = validateQuestion(question);
            Set<Long> submitted = selected.get(question.getId());
            Set<Long> options =
                    question.getOptions().stream().map(AnswerOption::getId).collect(Collectors.toSet());
            if (!options.containsAll(submitted)) {
                throw ApiException.invalid("Выбран вариант ответа от другого вопроса");
            }
            if (question.getType() == QuestionType.SINGLE_CHOICE && submitted.size() > 1) {
                throw ApiException.invalid("В вопросе с одиночным выбором допустим только один вариант");
            }
            maximum = Math.addExact(maximum, question.getPoints());
            if (correct.equals(submitted)) {
                earned = Math.addExact(earned, question.getPoints());
            }
        }
        return new Score(earned, maximum, earned * 100L >= maximum * (long) passPercent);
    }

    private Set<Long> validateQuestion(Question question) {
        Set<Long> correct = new HashSet<>();
        for (AnswerOption option : question.getOptions()) {
            if (option.isCorrect()) {
                correct.add(option.getId());
            }
        }
        if (question.getOptions().size() < 2
                || correct.isEmpty()
                || (question.getType() == QuestionType.SINGLE_CHOICE && correct.size() != 1)) {
            throw ApiException.conflict("Тест не готов: проверьте варианты и правильные ответы");
        }
        return correct;
    }
}
