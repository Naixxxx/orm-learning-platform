package ru.study.platform.service;

import static ru.study.platform.api.dto.LearningDtos.*;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.Account;
import ru.study.platform.domain.Course;
import ru.study.platform.domain.Enrollment;
import ru.study.platform.domain.EnrollmentStatus;
import ru.study.platform.domain.Role;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.EnrollmentRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository enrollments;
    private final CatalogService catalog;
    private final AccountService accounts;
    private final AccessService access;
    private final Clock clock;

    @Transactional
    public EnrollmentView enroll(long courseId, long studentId) {
        Course course = catalog.require(courseId);
        authorize(course, studentId);
        Account student = accounts.require(studentId);
        if (student.getRole() != Role.STUDENT) {
            throw ApiException.invalid("Записать на курс можно только студента");
        }
        if (enrollments.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw ApiException.conflict("Студент уже записан на курс");
        }
        Enrollment value = new Enrollment();
        value.setCourse(course);
        value.setStudent(student);
        value.setEnrolledAt(clock.instant());
        enrollments.saveAndFlush(value);
        log.info("Enrolled student={} course={}", studentId, courseId);
        return EnrollmentView.from(value);
    }

    public EnrollmentView get(long courseId, long studentId) {
        Course course = catalog.require(courseId);
        authorize(course, studentId);
        return EnrollmentView.from(require(courseId, studentId));
    }

    public List<EnrollmentView> students(long courseId) {
        access.teacher(catalog.require(courseId));
        return enrollments.findByCourseIdOrderByEnrolledAtDesc(courseId).stream()
                .map(EnrollmentView::from)
                .toList();
    }

    public List<EnrollmentView> courses(long studentId) {
        access.selfOrAdmin(studentId);
        accounts.require(studentId);
        return enrollments.findByStudentIdOrderByEnrolledAtDesc(studentId).stream()
                .map(EnrollmentView::from)
                .toList();
    }

    @Transactional
    public EnrollmentView update(long courseId, long studentId, EnrollmentStatus status) {
        access.teacher(catalog.require(courseId));
        Enrollment value = require(courseId, studentId);
        value.setStatus(status);
        return EnrollmentView.from(value);
    }

    @Transactional
    public void withdraw(long courseId, long studentId) {
        Course course = catalog.require(courseId);
        authorize(course, studentId);
        enrollments.delete(require(courseId, studentId));
    }

    private void authorize(Course course, long studentId) {
        if (access.current().getId() != studentId && !access.manages(course)) {
            throw ApiException.forbidden();
        }
    }

    private Enrollment require(long courseId, long studentId) {
        return enrollments
                .findByStudentIdAndCourseId(studentId, courseId)
                .orElseThrow(() -> ApiException.notFound("Запись на курс"));
    }
}
