# Webhook Avitool

Минималистичный **Spring Boot 3** сервис на **Java 21**: приём **HTTP webhook** (JSON), проверка **Bearer-токена**, асинхронная отправка уведомлений в **Telegram** (MarkdownV2, fenced-блок с языком `log`), дедупликация повторов, построчный JSON-лог в файл.

Подходит для **24/7** на VPS или локально: один JAR, без БД.

---

## Возможности

| Функция | Описание |
|--------|----------|
| **POST /webhook** | JSON payload, заголовок `Authorization: Bearer …`, коды **200 / 400 / 403 / 415** |
| **Telegram** | `sendMessage`, несколько получателей (`chat-id` + `notify-chat-ids`) |
| **Дедупликация** | Одинаковые `event` + `message` + `account` не дублируются в Telegram **60 с** после успешной отправки |
| **Асинхронность** | Ответ **200** сразу после валидации; Telegram и запись в лог — в фоне (`@Async`) |
| **Логи** | `logs/webhook.log` — одна JSON-строка на запись (без секретов в логе) |
| **Опционально** | `telegram.test-mode` + опрос `getUpdates` и файл `logs/telegram_test_chats.json` |
| **Сервис** | **GET /health**, **GET /** — подсказка по эндпоинтам |

Полная спецификация HTTP API: **[docs/API.md](docs/API.md)**.

---

## Стек

- Java **21**, Maven, Spring Boot **3.4**
- Jackson, Bean Validation, **Lombok**
- **RestTemplate** → Telegram Bot API
- Коллекция Postman: **`src/main/resources/avitool.postman_collection.json`** (Import в Postman)

---

## Быстрый старт

### 1. Клонирование и сборка

```bash
git clone <url-репозитория>
cd WebhookAvitool
mvn -DskipTests package
```

### 2. Переменные окружения

В `application.yml` заданы плейсхолдеры:

| Переменная | Назначение |
|------------|------------|
| `WEBHOOK_TOKEN` | Секрет для `Authorization: Bearer …` |
| `TELEGRAM_BOT_TOKEN` | Токен бота [@BotFather](https://t.me/BotFather) |
| `TELEGRAM_CHAT_ID` | Чат / канал для уведомлений |

Spring Boot **не читает `.env` сам**. Варианты: переменные в IDE/Docker/systemd, плагин EnvFile, или файл **`config/application-secrets.yml`** (см. пример **`config/application-secrets.yml.example`**).

```powershell
cd WebhookAvitool
Copy-Item config\application-secrets.yml.example config\application-secrets.yml
# отредактируйте application-secrets.yml
mvn spring-boot:run
```

Порт по умолчанию: **3000** (`server.port` в `application.yml`).

### 3. JAR

```powershell
mvn -DskipTests package
java -jar target\webhook-avitool-1.0.0.jar
```

Рабочий каталог процесса должен содержать **`config/application-secrets.yml`**, если вы им пользуетесь (путь задаётся `spring.config.import`).

---

## Структура проекта

```
src/main/java/com/webhookavitool/
  WebhookApplication.java
  config/          — свойства, RestTemplate, фильтры (лог, нормализация //)
  controller/      — Webhook, Health, Root
  dto/             — WebhookPayload, WebhookLogEntry
  service/         — обработка, Telegram, дедуп, лог в файл
src/main/resources/
  application.yml
  avitool.postman_collection.json
config/
  application-secrets.yml.example
docs/
  API.md
```

---

## Безопасность и публикация в Git

- В репозитории **не должно** быть реальных токенов. В **`.gitignore`**: `config/application-secrets.yml`, `.env`, `logs/`.
- Перед `git push` проверьте: **`git status`** — нет ли случайно добавленных секретов.
- Токен бота при утечке отзовите в **@BotFather** (`/revoke`).

---

## Публикация на GitHub (или другой remote)

```bash
cd WebhookAvitool
git init
git add .
git commit -m "Initial commit: Webhook Avitool — Spring Boot webhook + Telegram"
git branch -M main
git remote add origin https://github.com/<user>/<repo>.git
git push -u origin main
```

Подставьте свой URL репозитория. Если репозиторий на GitHub ещё не создан — **New repository** на github.com, без README (чтобы не было конфликта при первом push), затем команды выше.

---

## Частые проблемы

| Симптом | Что проверить |
|---------|----------------|
| **403** `forbidden` | Совпадение `WEBHOOK_TOKEN` / `webhook.token` и заголовка `Bearer` (ASCII, без лишних пробелов). |
| **200**, но нет сообщения в Telegram | `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` на **той же машине**, где крутится JAR; **`logs/webhook.log`** — `telegram_sent`, `telegram_error`. |
| Postman **//webhook** или **404** | В переменной `baseUrl` **без** завершающего `/`; URL вида `{{baseUrl}}/webhook`. |
| PowerShell **curl** ломает JSON / кириллицу в Bearer | Тело в одинарных кавычках; токен латиницей; см. раздел в [docs/API.md](docs/API.md). |
| Markdown в Telegram **400** | В проекте экранирование MarkdownV2 для блока ` ```log `; при новых символах в payload смотрите ответ API в логе. |

---

## Лицензия

[MIT](LICENSE)

---

## Документация

- [docs/API.md](docs/API.md) — endpoint, payload, примеры, логирование, curl.
