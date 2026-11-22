package ru.study.platform.service;

import static ru.study.platform.api.dto.AccountDtos.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.api.dto.CatalogDtos.PageView;
import ru.study.platform.config.SecurityConfiguration;
import ru.study.platform.domain.Account;
import ru.study.platform.domain.Profile;
import ru.study.platform.domain.Role;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.AccountRepository;
import ru.study.platform.repository.ProfileRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accounts;
    private final ProfileRepository profiles;
    private final PasswordEncoder passwords;
    private final JwtEncoder tokens;
    private final AccessService access;
    private final Clock clock;

    @Value("${app.jwt.lifetime}")
    private Duration tokenLifetime;

    @Transactional
    public AccountView register(Registration input) {
        return AccountView.from(create(input.name(), input.email(), input.password(), Role.STUDENT));
    }

    public Token login(Login input) {
        Account account = accounts.findByEmail(normalize(input.email())).orElse(null);
        if (account == null
                || input.password().getBytes(StandardCharsets.UTF_8).length > 72
                || !passwords.matches(input.password(), account.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Неверная почта или пароль");
        }
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfiguration.ISSUER)
                .subject(account.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(tokenLifetime))
                .build();
        String token = tokens.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new Token(token, "Bearer", tokenLifetime.toSeconds(), AccountView.from(account));
    }

    public AccountView me() {
        return AccountView.from(access.current());
    }

    public PageView<AccountView> list(int page, int size) {
        access.admin();
        return PageView.from(
                accounts.findAll(PageRequest.of(page, size, Sort.by("id"))).map(AccountView::from));
    }

    public AccountView get(long id) {
        access.selfOrAdmin(id);
        return AccountView.from(require(id));
    }

    @Transactional
    public AccountView createByAdmin(CreateAccount input) {
        access.admin();
        return AccountView.from(create(input.name(), input.email(), input.password(), input.role()));
    }

    @Transactional
    public AccountView update(long id, UpdateAccount input) {
        access.selfOrAdmin(id);
        Account account = require(id);
        String email = normalize(input.email());
        accounts.findByEmail(email)
                .filter(found -> !found.getId().equals(account.getId()))
                .ifPresent(found -> {
                    throw ApiException.conflict("Пользователь с такой почтой уже существует");
                });
        account.setName(input.name().strip());
        account.setEmail(email);
        return AccountView.from(account);
    }

    @Transactional
    public void delete(long id) {
        access.admin();
        if (access.current().getId() == id) {
            throw ApiException.conflict("Нельзя удалить собственную учётную запись администратора");
        }
        accounts.delete(require(id));
        accounts.flush();
    }

    public ProfileView profile(long accountId) {
        access.selfOrAdmin(accountId);
        require(accountId);
        return ProfileView.from(
                profiles.findByAccountId(accountId).orElseThrow(() -> ApiException.notFound("Профиль")));
    }

    @Transactional
    public ProfileView updateProfile(long accountId, ProfileInput input) {
        access.selfOrAdmin(accountId);
        Profile profile = profiles.findByAccountId(accountId).orElseThrow(() -> ApiException.notFound("Профиль"));
        profile.setBiography(input.biography());
        profile.setAvatarUrl(input.avatarUrl());
        return ProfileView.from(profile);
    }

    public Account require(long id) {
        return accounts.findById(id).orElseThrow(() -> ApiException.notFound("Пользователь"));
    }

    private Account create(String name, String email, String password, Role role) {
        String normalized = normalize(email);
        if (accounts.existsByEmail(normalized)) {
            throw ApiException.conflict("Пользователь с такой почтой уже существует");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw ApiException.invalid("Пароль должен занимать не более 72 байт UTF-8");
        }
        Account account = accounts.save(new Account(name.strip(), normalized, passwords.encode(password), role));
        profiles.save(new Profile(account));
        log.info("Created account id={} role={}", account.getId(), role);
        return account;
    }

    private String normalize(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
