package ru.study.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {}
