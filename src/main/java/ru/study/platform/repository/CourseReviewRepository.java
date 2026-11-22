package ru.study.platform.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.CourseReview;

public interface CourseReviewRepository extends JpaRepository<CourseReview, Long> {

    boolean existsByStudentIdAndCourseId(long studentId, long courseId);

    @EntityGraph(attributePaths = "student")
    List<CourseReview> findByCourseIdOrderByCreatedAtDesc(long courseId);
}
