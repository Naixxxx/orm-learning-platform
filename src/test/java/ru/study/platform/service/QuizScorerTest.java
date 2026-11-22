package ru.study.platform.service;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.study.platform.api.dto.QuizDtos.Answer;
import ru.study.platform.domain.AnswerOption;
import ru.study.platform.domain.Question;
import ru.study.platform.domain.QuestionType;
import ru.study.platform.error.ApiException;

class QuizScorerTest {

    private final QuizScorer scorer = new QuizScorer();

    @Test
    void awardsFullPointsForCorrectSingleChoice() {
        var result = scorer.evaluate(List.of(question(1, 5, QuestionType.SINGLE_CHOICE)), List.of(answer(1, 11)), 60);
        assertThat(result).isEqualTo(new QuizScorer.Score(5, 5, true));
    }

    @Test
    void awardsZeroForWrongAnswer() {
        var result = scorer.evaluate(List.of(question(1, 5, QuestionType.SINGLE_CHOICE)), List.of(answer(1, 12)), 60);
        assertThat(result).isEqualTo(new QuizScorer.Score(0, 5, false));
    }

    @Test
    void countsSkippedQuestionAsIncorrect() {
        var result = scorer.evaluate(
                List.of(question(1, 5, QuestionType.SINGLE_CHOICE)), List.of(new Answer(1, Set.of())), 60);
        assertThat(result.earned()).isZero();
    }

    @Test
    void acceptsExactSetForMultipleChoiceAndRejectsPartialMatch() {
        Question question = question(1, 3, QuestionType.MULTIPLE_CHOICE);
        question.getOptions().get(1).setCorrect(true);
        assertThat(scorer.evaluate(List.of(question), List.of(answer(1, 11)), 60)
                        .earned())
                .isZero();
        assertThat(scorer.evaluate(List.of(question), List.of(new Answer(1, Set.of(11L, 12L))), 60)
                        .earned())
                .isEqualTo(3);
    }

    @Test
    void appliesWeightedScoringAndInclusivePassThreshold() {
        var questions = List.of(question(1, 3, QuestionType.SINGLE_CHOICE), question(2, 2, QuestionType.SINGLE_CHOICE));
        var result = scorer.evaluate(questions, List.of(answer(1, 11), answer(2, 22)), 60);
        assertThat(result).isEqualTo(new QuizScorer.Score(3, 5, true));
    }

    @Test
    void rejectsMissingOrForeignQuestion() {
        var question = question(1, 3, QuestionType.SINGLE_CHOICE);
        assertThatThrownBy(() -> scorer.evaluate(List.of(question), List.of(answer(2, 21)), 60))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("все вопросы");
    }

    @Test
    void rejectsDuplicateQuestion() {
        assertThatThrownBy(() -> scorer.evaluate(
                        List.of(question(1, 3, QuestionType.SINGLE_CHOICE)), List.of(answer(1, 11), answer(1, 12)), 60))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("несколько раз");
    }

    @Test
    void rejectsForeignAnswerOption() {
        assertThatThrownBy(() -> scorer.evaluate(
                        List.of(question(1, 3, QuestionType.SINGLE_CHOICE)), List.of(answer(1, 999)), 60))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("другого вопроса");
    }

    @Test
    void rejectsMultipleSelectionsForSingleChoice() {
        assertThatThrownBy(() -> scorer.evaluate(
                        List.of(question(1, 3, QuestionType.SINGLE_CHOICE)),
                        List.of(new Answer(1, Set.of(11L, 12L))),
                        60))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("одиночным выбором");
    }

    @Test
    void rejectsQuizWithNoQuestions() {
        assertThatThrownBy(() -> scorer.evaluate(List.of(), List.of(), 60))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("нет вопросов");
    }

    @Test
    void rejectsQuestionWithoutCorrectOption() {
        var question = question(1, 3, QuestionType.SINGLE_CHOICE);
        question.getOptions().forEach(option -> option.setCorrect(false));
        assertThatThrownBy(() -> scorer.evaluate(List.of(question), List.of(answer(1, 11)), 60))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Тест не готов");
    }

    @Test
    void rejectsSingleChoiceQuestionWithMoreThanOneCorrectOption() {
        var question = question(1, 3, QuestionType.SINGLE_CHOICE);
        question.getOptions().get(1).setCorrect(true);
        assertThatThrownBy(() -> scorer.evaluate(List.of(question), List.of(answer(1, 11)), 60))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Тест не готов");
    }

    private Answer answer(long questionId, long optionId) {
        return new Answer(questionId, Set.of(optionId));
    }

    private Question question(long id, int points, QuestionType type) {
        Question question = new Question();
        ReflectionTestUtils.setField(question, "id", id);
        question.setType(type);
        question.setPoints(points);
        for (int index = 1; index <= 2; index++) {
            AnswerOption option = new AnswerOption();
            ReflectionTestUtils.setField(option, "id", id * 10 + index);
            option.setCorrect(index == 1);
            question.addOption(option);
        }
        return question;
    }
}
