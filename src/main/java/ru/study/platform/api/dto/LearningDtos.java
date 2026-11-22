package ru.study.platform.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.hibernate.validator.constraints.URL;
import ru.study.platform.domain.Assignment;
import ru.study.platform.domain.CourseReview;
import ru.study.platform.domain.Enrollment;
import ru.study.platform.domain.EnrollmentStatus;
import ru.study.platform.domain.LessonMaterial;
import ru.study.platform.domain.ScheduledLesson;
import ru.study.platform.domain.Submission;

public final class LearningDtos {

    private LearningDtos() {}

    public record EnrollmentInput(@Positive long studentId) {}

    public record EnrollmentUpdate(@NotNull EnrollmentStatus status) {}

    public record EnrollmentView(
            long id,
            long courseId,
            String courseTitle,
            long studentId,
            String studentName,
            Instant enrolledAt,
            EnrollmentStatus status) {

        public static EnrollmentView from(Enrollment value) {
            return new EnrollmentView(
                    value.getId(),
                    value.getCourse().getId(),
                    value.getCourse().getTitle(),
                    value.getStudent().getId(),
                    value.getStudent().getName(),
                    value.getEnrolledAt(),
                    value.getStatus());
        }
    }

    public record AssignmentInput(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 10000) String description,
            Instant dueAt,
            @Min(1) @Max(10000) int maxScore) {}

    public record AssignmentView(
            long id, long lessonId, String title, String description, Instant dueAt, int maxScore) {

        public static AssignmentView from(Assignment value) {
            return new AssignmentView(
                    value.getId(),
                    value.getLesson().getId(),
                    value.getTitle(),
                    value.getDescription(),
                    value.getDueAt(),
                    value.getMaxScore());
        }
    }

    public record WorkInput(@NotBlank @Size(max = 30000) String content) {}

    public record GradeInput(@NotNull @Min(0) Integer score, @Size(max = 4000) String feedback) {}

    public record SubmissionView(
            long id,
            long assignmentId,
            String assignmentTitle,
            long studentId,
            String studentName,
            String content,
            Instant submittedAt,
            Integer score,
            String feedback) {

        public static SubmissionView from(Submission value) {
            return new SubmissionView(
                    value.getId(),
                    value.getAssignment().getId(),
                    value.getAssignment().getTitle(),
                    value.getStudent().getId(),
                    value.getStudent().getName(),
                    value.getContent(),
                    value.getSubmittedAt(),
                    value.getScore(),
                    value.getFeedback());
        }
    }

    public record ReviewInput(@Min(1) @Max(5) int rating, @NotBlank @Size(max = 4000) String comment) {}

    public record ReviewView(long id, long courseId, long studentId, String studentName, int rating, String comment) {

        public static ReviewView from(CourseReview value) {
            return new ReviewView(
                    value.getId(),
                    value.getCourse().getId(),
                    value.getStudent().getId(),
                    value.getStudent().getName(),
                    value.getRating(),
                    value.getComment());
        }
    }

    public record MaterialInput(
            @NotBlank @Size(max = 200) String title, @NotBlank @URL(protocol = "https") @Size(max = 1000) String url) {}

    public record MaterialView(long id, long lessonId, String title, String url) {

        public static MaterialView from(LessonMaterial value) {
            return new MaterialView(value.getId(), value.getLesson().getId(), value.getTitle(), value.getUrl());
        }
    }

    public record ScheduleInput(
            @NotNull Instant startsAt,
            @NotNull Instant endsAt,
            @NotBlank @URL(protocol = "https") @Size(max = 1000) String meetingUrl) {}

    public record ScheduleView(
            long id, long lessonId, String lessonTitle, Instant startsAt, Instant endsAt, String meetingUrl) {

        public static ScheduleView from(ScheduledLesson value) {
            return new ScheduleView(
                    value.getId(),
                    value.getLesson().getId(),
                    value.getLesson().getTitle(),
                    value.getStartsAt(),
                    value.getEndsAt(),
                    value.getMeetingUrl());
        }
    }

    public record ProgressView(long lessonId, Instant completedAt) {}

    public record CourseStatistics(
            long courseId, long students, long lessons, long completedLessons, double progress) {}
}
