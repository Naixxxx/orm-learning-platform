package ru.study.platform.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.ScheduledLesson;

public interface ScheduledLessonRepository extends JpaRepository<ScheduledLesson, Long> {

    @EntityGraph(attributePaths = "lesson")
    List<ScheduledLesson> findByLessonModuleCourseIdOrderByStartsAt(long courseId);
}
