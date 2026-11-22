package ru.study.platform.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.LessonProgress;

public interface LessonProgressRepository extends JpaRepository<LessonProgress, Long> {

    Optional<LessonProgress> findByStudentIdAndLessonId(long studentId, long lessonId);

    List<LessonProgress> findByStudentIdAndLessonModuleCourseId(long studentId, long courseId);

    long countByStudentIdAndLessonModuleCourseId(long studentId, long courseId);
}
