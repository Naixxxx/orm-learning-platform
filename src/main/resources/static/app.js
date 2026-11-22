(() => {
    "use strict";

    const main = document.querySelector("#main");
    const modal = document.querySelector("#modal");
    const state = { token: null, user: null, view: "catalog", course: null, tab: "curriculum", page: 0 };
    const escape = value => String(value ?? "").replace(/[&<>"']/g, c => ({
        "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
    })[c]);
    const date = value => value ? new Date(value).toLocaleString("ru-RU") : "Без срока";
    const manager = () => state.user && ["TEACHER", "ADMIN"].includes(state.user.role);
    const owns = course => state.user && (state.user.role === "ADMIN" || course.teacherId === state.user.id);
    const empty = message => `<div class="empty">${escape(message)}</div>`;
    const button = (label, action, id = "", style = "secondary") =>
        `<button type="button" class="button ${style}" data-action="${action}" data-id="${id}">${label}</button>`;
    const title = (heading, subtitle = "") =>
        `<div class="eyebrow">ПРАКТИКУМ / ОНЛАЙН-ОБУЧЕНИЕ</div><h1>${escape(heading)}</h1><p class="muted">${escape(subtitle)}</p>`;

    async function api(path, method = "GET", body) {
        const headers = { Accept: "application/json" };
        if (state.token) headers.Authorization = `Bearer ${state.token}`;
        if (body !== undefined) headers["Content-Type"] = "application/json";
        const response = await fetch(`/api${path}`, {
            method, headers, body: body === undefined ? undefined : JSON.stringify(body)
        });
        if (response.status === 204) return null;
        const data = await response.json();
        if (!response.ok) {
            if (response.status === 401 && state.token) {
                state.token = null;
                state.user = null;
                renderAccount();
            }
            const fields = Object.entries(data.fields || {}).map(([key, value]) => `${key}: ${value}`).join("\n");
            throw new Error(fields || data.detail || `Ошибка ${response.status}`);
        }
        return data;
    }

    let toastTimer;

    function toast(message) {
        const element = document.querySelector("#toast");
        element.textContent = message;
        element.classList.add("visible");
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => element.classList.remove("visible"), 4500);
    }

    function renderAccount() {
        document.querySelector("#account").innerHTML = state.user
            ? `<span class="avatar">${escape(state.user.name[0])}</span><span>${escape(state.user.name)}<br><small>${escape(state.user.role)}</small></span>${button("Выйти", "logout", "", "secondary small")}`
            : button("Войти в аккаунт ↗", "login", "", "small");
    }

    function field(name, label, type = "text", value = "", options = null, required = true) {
        return { name, label, type, value, options, required };
    }

    function openForm(heading, fields, submit, note = "", submitText = "Сохранить") {
        document.querySelector("#modal-title").textContent = heading;
        document.querySelector("#modal-body").innerHTML = `
            <form id="editor-form">
                ${fields.map(f => {
                    let input;
                    if (f.options) {
                        input = `<select name="${f.name}">${f.options.map(o => `<option value="${escape(o.id)}" ${String(o.id) === String(f.value) ? "selected" : ""}>${escape(o.name)}</option>`).join("")}</select>`;
                    } else if (f.type === "textarea") {
                        input = `<textarea name="${f.name}" ${f.required ? "required" : ""}>${escape(f.value)}</textarea>`;
                    } else {
                        input = `<input name="${f.name}" type="${f.type}" value="${escape(f.value)}" ${f.type === "number" ? 'min="0" step="1"' : ""} ${f.required && f.type !== "date" && f.type !== "datetime-local" ? "required" : ""} ${f.type === "password" ? 'minlength="10" maxlength="72" autocomplete="current-password"' : ""}>`;
                    }
                    return `<label>${escape(f.label)}${input}</label>`;
                }).join("")}
                ${note ? `<p class="hint">${escape(note)}</p>` : ""}
                <div class="form-error" role="alert"></div>
                <button class="button" type="submit">${escape(submitText)}</button>
            </form>`;
        document.querySelector("#editor-form").addEventListener("submit", async event => {
            event.preventDefault();
            const form = event.currentTarget;
            const submitButton = form.querySelector('[type="submit"]');
            submitButton.disabled = true;
            try {
                const values = Object.fromEntries(new FormData(form));
                fields.filter(f => f.type === "number").forEach(f => values[f.name] = Number(values[f.name]));
                await submit(values);
                modal.close();
            } catch (error) {
                form.querySelector(".form-error").textContent = error.message;
            } finally {
                submitButton.disabled = false;
            }
        });
        if (!modal.open) modal.showModal();
    }

    function login(register = false) {
        const fields = [
            ...(register ? [field("name", "Ваше имя")] : []),
            field("email", "Электронная почта", "email"),
            field("password", "Пароль", "password")
        ];
        openForm(register ? "Создать аккаунт" : "С возвращением!", fields, async values => {
            if (register) await api("/auth/register", "POST", values);
            const result = await api("/auth/login", "POST", { email: values.email, password: values.password });
            state.token = result.accessToken;
            state.user = result.user;
            renderAccount();
            toast(`Здравствуйте, ${state.user.name}!`);
            await navigate("catalog");
        }, "Демо-аккаунты: student@learning.local, teacher@learning.local, admin@learning.local. Пароль задан в DEMO_PASSWORD.", register ? "Зарегистрироваться" : "Войти");
        if (!register) {
            const link = document.createElement("button");
            link.className = "button secondary";
            link.style.marginTop = "12px";
            link.textContent = "Создать аккаунт студента";
            link.addEventListener("click", () => login(true));
            document.querySelector("#modal-body").append(link);
        }
    }

    function requireLogin() {
        if (state.user) return true;
        login();
        return false;
    }

    async function navigate(view) {
        if (view !== "catalog" && !requireLogin()) return;
        state.view = view;
        document.querySelectorAll("[data-nav]").forEach(item => item.classList.toggle("active", item.dataset.nav === view));
        document.querySelector("#breadcrumb").textContent = ({
            catalog: "Каталог курсов", my: "Моё обучение", results: "Работы и результаты",
            editor: "Управление", profile: "Мой профиль"
        })[view] || "Курс";
        main.innerHTML = empty("Загружаем данные…");
        try {
            if (view === "catalog") await catalog();
            else if (view === "my") await myLearning();
            else if (view === "results") await results();
            else if (view === "editor") await editor();
            else if (view === "profile") await profile();
        } catch (error) {
            main.innerHTML = empty(error.message);
        }
    }

    function courseCard(course) {
        return `<article class="course-card">
            <div class="course-art"><span>{ orm }</span><code>@Entity\nclass YourFuture\n    extends Knowledge</code></div>
            <div class="course-body"><span class="badge">${escape(course.category)}</span><h3>${escape(course.title)}</h3>
            <p>${escape(course.description.slice(0, 135))}${course.description.length > 135 ? "…" : ""}</p>
            <div class="course-meta"><span>◉ ${escape(course.teacher)}</span><span>Онлайн</span></div>
            <div class="course-bottom"><span class="price">Учебный курс</span>${button("Открыть ↗", "course", course.id, "small")}</div></div>
        </article>`;
    }

    async function catalog(search = "", categoryId = "") {
        const query = new URLSearchParams({ search, page: state.page, size: 9 });
        if (categoryId) query.set("categoryId", categoryId);
        const [page, categories] = await Promise.all([api(`/courses?${query}`), api("/categories")]);
        main.innerHTML = `${title("Знания, которые работают", "От первого знакомства с ORM до уверенной работы с данными.")}
            <section class="hero"><div class="eyebrow">ТЕОРИЯ СТАНОВИТСЯ ПРАКТИКОЙ</div>
                <h2>За каждой таблицей —<br>новая возможность.</h2>
                <p>Изучайте Hibernate, создавайте связи и управляйте данными.<br>Всё необходимое для вашего следующего шага в Java.</p>
                <div class="hero-bottom">${button("Выбрать курс ↓", "scroll-courses", "", "light")}<span>Практические задания</span><span>Проверка знаний</span></div>
                <div class="hero-art" aria-hidden="true"><div class="data-block">COURSE</div><div class="data-block">STUDENT</div><div class="data-block">YOUR SKILLS</div></div>
            </section>
            <div class="section-heading" id="courses-heading"><div><h2>Откройте свой следующий курс</h2><p>${page.totalElements} курсов · учитесь в своём темпе</p></div><span class="badge green">Доступно онлайн</span></div>
            <form class="filters" id="filters"><input name="search" aria-label="Поиск курсов" placeholder="Найти курс по названию…" value="${escape(search)}">
            <select name="category" aria-label="Категория"><option value="">Все направления</option>${categories.map(c => `<option value="${c.id}" ${String(c.id) === String(categoryId) ? "selected" : ""}>${escape(c.name)}</option>`).join("")}</select><button class="button">Найти</button></form>
            ${page.content.length ? `<div class="grid">${page.content.map(courseCard).join("")}</div>` : empty("Курсы не найдены. Попробуйте другой запрос.")}
            <div class="actions">${state.page > 0 ? button("← Назад", "prev-page") : ""}${state.page + 1 < page.totalPages ? button("Следующая страница →", "next-page") : ""}</div>`;
        document.querySelector("#filters").addEventListener("submit", async event => {
            event.preventDefault();
            state.page = 0;
            const data = new FormData(event.currentTarget);
            state.search = data.get("search");
            state.categoryId = data.get("category");
            await catalog(state.search, state.categoryId);
        });
    }

    async function myLearning() {
        const entries = await api(`/users/${state.user.id}/enrollments`);
        const courses = await Promise.all(entries.map(entry => api(`/courses/${entry.courseId}`)));
        main.innerHTML = `${title("Моё обучение", "Ваши курсы и следующий шаг к новым знаниям.")}
            ${courses.length ? `<div class="grid" style="margin-top:28px">${courses.map(courseCard).join("")}</div>` : empty("Вы ещё не записаны на курсы. Начните с каталога.")}`;
    }

    async function showCourse(id, tab = "curriculum") {
        if (!requireLogin()) return;
        state.course = await api(`/courses/${id}`);
        state.tab = tab;
        const course = state.course;
        let enrolled = owns(course);
        if (!enrolled && state.user.role === "STUDENT") {
            const entries = await api(`/users/${state.user.id}/enrollments`);
            enrolled = entries.some(entry => entry.courseId === course.id);
        }
        main.innerHTML = `<button class="back-link" data-nav="catalog">← К каталогу</button>
            <div class="eyebrow">${escape(course.category)}</div><h1>${escape(course.title)}</h1><p class="muted">${escape(course.description)}</p>
            <p class="hint">Преподаватель: ${escape(course.teacher)} · Начало: ${course.startDate || "В любое время"}</p>
            ${!enrolled ? `<div class="panel"><h3>Готовы начать?</h3><p class="muted">Запишитесь, чтобы открыть уроки, задания и тесты.</p>${state.user.role === "STUDENT" ? button("Записаться на курс", "enroll", id, "") : "<p>Управлять этим курсом может его преподаватель.</p>"}</div>` : `
            <div class="tabs">${[["curriculum", "Программа"], ["quizzes", "Тесты"], ["schedule", "Расписание"], ["reviews", "Отзывы"], ["statistics", "Прогресс"], ...(owns(course) ? [["students", "Студенты"]] : [])].map(([key, label]) => `<button class="${tab === key ? "active" : ""}" data-action="course-tab" data-tab="${key}">${label}</button>`).join("")}</div>
            <section id="course-content">${empty("Загрузка…")}</section>
            <div class="actions">${owns(course) ? button("Редактировать курс", "edit-course", id) + button("Добавить модуль", "add-module", id) + button("Удалить курс", "delete-course", id, "danger") : button("Отписаться от курса", "withdraw", id, "danger")}</div>`}`;
        if (!enrolled) return;
        const container = document.querySelector("#course-content");
        if (tab === "curriculum") {
            const tree = await api(`/courses/${id}/curriculum`);
            container.innerHTML = tree.modules.map(item => `<article class="panel"><div class="row"><div><span class="eyebrow">МОДУЛЬ ${item.module.position}</span><h3>${escape(item.module.title)}</h3></div>${owns(course) ? button("+ Урок", "add-lesson", item.module.id, "secondary small") + button("Изменить", "edit-module", item.module.id, "secondary small") : ""}</div>
                ${item.lessons.map(lesson => `<button class="lesson-link" data-action="lesson" data-id="${lesson.id}"><span>${lesson.position}. ${escape(lesson.title)}</span><span>Открыть →</span></button>`).join("") || '<p class="muted">Уроки скоро появятся</p>'}
                ${owns(course) ? `<div class="actions">${button("+ Тест", "add-quiz", item.module.id, "secondary small")}${button("Удалить модуль", "delete-module", item.module.id, "danger small")}</div>` : ""}</article>`).join("") || empty("Программа пока готовится");
        } else if (tab === "quizzes") {
            const quizzes = await api(`/courses/${id}/quizzes`);
            container.innerHTML = quizzes.map(quiz => `<article class="panel"><div class="row"><div><h3>${escape(quiz.title)}</h3><span class="hint">${quiz.questions.length} вопросов · Порог ${quiz.passPercent}%</span></div>${button(owns(course) ? "Редактор теста" : "Пройти тест →", "quiz", quiz.id, "")}</div></article>`).join("") || empty("Тестов пока нет");
        } else if (tab === "schedule") {
            const entries = await api(`/courses/${id}/schedule`);
            container.innerHTML = entries.map(value => `<article class="panel"><h3>${escape(value.lessonTitle)}</h3><p>${date(value.startsAt)} — ${date(value.endsAt)}</p><a href="${escape(value.meetingUrl)}" target="_blank" rel="noopener">Ссылка на занятие ↗</a>${owns(course) ? `<div class="actions">${button("Удалить", "delete-schedule", value.id, "danger small")}</div>` : ""}</article>`).join("") || empty("Занятия пока не запланированы");
        } else if (tab === "reviews") {
            const reviews = await api(`/courses/${id}/reviews`);
            container.innerHTML = reviews.map(value => `<article class="panel"><div class="row"><strong>${escape(value.studentName)}</strong><span class="badge">${"★".repeat(value.rating)}</span></div><p>${escape(value.comment)}</p>${value.studentId === state.user.id || state.user.role === "ADMIN" ? button("Удалить отзыв", "delete-review", value.id, "danger small") : ""}</article>`).join("") || empty("Станьте первым, кто оставит отзыв");
            if (state.user.role === "STUDENT") container.innerHTML += `<div class="actions">${button("Оставить отзыв", "add-review", id, "")}</div>`;
        } else if (tab === "statistics") {
            const query = state.user.role === "STUDENT" ? `?studentId=${state.user.id}` : "";
            const data = await api(`/courses/${id}/statistics${query}`);
            container.innerHTML = `<div class="stat-grid"><div class="stat"><strong>${data.students}</strong><span>студентов на курсе</span></div><div class="stat"><strong>${data.lessons}</strong><span>уроков в программе</span></div><div class="stat"><strong>${data.progress}%</strong><span>ваш прогресс · ${data.completedLessons} уроков</span><div class="progress"><div style="width:${data.progress}%"></div></div></div></div>`;
        } else if (tab === "students") {
            const students = await api(`/courses/${id}/enrollments`);
            container.innerHTML = `<div class="panel">${students.map(s => `<div class="list-row row"><div><strong>${escape(s.studentName)}</strong><p>${date(s.enrolledAt)} · ${escape(s.status)}</p></div>${button("Завершил курс", "complete-enrollment", s.studentId, "secondary small")}</div>`).join("") || "Записей пока нет"}</div>`;
        }
    }

    async function showLesson(id) {
        const [lesson, assignments, materials] = await Promise.all([
            api(`/lessons/${id}`), api(`/lessons/${id}/assignments`), api(`/lessons/${id}/materials`)
        ]);
        main.innerHTML = `<button class="back-link" data-action="back-course">← К программе курса</button>${title(lesson.title, "Урок " + lesson.position)}
            <div class="panel lesson-content">${escape(lesson.content)}</div>
            <div class="actions">${state.user.role === "STUDENT" ? button("✓ Отметить изученным", "complete-lesson", id, "") : ""}
            ${owns(state.course) ? button("Изменить урок", "edit-lesson", id) + button("+ Задание", "add-assignment", id) + button("+ Материал", "add-material", id) + button("+ Занятие", "add-schedule", id) + button("Удалить урок", "delete-lesson", id, "danger") : ""}</div>
            <div class="section-heading"><h2>Материалы</h2></div>${materials.map(m => `<div class="panel row"><a target="_blank" rel="noopener" href="${escape(m.url)}">${escape(m.title)} ↗</a>${owns(state.course) ? button("Удалить", "delete-material", m.id, "danger small") : ""}</div>`).join("") || empty("Дополнительных материалов нет")}
            <div class="section-heading"><h2>Практика</h2></div>${assignments.map(a => `<article class="panel"><span class="badge">${a.maxScore} баллов</span><h3>${escape(a.title)}</h3><p>${escape(a.description)}</p><p class="hint">Дедлайн: ${date(a.dueAt)}</p><div class="actions">${owns(state.course) ? button("Решения студентов", "assignment-submissions", a.id, "") + button("Удалить задание", "delete-assignment", a.id, "danger") : button("Отправить решение", "submit", a.id, "")}</div></article>`).join("") || empty("Заданий пока нет")}`;
        state.lesson = id;
    }

    async function showQuiz(id) {
        const quiz = await api(`/quizzes/${id}`);
        state.quiz = id;
        const editable = owns(state.course);
        main.innerHTML = `<button class="back-link" data-action="back-course">← К курсу</button>${title(quiz.title, `Порог прохождения: ${quiz.passPercent}%. Одна попытка. Для множественного выбора нужны все правильные варианты.`)}
            <form id="quiz-form">${quiz.questions.map(q => `<article class="panel"><span class="eyebrow">ВОПРОС ${q.position} · ${q.points} БАЛЛОВ</span><h3>${escape(q.text)}</h3>
            ${q.options.map(o => `<label class="choice"><input type="${q.type === "SINGLE_CHOICE" ? "radio" : "checkbox"}" name="q-${q.id}" value="${o.id}"><span>${escape(o.text)} ${editable && o.correct ? "✓" : ""}</span></label>`).join("")}
            ${editable ? `<div class="actions">${button("+ Вариант", "add-option", q.id, "secondary small")}${button("Удалить вопрос", "delete-question", q.id, "danger small")}</div>` : ""}</article>`).join("") || empty("В тесте пока нет вопросов")}
            ${!editable && quiz.questions.length ? '<button class="button" type="submit">Завершить тест →</button>' : ""}</form>
            ${editable ? `<div class="actions">${button("+ Вопрос", "add-question", id, "")}${button("Результаты студентов", "quiz-results", id)}${button("Удалить тест", "delete-quiz", id, "danger")}</div><p class="hint">После первой попытки структура теста блокируется для сохранения достоверности результатов.</p>` : ""}`;
        document.querySelector("#quiz-form").addEventListener("submit", async event => {
            event.preventDefault();
            const form = event.currentTarget;
            const data = new FormData(form);
            const answers = quiz.questions.map(q => ({ questionId: q.id, optionIds: data.getAll(`q-${q.id}`).map(Number) }));
            const submit = form.querySelector('[type="submit"]');
            submit.disabled = true;
            try {
                const result = await api(`/quizzes/${id}/attempts`, "POST", { answers });
                toast(`${result.passed ? "Тест пройден!" : "Тест завершён."} Результат: ${result.score}/${result.maxScore}`);
                await navigate("results");
            } catch (error) {
                toast(error.message);
                submit.disabled = false;
            }
        });
    }

    function workCard(work, grading = false) {
        return `<article class="panel"><div class="row"><h3>${escape(work.assignmentTitle)}</h3><span class="badge ${work.score !== null ? "green" : ""}">${work.score === null ? "На проверке" : `${work.score} баллов`}</span></div>
            <p class="hint">${escape(work.studentName)} · ${date(work.submittedAt)}</p><p>${escape(work.content)}</p>${work.feedback ? `<p><strong>Обратная связь:</strong> ${escape(work.feedback)}</p>` : ""}${grading ? button("Оценить работу", "grade", work.id, "") : ""}</article>`;
    }

    function resultCard(result) {
        return `<article class="panel row"><div><h3>${escape(result.quizTitle)}</h3><span class="hint">${escape(result.studentName)} · ${date(result.takenAt)}</span></div><span class="badge ${result.passed ? "green" : ""}">${result.score}/${result.maxScore} · ${result.passed ? "Пройден" : "Не пройден"}</span></article>`;
    }

    async function results() {
        const [works, quizzes] = await Promise.all([
            api(`/users/${state.user.id}/submissions`), api(`/users/${state.user.id}/quiz-results`)
        ]);
        main.innerHTML = `${title("Каждый шаг — результат", "Сданные задания, обратная связь и проверка знаний.")}
            <div class="section-heading"><h2>Домашние работы</h2></div>${works.map(w => workCard(w)).join("") || empty("Вы ещё не отправляли работы")}
            <div class="section-heading"><h2>Пройденные тесты</h2></div>${quizzes.map(resultCard).join("") || empty("Здесь появятся результаты тестов")}`;
    }

    async function profile() {
        const value = await api(`/users/${state.user.id}/profile`);
        main.innerHTML = `${title("Мой профиль", "Немного о вас и вашем обучении.")}
            <div class="panel"><h3>${escape(state.user.name)}</h3><p>${escape(state.user.email)} · ${escape(state.user.role)}</p><p>${escape(value.biography || "Расскажите о себе")}</p><div class="actions">${button("Редактировать профиль", "edit-profile", state.user.id, "")}</div></div>`;
    }

    async function editor() {
        if (!manager()) {
            main.innerHTML = title("Пространство преподавателя") + empty("Управление доступно преподавателям и администраторам. Для обучения откройте каталог.");
            return;
        }
        const page = await api("/courses?size=100");
        const own = page.content.filter(owns);
        main.innerHTML = `${title("Пространство преподавателя", "Создавайте курсы, выдавайте задания и помогайте студентам расти.")}
            <div class="actions">${button("+ Создать курс", "add-course", "", "")}
            ${state.user.role === "ADMIN" ? button("+ Пользователь", "add-user") + button("+ Категория", "add-category") + button("+ Тег", "add-tag") + button("Пользователи", "list-users") : ""}</div>
            <div class="section-heading"><h2>Ваши курсы</h2></div>
            ${own.length ? `<div class="grid">${own.map(courseCard).join("")}</div>` : empty("Создайте свой первый курс")}`;
    }

    async function courseForm(id) {
        const categories = await api("/categories");
        const current = id ? await api(`/courses/${id}`) : {};
        const teachers = state.user.role === "ADMIN"
            ? (await api("/users?size=100")).content.filter(u => u.role === "TEACHER").map(u => ({ id: u.id, name: u.name }))
            : [{ id: state.user.id, name: state.user.name }];
        openForm(id ? "Редактировать курс" : "Новый курс", [
            field("title", "Название", "text", current.title),
            field("description", "Описание", "textarea", current.description),
            field("categoryId", "Категория", "number", current.categoryId, categories),
            field("teacherId", "Преподаватель", "number", current.teacherId, teachers),
            field("startDate", "Дата начала", "date", current.startDate)
        ], async values => {
            values.startDate ||= null;
            const result = await api(id ? `/courses/${id}` : "/courses", id ? "PUT" : "POST", values);
            await showCourse(result.id);
            toast("Курс сохранён");
        });
    }

    async function editContent(kind, id, editing = false) {
        const config = {
            module: { title: "Модуль", parent: `/courses/${id}/modules`, path: "modules",
                fields: [field("title", "Название"), field("description", "Описание", "textarea"), field("position", "Порядковый номер", "number", 1)] },
            lesson: { title: "Урок", parent: `/modules/${id}/lessons`, path: "lessons",
                fields: [field("title", "Название"), field("content", "Содержание", "textarea"), field("position", "Порядковый номер", "number", 1)] },
            assignment: { title: "Задание", parent: `/lessons/${id}/assignments`, path: "assignments",
                fields: [field("title", "Название"), field("description", "Условие", "textarea"), field("dueAt", "Дедлайн (ваше местное время)", "datetime-local"), field("maxScore", "Максимальный балл", "number", 100)] },
            material: { title: "Материал", parent: `/lessons/${id}/materials`, path: "materials",
                fields: [field("title", "Название"), field("url", "Ссылка HTTPS", "url")] },
            schedule: { title: "Занятие", parent: `/lessons/${id}/schedule`, path: "schedule",
                fields: [field("startsAt", "Начало", "datetime-local"), field("endsAt", "Окончание", "datetime-local"), field("meetingUrl", "Ссылка HTTPS", "url")] },
            quiz: { title: "Тест", parent: `/modules/${id}/quiz`, path: "quizzes",
                fields: [field("title", "Название"), field("passPercent", "Порог прохождения, %", "number", 60)] },
            question: { title: "Вопрос", parent: `/quizzes/${id}/questions`, path: "questions",
                fields: [field("text", "Текст вопроса", "textarea"), field("type", "Тип", "text", "SINGLE_CHOICE", [{ id: "SINGLE_CHOICE", name: "Один ответ" }, { id: "MULTIPLE_CHOICE", name: "Несколько ответов" }]), field("position", "Порядковый номер", "number", 1), field("points", "Баллы", "number", 1)] },
            option: { title: "Вариант ответа", parent: `/questions/${id}/options`, path: "answer-options",
                fields: [field("text", "Текст"), field("correct", "Правильный?", "text", "false", [{ id: "false", name: "Нет" }, { id: "true", name: "Да" }]), field("position", "Порядковый номер", "number", 1)] }
        }[kind];
        if (editing) {
            const value = await api(`/${config.path}/${id}`);
            config.fields.forEach(f => f.value = value[f.name]);
        }
        openForm(`${editing ? "Изменить:" : "Добавить:"} ${config.title}`, config.fields, async values => {
            ["dueAt", "startsAt", "endsAt"].forEach(key => {
                if (key in values) values[key] = values[key] ? new Date(values[key]).toISOString() : null;
            });
            if ("correct" in values) values.correct = values.correct === "true";
            await api(editing ? `/${config.path}/${id}` : config.parent, editing ? "PUT" : "POST", values);
            toast("Сохранено");
            if (kind === "question" || kind === "option") await showQuiz(state.quiz);
            else if (["assignment", "material", "schedule"].includes(kind)) await showLesson(state.lesson);
            else await showCourse(state.course.id, kind === "quiz" ? "quizzes" : "curriculum");
        });
    }

    async function handle(action, id, target) {
        if (action === "login") return login();
        if (action === "logout") {
            state.token = null;
            state.user = null;
            renderAccount();
            return navigate("catalog");
        }
        if (action === "scroll-courses") return document.querySelector("#courses-heading").scrollIntoView({ behavior: "smooth" });
        if (action === "next-page" || action === "prev-page") {
            state.page += action === "next-page" ? 1 : -1;
            return catalog(state.search, state.categoryId);
        }
        if (action === "course") return showCourse(id);
        if (action === "course-tab") return showCourse(state.course.id, target.dataset.tab);
        if (action === "back-course") return showCourse(state.course.id, state.tab);
        if (action === "lesson") return showLesson(id);
        if (action === "quiz") return showQuiz(id);
        if (action === "enroll") {
            await api(`/courses/${id}/enrollments`, "POST", { studentId: state.user.id });
            toast("Вы записаны на курс");
            return showCourse(id);
        }
        if (action === "withdraw") {
            if (!confirm("Отписаться от курса? Работы и результаты сохранятся.")) return;
            await api(`/courses/${id}/enrollments/${state.user.id}`, "DELETE");
            return navigate("my");
        }
        if (action === "complete-enrollment") {
            await api(`/courses/${state.course.id}/enrollments/${id}`, "PUT", { status: "COMPLETED" });
            return showCourse(state.course.id, "students");
        }
        if (action === "complete-lesson") {
            await api(`/lessons/${id}/progress`, "PUT");
            return toast("Урок отмечен изученным");
        }
        if (action === "add-course" || action === "edit-course") return courseForm(id || null);
        const contentActions = action.match(/^(add|edit)-(module|lesson|assignment|material|schedule|quiz|question|option)$/);
        if (contentActions) return editContent(contentActions[2], id, contentActions[1] === "edit");
        if (action.startsWith("delete-")) {
            const paths = { course: "courses", module: "modules", lesson: "lessons", assignment: "assignments", material: "materials", schedule: "schedule", quiz: "quizzes", question: "questions", review: "reviews" };
            const kind = action.slice(7);
            if (!confirm("Удалить запись? Действие нельзя отменить.")) return;
            await api(`/${paths[kind]}/${id}`, "DELETE");
            toast("Запись удалена");
            if (kind === "course") return navigate("editor");
            if (kind === "question") return showQuiz(state.quiz);
            if (["material", "assignment"].includes(kind)) return showLesson(state.lesson);
            return showCourse(state.course.id, state.tab);
        }
        if (action === "submit") return openForm("Отправить решение", [field("content", "Ваше решение", "textarea")], async values => {
            await api(`/assignments/${id}/submissions`, "POST", values);
            await navigate("results");
            toast("Решение отправлено");
        }, "На каждое задание допускается одна отправка.");
        if (action === "assignment-submissions") {
            const works = await api(`/assignments/${id}/submissions`);
            main.innerHTML = `<button class="back-link" data-action="back-course">← К курсу</button>${title("Решения студентов")}${works.map(w => workCard(w, true)).join("") || empty("Решений пока нет")}`;
            state.assignment = id;
            return;
        }
        if (action === "grade") return openForm("Оценить работу", [field("score", "Балл", "number", 0), field("feedback", "Обратная связь", "textarea")], async values => {
            await api(`/submissions/${id}/grade`, "PUT", values);
            await handle("assignment-submissions", state.assignment);
            toast("Оценка сохранена");
        });
        if (action === "quiz-results") {
            const values = await api(`/quizzes/${id}/attempts`);
            main.innerHTML = `<button class="back-link" data-action="back-course">← К курсу</button>${title("Результаты теста")}${values.map(resultCard).join("") || empty("Попыток пока нет")}`;
            return;
        }
        if (action === "add-review") return openForm("Ваше мнение о курсе", [field("rating", "Оценка от 1 до 5", "number", 5), field("comment", "Отзыв", "textarea")], async values => {
            await api(`/courses/${id}/reviews`, "POST", values);
            await showCourse(id, "reviews");
        });
        if (action === "edit-profile") {
            const value = await api(`/users/${id}/profile`);
            return openForm("Мой профиль", [field("biography", "О себе", "textarea", value.biography, null, false), field("avatarUrl", "URL аватара (HTTPS)", "url", value.avatarUrl, null, false)], async values => {
                await api(`/users/${id}/profile`, "PUT", values);
                await profile();
            });
        }
        if (action === "add-category" || action === "add-tag") return openForm(action === "add-tag" ? "Тег" : "Категория", [field("name", "Название")], async values => {
            await api(action === "add-tag" ? "/tags" : "/categories", "POST", values);
            toast("Создано");
        });
        if (action === "add-user") return openForm("Новый пользователь", [
            field("name", "Имя"), field("email", "Почта", "email"), field("password", "Пароль", "password"),
            field("role", "Роль", "text", "STUDENT", ["STUDENT", "TEACHER", "ADMIN"].map(r => ({ id: r, name: r })))
        ], async values => {
            await api("/users", "POST", values);
            toast("Пользователь создан");
        });
        if (action === "list-users") {
            const users = await api("/users?size=100");
            main.innerHTML = `${title("Пользователи")}<div class="panel">${users.content.map(u => `<div class="list-row row"><div><strong>${escape(u.name)}</strong><p>${escape(u.email)}</p></div><span class="badge">${escape(u.role)}</span></div>`).join("")}</div>`;
        }
    }

    document.addEventListener("click", async event => {
        const nav = event.target.closest("[data-nav]");
        const action = event.target.closest("[data-action]");
        try {
            if (nav) await navigate(nav.dataset.nav);
            else if (action) await handle(action.dataset.action, action.dataset.id ? Number(action.dataset.id) : "", action);
        } catch (error) {
            toast(error.message);
        }
    });
    document.querySelector("#close-modal").addEventListener("click", () => modal.close());
    renderAccount();
    navigate("catalog");
})();
