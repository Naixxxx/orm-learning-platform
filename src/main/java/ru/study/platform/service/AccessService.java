package ru.study.platform.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.Account;
import ru.study.platform.domain.Course;
import ru.study.platform.domain.Role;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.AccountRepository;
import ru.study.platform.repository.EnrollmentRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessService {

    private final AccountRepository accounts;
    private final EnrollmentRepository enrollments;

    public Account current() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Требуется авторизация");
        }
        try {
            return accounts.findById(Long.parseLong(token.getToken().getSubject()))
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Пользователь больше не существует"));
        } catch (NumberFormatException exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Некорректный токен");
        }
    }

    public void admin() {
        role(Role.ADMIN);
    }

    public Account role(Role role) {
        Account actor = current();
        if (actor.getRole() != role) {
            throw ApiException.forbidden();
        }
        return actor;
    }

    public void selfOrAdmin(long accountId) {
        Account actor = current();
        if (actor.getRole() != Role.ADMIN && actor.getId() != accountId) {
            throw ApiException.forbidden();
        }
    }

    public boolean manages(Course course) {
        Account actor = current();
        return actor.getRole() == Role.ADMIN
                || (actor.getRole() == Role.TEACHER
                        && actor.getId().equals(course.getTeacher().getId()));
    }

    public void teacher(Course course) {
        if (!manages(course)) {
            throw ApiException.forbidden();
        }
    }

    public void studentOrTeacher(Course course) {
        Account actor = current();
        if (!manages(course) && !enrollments.existsByStudentIdAndCourseId(actor.getId(), course.getId())) {
            throw ApiException.forbidden();
        }
    }

    public Account enrolledStudent(Course course) {
        Account actor = role(Role.STUDENT);
        if (!enrollments.existsByStudentIdAndCourseId(actor.getId(), course.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Сначала запишитесь на курс");
        }
        return actor;
    }
}
