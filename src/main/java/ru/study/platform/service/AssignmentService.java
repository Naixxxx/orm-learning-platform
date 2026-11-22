package ru.study.platform.service;

import static ru.study.platform.api.dto.LearningDtos.*;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.Account;
import ru.study.platform.domain.Assignment;
import ru.study.platform.domain.Course;
import ru.study.platform.domain.Lesson;
import ru.study.platform.domain.Submission;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.AssignmentRepository;
import ru.study.platform.repository.SubmissionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    private final AssignmentRepository assignments;
    private final SubmissionRepository submissions;
    private final ContentService content;
    private final AccessService access;
    private final AccountService accounts;
    private final Clock clock;

    public List<AssignmentView> list(long lessonId) {
        access.studentOrTeacher(content.requireLesson(lessonId).getModule().getCourse());
        return assignments.findByLessonIdOrderById(lessonId).stream()
                .map(AssignmentView::from)
                .toList();
    }

    public AssignmentView get(long id) {
        Assignment assignment = require(id);
        access.studentOrTeacher(course(assignment));
        return AssignmentView.from(assignment);
    }

    @Transactional
    public AssignmentView create(long lessonId, AssignmentInput input) {
        Lesson lesson = content.requireLesson(lessonId);
        access.teacher(lesson.getModule().getCourse());
        Assignment assignment = new Assignment();
        assignment.setLesson(lesson);
        apply(assignment, input);
        return AssignmentView.from(assignments.save(assignment));
    }

    @Transactional
    public AssignmentView update(long id, AssignmentInput input) {
        Assignment assignment = require(id);
        access.teacher(course(assignment));
        if (submissions.existsByAssignmentId(id)) {
            throw ApiException.conflict("Нельзя менять условия задания после получения решений");
        }
        apply(assignment, input);
        return AssignmentView.from(assignment);
    }

    @Transactional
    public void delete(long id) {
        Assignment assignment = require(id);
        access.teacher(course(assignment));
        assignments.delete(assignment);
        assignments.flush();
    }

    @Transactional
    public SubmissionView submit(long assignmentId, WorkInput input) {
        Assignment assignment = require(assignmentId);
        Account student = access.enrolledStudent(course(assignment));
        if (assignment.getDueAt() != null && !clock.instant().isBefore(assignment.getDueAt())) {
            throw ApiException.conflict("Срок сдачи задания истёк");
        }
        if (submissions.existsByStudentIdAndAssignmentId(student.getId(), assignmentId)) {
            throw ApiException.conflict("Решение этого задания уже отправлено");
        }
        Submission submission = new Submission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setContent(input.content());
        submission.setSubmittedAt(clock.instant());
        submissions.saveAndFlush(submission);
        log.info("Submitted assignment={} student={}", assignmentId, student.getId());
        return SubmissionView.from(submission);
    }

    public List<SubmissionView> submissions(long assignmentId) {
        access.teacher(course(require(assignmentId)));
        return submissions.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId).stream()
                .map(SubmissionView::from)
                .toList();
    }

    public SubmissionView submission(long id) {
        Submission submission = requireSubmission(id);
        if (!submission.getStudent().getId().equals(access.current().getId())
                && !access.manages(course(submission.getAssignment()))) {
            throw ApiException.forbidden();
        }
        return SubmissionView.from(submission);
    }

    public List<SubmissionView> studentSubmissions(long studentId) {
        access.selfOrAdmin(studentId);
        accounts.require(studentId);
        return submissions.findByStudentIdOrderBySubmittedAtDesc(studentId).stream()
                .map(SubmissionView::from)
                .toList();
    }

    @Transactional
    public SubmissionView grade(long id, GradeInput input) {
        Submission submission = requireSubmission(id);
        access.teacher(course(submission.getAssignment()));
        if (input.score() > submission.getAssignment().getMaxScore()) {
            throw ApiException.invalid("Оценка превышает максимальный балл задания");
        }
        submission.setScore(input.score());
        submission.setFeedback(input.feedback());
        log.info("Graded submission={} score={}", id, input.score());
        return SubmissionView.from(submission);
    }

    private Assignment require(long id) {
        return assignments.findById(id).orElseThrow(() -> ApiException.notFound("Задание"));
    }

    private Submission requireSubmission(long id) {
        return submissions.findById(id).orElseThrow(() -> ApiException.notFound("Решение"));
    }

    private Course course(Assignment assignment) {
        return assignment.getLesson().getModule().getCourse();
    }

    private void apply(Assignment assignment, AssignmentInput input) {
        assignment.setTitle(input.title().strip());
        assignment.setDescription(input.description());
        assignment.setDueAt(input.dueAt());
        assignment.setMaxScore(input.maxScore());
    }
}
