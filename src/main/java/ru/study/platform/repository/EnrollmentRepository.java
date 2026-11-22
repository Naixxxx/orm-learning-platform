package ru.study.platform.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.Enrollment;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    Optional<Enrollment> findByStudentIdAndCourseId(long studentId, long courseId);

    boolean existsByStudentIdAndCourseId(long studentId, long courseId);

    @EntityGraph(attributePaths = {"student", "course"})
    List<Enrollment> findByStudentIdOrderByEnrolledAtDesc(long studentId);

    @EntityGraph(attributePaths = {"student", "course"})
    List<Enrollment> findByCourseIdOrderByEnrolledAtDesc(long courseId);

    long countByCourseId(long courseId);
}
