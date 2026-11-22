package ru.study.platform.api;

import static ru.study.platform.api.dto.AccountDtos.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.study.platform.api.dto.CatalogDtos.PageView;
import ru.study.platform.service.AccountService;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
public class AccountController {

    private final AccountService service;

    @PostMapping("/auth/register")
    ResponseEntity<AccountView> register(@Valid @RequestBody Registration input) {
        AccountView result = service.register(input);
        return Responses.created("users", result.id(), result);
    }

    @PostMapping("/auth/login")
    Token login(@Valid @RequestBody Login input) {
        return service.login(input);
    }

    @GetMapping("/auth/me")
    AccountView me() {
        return service.me();
    }

    @GetMapping("/users")
    PageView<AccountView> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(page, size);
    }

    @GetMapping("/users/{id}")
    AccountView get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PostMapping("/users")
    ResponseEntity<AccountView> create(@Valid @RequestBody CreateAccount input) {
        AccountView result = service.createByAdmin(input);
        return Responses.created("users", result.id(), result);
    }

    @PutMapping("/users/{id}")
    AccountView update(@PathVariable @Positive long id, @Valid @RequestBody UpdateAccount input) {
        return service.update(id, input);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable @Positive long id) {
        service.delete(id);
    }

    @GetMapping("/users/{id}/profile")
    ProfileView profile(@PathVariable @Positive long id) {
        return service.profile(id);
    }

    @PutMapping("/users/{id}/profile")
    ProfileView updateProfile(@PathVariable @Positive long id, @Valid @RequestBody ProfileInput input) {
        return service.updateProfile(id, input);
    }
}
