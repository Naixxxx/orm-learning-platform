package ru.study.platform.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.Submission;

public interface SubmissionRepository extends JpaRepository<Submission, Long> {

    boolean existsByStudentIdAndAssignmentId(long studentId, long assignmentId);

    boolean existsByAssignmentId(long assignmentId);

    @EntityGraph(attributePaths = {"student", "assignment"})
    List<Submission> findByStudentIdOrderBySubmittedAtDesc(long studentId);

    @EntityGraph(attributePaths = {"student", "assignment"})
    List<Submission> findByAssignmentIdOrderBySubmittedAtDesc(long assignmentId);
}
