package ru.study.platform.service;

import static ru.study.platform.api.dto.LearningDtos.*;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.Account;
import ru.study.platform.domain.Course;
import ru.study.platform.domain.CourseReview;
import ru.study.platform.domain.Lesson;
import ru.study.platform.domain.LessonProgress;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.CourseReviewRepository;
import ru.study.platform.repository.EnrollmentRepository;
import ru.study.platform.repository.LessonProgressRepository;
import ru.study.platform.repository.LessonRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final CourseReviewRepository reviews;
    private final LessonProgressRepository progress;
    private final LessonRepository lessons;
    private final EnrollmentRepository enrollments;
    private final CatalogService catalog;
    private final ContentService content;
    private final AccessService access;
    private final Clock clock;

    public List<ReviewView> reviews(long courseId) {
        catalog.require(courseId);
        return reviews.findByCourseIdOrderByCreatedAtDesc(courseId).stream()
                .map(ReviewView::from)
                .toList();
    }

    public ReviewView review(long id) {
        return ReviewView.from(requireReview(id));
    }

    @Transactional
    public ReviewView createReview(long courseId, ReviewInput input) {
        Course course = catalog.require(courseId);
        Account student = access.enrolledStudent(course);
        if (reviews.existsByStudentIdAndCourseId(student.getId(), courseId)) {
            throw ApiException.conflict("Отзыв на этот курс уже оставлен");
        }
        CourseReview value = new CourseReview();
        value.setCourse(course);
        value.setStudent(student);
        value.setCreatedAt(clock.instant());
        value.setRating(input.rating());
        value.setComment(input.comment());
        return ReviewView.from(reviews.saveAndFlush(value));
    }

    @Transactional
    public ReviewView updateReview(long id, ReviewInput input) {
        CourseReview value = requireReview(id);
        access.selfOrAdmin(value.getStudent().getId());
        value.setRating(input.rating());
        value.setComment(input.comment());
        return ReviewView.from(value);
    }

    @Transactional
    public void deleteReview(long id) {
        CourseReview value = requireReview(id);
        access.selfOrAdmin(value.getStudent().getId());
        reviews.delete(value);
    }

    @Transactional
    public ProgressView complete(long lessonId) {
        Lesson lesson = content.requireLesson(lessonId);
        Account student = access.enrolledStudent(lesson.getModule().getCourse());
        LessonProgress value = progress.findByStudentIdAndLessonId(student.getId(), lessonId)
                .orElseGet(() -> {
                    LessonProgress created = new LessonProgress();
                    created.setStudent(student);
                    created.setLesson(lesson);
                    created.setCompletedAt(clock.instant().truncatedTo(ChronoUnit.MICROS));
                    return progress.saveAndFlush(created);
                });
        return new ProgressView(lessonId, value.getCompletedAt());
    }

    @Transactional
    public void reset(long lessonId) {
        Account student = access.enrolledStudent(
                content.requireLesson(lessonId).getModule().getCourse());
        progress.findByStudentIdAndLessonId(student.getId(), lessonId).ifPresent(progress::delete);
    }

    public List<ProgressView> progress(long courseId, long studentId) {
        Course course = catalog.require(courseId);
        if (access.current().getId() != studentId && !access.manages(course)) {
            throw ApiException.forbidden();
        }
        return progress.findByStudentIdAndLessonModuleCourseId(studentId, courseId).stream()
                .map(value -> new ProgressView(value.getLesson().getId(), value.getCompletedAt()))
                .toList();
    }

    public CourseStatistics statistics(long courseId, Long studentId) {
        Course course = catalog.require(courseId);
        if (studentId == null) {
            access.teacher(course);
            return new CourseStatistics(
                    courseId, enrollments.countByCourseId(courseId), lessons.countByModuleCourseId(courseId), 0, 0);
        }
        if (access.current().getId() != studentId && !access.manages(course)) {
            throw ApiException.forbidden();
        }
        long total = lessons.countByModuleCourseId(courseId);
        long completed = progress.countByStudentIdAndLessonModuleCourseId(studentId, courseId);
        return new CourseStatistics(
                courseId,
                enrollments.countByCourseId(courseId),
                total,
                completed,
                total == 0 ? 0 : Math.round(1000.0 * completed / total) / 10.0);
    }

    private CourseReview requireReview(long id) {
        return reviews.findById(id).orElseThrow(() -> ApiException.notFound("Отзыв"));
    }
}
