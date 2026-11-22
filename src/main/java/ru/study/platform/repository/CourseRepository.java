package ru.study.platform.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.study.platform.domain.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {

    @EntityGraph(attributePaths = {"teacher", "category"})
    @Query(
            """
            select c from Course c where
            (:categoryId is null or c.category.id = :categoryId)
            and (:tagId is null or exists (select t.id from c.tags t where t.id = :tagId))
            and lower(c.title) like :search escape '!'
            """)
    Page<Course> search(
            @Param("categoryId") Long categoryId,
            @Param("tagId") Long tagId,
            @Param("search") String search,
            Pageable pageable);

    @EntityGraph(attributePaths = {"teacher", "category", "tags"})
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findDetailedById(@Param("id") long id);

    @Query("select distinct c from Course c left join fetch c.modules where c.id = :id")
    Optional<Course> findWithModulesById(@Param("id") long id);
}
