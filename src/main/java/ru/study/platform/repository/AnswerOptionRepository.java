package ru.study.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.AnswerOption;

public interface AnswerOptionRepository extends JpaRepository<AnswerOption, Long> {}
