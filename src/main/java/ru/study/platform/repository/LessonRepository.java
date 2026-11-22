package ru.study.platform.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.Lesson;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByModuleIdOrderByPosition(long moduleId);

    List<Lesson> findByModuleCourseIdOrderByPosition(long courseId);

    long countByModuleCourseId(long courseId);
}
