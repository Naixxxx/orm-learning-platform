package ru.study.platform.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.study.platform.domain.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    Optional<Profile> findByAccountId(long accountId);
}
