# ORM Learning Platform

Учебная платформа для онлайн-курсов: программа, расписание, запись студентов, домашние работы, проверка знаний и прогресс.

**Стек:** Java 21, Spring Boot 3.5, Spring Data JPA / Hibernate, PostgreSQL 16, Liquibase, Spring Security + JWT, Maven. Интерфейс — HTML/CSS/JavaScript без отдельного frontend-сервера.

## Быстрый запуск

Нужны Docker Engine с Docker Compose и доступ в интернет для первой сборки. Локальные Java и Maven для Docker-запуска не требуются.

```bash
cp .env.example .env
```

Замените в `.env`:

- `DB_PASSWORD` — пароль локальной БД;
- `JWT_SECRET` — случайный секрет минимум 64 байта; сгенерировать: `openssl rand -hex 64`;
- `DEMO_PASSWORD` — пароль демо-пользователей, от 10 символов и не более 72 байт UTF-8.

```bash
docker compose up --build -d
docker compose logs -f app
```

- Интерфейс: **http://localhost:8080**
- Swagger UI: **http://localhost:8080/swagger-ui.html**
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Публичный каталог: http://localhost:8080/api/courses

Если 8080 занят, измените `APP_PORT`. Если занят 5432, задайте другой `DB_PORT` в `.env`, например `15432`; внутреннее подключение приложения к БД останется прежним.

`docker compose down` останавливает приложение и БД, не удаляя данные. Не используйте `down -v`, если данные нужны: этот флаг удаляет том PostgreSQL.

### Демо-аккаунты

У всех пароль из **вашего** `DEMO_PASSWORD`; пароль не вшит в код и не выводится в лог.

| Почта | Роль |
|---|---|
| `admin@learning.local` | Администратор |
| `teacher@learning.local` | Преподаватель |
| `student@learning.local` | Студент |
| `student2@learning.local` | Второй студент |

При первом запуске с профилем `dev` атомарно создаются все 18 типов сущностей: курс, два модуля, четыре урока, задания, тест с двумя типами вопросов, материалы, расписание, записи, отзыв, прогресс и примеры результатов. Повторный запуск не дублирует набор. Изменение `DEMO_PASSWORD` не меняет пароли уже созданных аккаунтов.

Ссылки на занятия в демоданных — демонстрационные, не настоящая видеоконференция.

## Как пользоваться

### Студент

1. Войдите как `student@learning.local` или зарегистрируйте свой аккаунт.
2. В каталоге откройте курс. Новый студент должен нажать «Записаться на курс».
3. Откройте урок в программе, прочитайте материал и отметьте его изученным.
4. Отправьте решение задания «Спроектируйте сущность курса».
5. На вкладке «Тесты» пройдите тест. Для множественного выбора отметьте **все и только правильные** варианты.
6. Оценки и результаты появятся в разделе «Работы и результаты», прогресс — во вкладке курса.
7. Можно оставить отзыв, изменить профиль или отписаться. После отписки работы и результаты сохраняются.

У первого демо-студента уже есть одна проверенная работа; первая домашняя работа и тест доступны для новой отправки. У второго студента есть пример успешно пройденного теста.

### Преподаватель

1. Войдите как `teacher@learning.local`, откройте «Управление».
2. Создайте курс, затем модуль и урок.
3. В уроке добавьте задание, материал или занятие в расписании.
4. Откройте «Решения студентов» у задания и выставьте оценку с комментарием.
5. В модуле добавьте тест, вопросы и варианты. Поддерживаются одиночный и множественный выбор.
6. Смотрите записанных студентов и результаты тестов.

Демонстрационный тест уже имеет результат второго студента, поэтому его структура заблокирована. Для проверки редактора создайте новый тест в другом модуле или свой курс.

### Администратор

Может создавать преподавателей и администраторов, управлять справочниками и всеми курсами. Обычная регистрация всегда создаёт только студента. Полное административное CRUD-действие также доступно через Swagger; простой интерфейс покрывает основные учебные сценарии, а не заменяет все методы API.

## Локальный запуск без контейнера приложения

Требуются JDK 21, Maven 3.9+, Docker для PostgreSQL.

```bash
set -a
source .env
set +a
docker compose up -d db
export DB_URL="jdbc:postgresql://localhost:${DB_PORT:-5432}/${DB_NAME:-learning}"
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Для установки зависимостей достаточно Maven: ручная установка JAR не требуется. Первая сборка скачивает их из Maven Central.

### Конфигурация и профили

| Переменная | Назначение |
|---|---|
| `DB_URL` | JDBC URL; по умолчанию `jdbc:postgresql://localhost:5432/learning` |
| `DB_USERNAME` | Пользователь БД; по умолчанию `learning` |
| `DB_PASSWORD` | Обязательный пароль БД |
| `DB_PORT` | Публикуемый порт PostgreSQL в Docker Compose; по умолчанию 5432 |
| `JWT_SECRET` | Обязательный секрет подписи JWT, минимум 64 байта |
| `DEMO_PASSWORD` | Обязателен только при включении демонабора |
| `SPRING_PROFILES_ACTIVE` | `dev` для демонстрации, `test` для автоматических тестов |
| `APP_PORT` | Публикуемый порт Docker Compose |

