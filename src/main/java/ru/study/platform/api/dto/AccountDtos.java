package ru.study.platform.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;
import ru.study.platform.domain.Account;
import ru.study.platform.domain.Profile;
import ru.study.platform.domain.Role;

public final class AccountDtos {

    private AccountDtos() {}

    public record Registration(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password) {}

    public record Login(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

    public record CreateAccount(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 10, max = 72) String password,
            @NotNull Role role) {}

    public record UpdateAccount(
            @NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 254) String email) {}

    public record AccountView(long id, String name, String email, Role role) {

        public static AccountView from(Account account) {
            return new AccountView(account.getId(), account.getName(), account.getEmail(), account.getRole());
        }
    }

    public record ProfileInput(
            @Size(max = 2000) String biography, @URL(protocol = "https") @Size(max = 1000) String avatarUrl) {}

    public record ProfileView(long id, long accountId, String biography, String avatarUrl) {

        public static ProfileView from(Profile profile) {
            return new ProfileView(
                    profile.getId(), profile.getAccount().getId(), profile.getBiography(), profile.getAvatarUrl());
        }
    }

    public record Token(String accessToken, String tokenType, long expiresIn, AccountView user) {}
}
