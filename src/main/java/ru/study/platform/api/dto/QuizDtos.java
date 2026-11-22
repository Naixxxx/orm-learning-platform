package ru.study.platform.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import ru.study.platform.domain.QuestionType;
import ru.study.platform.domain.QuizSubmission;

public final class QuizDtos {

    private QuizDtos() {}

    public record QuizInput(@NotBlank @Size(max = 200) String title, @Min(0) @Max(100) int passPercent) {}

    public record QuestionInput(
            @NotBlank @Size(max = 4000) String text,
            @NotNull QuestionType type,
            @Positive int position,
            @Min(1) @Max(1000) int points) {}

    public record OptionInput(@NotBlank @Size(max = 2000) String text, boolean correct, @Positive int position) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OptionView(long id, String text, int position, Boolean correct) {}

    public record QuestionView(
            long id, String text, QuestionType type, int position, int points, List<OptionView> options) {}

    public record QuizView(long id, long moduleId, String title, int passPercent, List<QuestionView> questions) {}

    public record Answer(@Positive long questionId, @NotNull @Size(max = 100) Set<@NotNull @Positive Long> optionIds) {}

    public record AttemptInput(@NotEmpty @Size(max = 200) List<@Valid Answer> answers) {}

    public record ResultView(
            long id,
            long quizId,
            String quizTitle,
            long studentId,
            String studentName,
            int score,
            int maxScore,
            boolean passed,
            Instant takenAt) {

        public static ResultView from(QuizSubmission value) {
            return new ResultView(
                    value.getId(),
                    value.getQuiz().getId(),
                    value.getQuiz().getTitle(),
                    value.getStudent().getId(),
                    value.getStudent().getName(),
                    value.getScore(),
                    value.getMaxScore(),
                    value.isPassed(),
                    value.getTakenAt());
        }
    }
}