Без профиля `dev` демонабор выключен. Схему автоматически создаёт Liquibase; `ddl-auto=validate` проверяет соответствие JPA-модели миграции. Это намеренная альтернатива неконтролируемому `ddl-auto=update`.

Для недемонстрационного окружения начального администратора нужно подготовить отдельно через контролируемое администрирование БД с BCrypt-хешем пароля. Публичного назначения роли ADMIN нет.

## REST API

Все маршруты начинаются с `/api`. JSON — формат запросов и ответов. Полная машинно-проверяемая спецификация с моделями находится в Swagger.

### Авторизация

```bash
curl -s http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"student@learning.local","password":"ВАШ_DEMO_PASSWORD"}'
```

Скопируйте `accessToken` из ответа:

```bash
export TOKEN='полученный-accessToken'
curl http://localhost:8080/api/auth/me -H "Authorization: Bearer $TOKEN"
```

В Swagger нажмите **Authorize** и вставьте токен без префикса `Bearer`. JWT действует два часа. Роль и существование аккаунта проверяются по БД при каждом защищённом сценарии, а не берутся из изменяемого заголовка с ID пользователя.

| Маршрут | Методы / назначение |
|---|---|
| `/auth/register` | `POST` регистрация студента: `name`, `email`, `password` |
| `/auth/login` | `POST` получение JWT: `email`, `password` |
| `/auth/me` | `GET` текущий пользователь |
| `/users`, `/users/{id}` | `GET/POST`, `GET/PUT/DELETE`; список и создание — ADMIN, чтение/изменение — владелец или ADMIN |
| `/users/{id}/profile` | `GET/PUT`, поля `biography`, `avatarUrl` |
| `/categories`, `/categories/{id}` | `GET/POST`, `GET/PUT/DELETE`; изменение — ADMIN |
| `/tags`, `/tags/{id}` | Аналогичный CRUD справочника тегов |
| `/courses`, `/courses/{id}` | `GET/POST`, `GET/PUT/DELETE` |
| `/courses/{id}/tags` | `GET/PUT`, тело `{"tagIds":[1,2]}` |
| `/courses/{id}/curriculum` | `GET` полная программа курса |
| `/courses/{courseId}/modules`, `/modules/{id}` | `GET/POST`, `GET/PUT/DELETE` |
| `/modules/{moduleId}/lessons`, `/lessons/{id}` | `GET/POST`, `GET/PUT/DELETE` |
| `/lessons/{lessonId}/materials`, `/materials/{id}` | `GET/POST`, `GET/PUT/DELETE` |
| `/courses/{courseId}/schedule` | `GET` расписание курса |
| `/lessons/{lessonId}/schedule`, `/schedule/{id}` | `POST`, `GET/PUT/DELETE` |
| `/courses/{courseId}/enrollments` | `GET` студенты курса; `POST {"studentId":3}` запись |
| `/courses/{courseId}/enrollments/{studentId}` | `GET/PUT/DELETE`; `PUT {"status":"COMPLETED"}` — преподаватель |
| `/users/{studentId}/enrollments` | `GET` курсы студента |
| `/lessons/{lessonId}/assignments`, `/assignments/{id}` | `GET/POST`, `GET/PUT/DELETE` |
| `/assignments/{assignmentId}/submissions` | `GET` решения для преподавателя; `POST {"content":"Решение"}` |
| `/submissions/{id}` | `GET` решение для автора или преподавателя курса |
| `/submissions/{id}/grade` | `PUT {"score":95,"feedback":"Хорошая работа"}` |
| `/users/{studentId}/submissions` | `GET` работы и оценки студента |
| `/courses/{courseId}/quizzes` | `GET` тесты курса |
| `/modules/{moduleId}/quiz`, `/quizzes/{id}` | `POST`, `GET/PUT/DELETE` |
| `/quizzes/{quizId}/questions`, `/questions/{id}` | `POST`, `GET/PUT/DELETE`; список вопросов вложен в тест |
| `/questions/{questionId}/options`, `/answer-options/{id}` | `POST`, `GET/PUT/DELETE`; список вариантов вложен в вопрос |
| `/quizzes/{quizId}/attempts` | `POST` прохождение; `GET` результаты для преподавателя |
| `/quiz-results/{id}`, `/users/{studentId}/quiz-results` | `GET` результат / результаты студента |
| `/courses/{courseId}/reviews`, `/reviews/{id}` | `GET/POST`, `GET/PUT/DELETE` |
| `/lessons/{lessonId}/progress` | Идемпотентный `PUT` «изучен», `DELETE` сброс |
| `/courses/{courseId}/progress?studentId=3` | `GET` изученные уроки |
| `/courses/{courseId}/statistics?studentId=3` | `GET` прогресс студента; без studentId — сводка для преподавателя |

