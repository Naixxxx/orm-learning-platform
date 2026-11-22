package ru.study.platform.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.QuizSubmission;

public interface QuizSubmissionRepository extends JpaRepository<QuizSubmission, Long> {

    boolean existsByQuizId(long quizId);

    boolean existsByStudentIdAndQuizId(long studentId, long quizId);

    @EntityGraph(attributePaths = {"student", "quiz"})
    List<QuizSubmission> findByStudentIdOrderByTakenAtDesc(long studentId);

    @EntityGraph(attributePaths = {"student", "quiz"})
    List<QuizSubmission> findByQuizIdOrderByTakenAtDesc(long quizId);
}
