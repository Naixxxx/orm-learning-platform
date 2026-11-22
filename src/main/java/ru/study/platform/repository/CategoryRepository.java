package ru.study.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {}