Публичны интерфейс, OpenAPI, чтение каталога, отдельных курсов, категорий и тегов. Учебный контент — для записанного студента, преподавателя курса и ADMIN. Правильные варианты ответов отсутствуют в JSON для студента, включая отдельный endpoint варианта.

Списки курсов и пользователей пагинированы: `page=0&size=20`, максимум 100. Для курсов доступны `search`, `categoryId`, `tagId`. Остальные вложенные списки рассчитаны на учебные наборы данных.

### Примеры тел запросов

Создание курса от имени преподавателя:

```json
{
  "title": "Основы Hibernate",
  "description": "Практический курс по JPA",
  "categoryId": 1,
  "teacherId": 2,
  "startDate": "2030-09-01"
}
```

Прохождение теста (ID нужно взять из `GET /quizzes/{id}`):

```json
{
  "answers": [
    {"questionId": 1, "optionIds": [1]},
    {"questionId": 2, "optionIds": [4, 6]}
  ]
}
```

Для пропущенного вопроса передайте пустой `optionIds`; такой вопрос даёт 0 баллов. Каждый вопрос должен быть передан ровно один раз. Баллы начисляются за точное совпадение множества выбранных вариантов; частичного начисления нет.

Даты со временем — ISO 8601 с часовым поясом (`2030-09-01T10:00:00Z`), простая дата — `YYYY-MM-DD`. Позиции модулей, уроков, вопросов и вариантов начинаются с 1 и уникальны внутри родителя.

### HTTP и ошибки

- `201 Created` + `Location` — созданный ресурс;
- `200 OK` — чтение/изменение; `204 No Content` — удаление;
- `400` — неверные параметры, JSON или бизнес-значения;
- `401` — отсутствует/истёк JWT или неверные реквизиты входа;
- `403` — недостаточно прав или нет записи на курс;
- `404` — ресурс не найден;
- `409` — дубликат, истёк дедлайн, защищённая история или конкурентное изменение.

Ошибки — `application/problem+json`; ошибки валидации содержат карту `fields`. SQL, пароли и внутренние трассировки клиенту не выдаются.

## Архитектура

```text
src/main/java/ru/study/platform/
├── api/          REST-контроллеры и DTO с Bean Validation
├── config/       JWT, OpenAPI, Clock, демонабор
├── domain/       18 JPA-сущностей и перечисления
├── error/        доменные ошибки и ControllerAdvice
├── repository/   18 Spring Data JPA-репозиториев
└── service/      транзакционные бизнес-сценарии и проверка доступа
src/main/resources/
├── db/changelog/ версионированная схема Liquibase и rollback
└── static/       интерфейс без frontend-зависимостей
```

Подробнее: [модель данных и решения ORM](docs/architecture.md)

## Тестирование

Для полной проверки нужны JDK 21, Maven и работающий Docker daemon. Внешняя тестовая БД не нужна: Testcontainers поднимает отдельный PostgreSQL 16 на случайном порту. Тесты **не используют H2** и не трогают БД из Docker Compose. Доступ в интернет нужен для первой загрузки Maven-зависимостей и Docker-образов.

```bash
mvn clean verify
```

Команда запускает проверку форматирования, unit-тесты и интеграционные тесты, собирает JAR и формирует отчёт JaCoCo. Сборка не проходит при покрытии строк ниже 80% или ветвей ниже 70%:

- `target/surefire-reports/` — результаты тестов;
- `target/site/jacoco/index.html` — покрытие;
- `target/orm-learning-platform-1.0.0.jar` — приложение.

Только unit-тесты, без Docker:

```bash
mvn -Dtest=QuizScorerTest test
```

Автоформатирование Java:

```bash
mvn spotless:apply
```

Проверяются настоящий JWT, приватность и роли, CRUD, FK и CHECK-ограничения, каскады и rollback, конкурентная повторная запись, дедлайны, оценки, оба типа вопросов, сохранение результатов, демонабор и ленивая загрузка. Негативные тесты ожидают исключения/HTTP-ошибки и считаются успешными именно при их корректном воспроизведении.

GitHub Actions запускает `clean verify`, сохраняет отчёты и собирает Docker-образ на push и pull request. После успешной проверки push в основную ветку публикует образ в GitHub Container Registry (`ghcr.io/<владелец>/<репозиторий>`, теги `latest` и `sha-…`). Для этого используется встроенный `GITHUB_TOKEN` с правом `packages: write`; если организация ограничивает пакеты, разрешите публикацию в настройках.

Это continuous delivery образа, не автоматическое развёртывание на стороннем сервере. Саму публикацию репозитория и внешнее развёртывание нужно выполнить под вашей учётной записью.
