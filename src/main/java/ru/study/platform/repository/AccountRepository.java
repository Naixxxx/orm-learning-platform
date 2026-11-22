package ru.study.platform.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.Account;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);
}
