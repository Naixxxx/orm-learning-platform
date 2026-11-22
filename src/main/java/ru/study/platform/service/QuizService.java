package ru.study.platform.service;

import static ru.study.platform.api.dto.QuizDtos.*;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.Account;
import ru.study.platform.domain.AnswerOption;
import ru.study.platform.domain.Course;
import ru.study.platform.domain.CourseModule;
import ru.study.platform.domain.Question;
import ru.study.platform.domain.Quiz;
import ru.study.platform.domain.QuizSubmission;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.AnswerOptionRepository;
import ru.study.platform.repository.QuestionRepository;
import ru.study.platform.repository.QuizRepository;
import ru.study.platform.repository.QuizSubmissionRepository;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {

    private final QuizRepository quizzes;
    private final QuestionRepository questions;
    private final AnswerOptionRepository options;
    private final QuizSubmissionRepository results;
    private final QuizScorer scorer;
    private final ContentService content;
    private final CatalogService catalog;
    private final AccountService accounts;
    private final AccessService access;
    private final Clock clock;

    public List<QuizView> list(long courseId) {
        access.studentOrTeacher(catalog.require(courseId));
        return quizzes.findByModuleCourseIdOrderById(courseId).stream()
                .map(this::view)
                .toList();
    }

    public QuizView get(long id) {
        Quiz quiz = require(id);
        access.studentOrTeacher(course(quiz));
        return view(quiz);
    }

    @Transactional
    public QuizView create(long moduleId, QuizInput input) {
        CourseModule module = content.requireModule(moduleId);
        access.teacher(module.getCourse());
        Quiz quiz = new Quiz();
        quiz.setModule(module);
        quiz.setTitle(input.title().strip());
        quiz.setPassPercent(input.passPercent());
        quizzes.saveAndFlush(quiz);
        return view(quiz);
    }

    @Transactional
    public QuizView update(long id, QuizInput input) {
        Quiz quiz = editable(id);
        quiz.setTitle(input.title().strip());
        quiz.setPassPercent(input.passPercent());
        return view(quiz);
    }

    @Transactional
    public void delete(long id) {
        Quiz quiz = editable(id);
        quizzes.delete(quiz);
        quizzes.flush();
    }

    @Transactional
    public QuestionView addQuestion(long quizId, QuestionInput input) {
        Quiz quiz = editable(quizId);
        Question question = new Question();
        question.setQuiz(quiz);
        apply(question, input);
        questions.saveAndFlush(question);
        return questionView(question, true);
    }

    public QuestionView question(long id) {
        Question question = requireQuestion(id);
        access.studentOrTeacher(course(question.getQuiz()));
        return questionView(question, access.manages(course(question.getQuiz())));
    }

    @Transactional
    public QuestionView updateQuestion(long id, QuestionInput input) {
        Question question = requireQuestion(id);
        editable(question.getQuiz().getId());
        apply(question, input);
        return questionView(question, true);
    }

    @Transactional
    public void deleteQuestion(long id) {
        Question question = requireQuestion(id);
        editable(question.getQuiz().getId());
        questions.delete(question);
        questions.flush();
    }

    public OptionView option(long id) {
        AnswerOption option = requireOption(id);
        Course course = course(option.getQuestion().getQuiz());
        access.studentOrTeacher(course);
        return optionView(option, access.manages(course));
    }

    @Transactional
    public OptionView addOption(long questionId, OptionInput input) {
        Question question = requireQuestion(questionId);
        editable(question.getQuiz().getId());
        AnswerOption option = new AnswerOption();
        option.setQuestion(question);
        apply(option, input);
        return optionView(options.saveAndFlush(option), true);
    }

    @Transactional
    public OptionView updateOption(long id, OptionInput input) {
        AnswerOption option = requireOption(id);
        editable(option.getQuestion().getQuiz().getId());
        apply(option, input);
        return optionView(option, true);
    }

    @Transactional
    public void deleteOption(long id) {
        AnswerOption option = requireOption(id);
        editable(option.getQuestion().getQuiz().getId());
        options.delete(option);
        options.flush();
    }

    @Transactional
    public ResultView take(long quizId, AttemptInput input) {
        Quiz quiz = quizzes.lockById(quizId).orElseThrow(() -> ApiException.notFound("Тест"));
        Account student = access.enrolledStudent(course(quiz));
        if (results.existsByStudentIdAndQuizId(student.getId(), quizId)) {
            throw ApiException.conflict("Тест уже пройден. Допускается одна попытка");
        }
        QuizScorer.Score score =
                scorer.evaluate(questions.findByQuizIdOrderByPosition(quizId), input.answers(), quiz.getPassPercent());
        QuizSubmission result = new QuizSubmission();
        result.setQuiz(quiz);
        result.setStudent(student);
        result.setScore(score.earned());
        result.setMaxScore(score.maximum());
        result.setPassed(score.passed());
        result.setTakenAt(clock.instant());
        results.saveAndFlush(result);
        log.info("Quiz taken quiz={} student={} score={}/{}", quizId, student.getId(), score.earned(), score.maximum());
        return ResultView.from(result);
    }

    public List<ResultView> results(long quizId) {
        access.teacher(course(require(quizId)));
        return results.findByQuizIdOrderByTakenAtDesc(quizId).stream()
                .map(ResultView::from)
                .toList();
    }

    public ResultView result(long id) {
        QuizSubmission result = results.findById(id).orElseThrow(() -> ApiException.notFound("Результат"));
        if (!result.getStudent().getId().equals(access.current().getId())
                && !access.manages(course(result.getQuiz()))) {
            throw ApiException.forbidden();
        }
        return ResultView.from(result);
    }

    public List<ResultView> studentResults(long studentId) {
        access.selfOrAdmin(studentId);
        accounts.require(studentId);
        return results.findByStudentIdOrderByTakenAtDesc(studentId).stream()
                .map(ResultView::from)
                .toList();
    }

    private Quiz editable(long id) {
        Quiz quiz = quizzes.lockById(id).orElseThrow(() -> ApiException.notFound("Тест"));
        access.teacher(course(quiz));
        if (results.existsByQuizId(id)) {
            throw ApiException.conflict("Нельзя редактировать тест после первой попытки");
        }
        return quiz;
    }

    private QuizView view(Quiz quiz) {
        boolean manager = access.manages(course(quiz));
        List<QuestionView> items = questions.findByQuizIdOrderByPosition(quiz.getId()).stream()
                .map(question -> questionView(question, manager))
                .toList();
        return new QuizView(quiz.getId(), quiz.getModule().getId(), quiz.getTitle(), quiz.getPassPercent(), items);
    }

    private QuestionView questionView(Question question, boolean manager) {
        return new QuestionView(
                question.getId(),
                question.getText(),
                question.getType(),
                question.getPosition(),
                question.getPoints(),
                question.getOptions().stream()
                        .map(option -> optionView(option, manager))
                        .toList());
    }

    private OptionView optionView(AnswerOption option, boolean manager) {
        return new OptionView(
                option.getId(), option.getText(), option.getPosition(), manager ? option.isCorrect() : null);
    }

    private Quiz require(long id) {
        return quizzes.findById(id).orElseThrow(() -> ApiException.notFound("Тест"));
    }

    private Question requireQuestion(long id) {
        return questions.findById(id).orElseThrow(() -> ApiException.notFound("Вопрос"));
    }

    private AnswerOption requireOption(long id) {
        return options.findById(id).orElseThrow(() -> ApiException.notFound("Вариант ответа"));
    }

    private Course course(Quiz quiz) {
        return quiz.getModule().getCourse();
    }

    private void apply(Question question, QuestionInput input) {
        question.setText(input.text());
        question.setType(input.type());
        question.setPosition(input.position());
        question.setPoints(input.points());
    }

    private void apply(AnswerOption option, OptionInput input) {
        option.setText(input.text());
        option.setCorrect(input.correct());
        option.setPosition(input.position());
    }
}
