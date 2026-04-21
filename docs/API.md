# Webhook API

## A. Общее описание

HTTP-сервис принимает события в формате JSON на endpoint `POST /webhook`. Доступ защищён заголовком `Authorization: Bearer <WEBHOOK_TOKEN>`, значение токена — свойство `webhook.token` (по умолчанию из переменной окружения **`WEBHOOK_TOKEN`**, см. `application.yml` и [README.md](../README.md)); при необходимости можно переопределить в **`config/application-secrets.yml`**. При пустом `webhook.token` авторизация **всегда** неуспешна (`403`). Настройки Telegram: **`TELEGRAM_BOT_TOKEN`**, **`TELEGRAM_CHAT_ID`**, опционально список `telegram.notify-chat-ids` и прочее в опциональном `application-secrets.yml`.

После успешной проверки авторизации и тела запроса сервер **немедленно** отвечает `200 OK`, а обработка (дедупликация, вызов Telegram API, запись строки в `logs/webhook.log`) выполняется **асинхронно**, чтобы не блокировать поток обработки HTTP.

## B. Endpoint

| Параметр | Значение |
|----------|----------|
| **URL** | `http://<host>:3000/webhook` (порт по умолчанию из `server.port`) |
| **Метод** | `POST` |
| **Content-Type** | `application/json` |
| **Заголовки** | `Authorization: Bearer <WEBHOOK_TOKEN>` — обязателен |

### Коды ответов

| Код | Условие |
|-----|---------|
| **200** | Токен верный, JSON валиден, обязательные поля присутствуют; событие принято к асинхронной обработке |
| **400** | Некорректный JSON, нарушена структура или не заданы обязательные поля |
| **403** | Неверный или отсутствующий Bearer-токен |
| **415** | Неверный `Content-Type` (ожидается `application/json`) |

## C. Поля payload

| Поле | Тип | Обязательность | Описание |
|------|-----|----------------|----------|
| `event_id` | string | Необяз. | Идентификатор события (например UUID) |
| `event` | string | **Обяз.** | Код/тип события |
| `level` | string | Необяз. | Уровень (например `error`) |
| `status` | string | Необяз. | Статус операции |
| `message` | string | **Обяз.** | Текст сообщения / описание ошибки |
| `error_code` | string | Необяз. | Код ошибки |
| `account` | string | Необяз. | Идентификатор аккаунта |
| `task_id` | number | Необяз. | Номер задачи |
| `project` | string | Необяз. | Проект |
| `host` | string | Необяз. | Хост / IP |
| `timestamp` | string | **Обяз.** | Время события (как прислал клиент, строка) |
| `meta` | object | Необяз. | Произвольные дополнительные данные |

Неизвестные поля в JSON **игнорируются** (не приводят к ошибке).

## D. Пример запроса

```http
POST /webhook HTTP/1.1
Host: localhost:3000
Authorization: Bearer SECRET123
Content-Type: application/json

{
  "event_id": "uuid-123",
  "event": "PUBLISH_ERROR",
  "level": "error",
  "status": "failed",
  "message": "Не удалось завершить публикацию",
  "error_code": "CAPTCHA_REQUIRED",
  "account": "acc_123",
  "task_id": 27,
  "project": "avito_main",
  "host": "185.97.201.141",
  "timestamp": "2026-04-21T12:00:00Z",
  "meta": {}
}
```

## E. Пример ответа

Успешный приём:

```http
HTTP/1.1 200 OK
Content-Type: application/json

{"ok":true,"queued":true,"event":"PUBLISH_ERROR"}
```

Ошибка авторизации:

```http
HTTP/1.1 403 Forbidden
```

Ошибка тела запроса:

```http
HTTP/1.1 400 Bad Request
```

## F. Примеры событий

### PUBLISH_ERROR

```json
{
  "event": "PUBLISH_ERROR",
  "message": "Не удалось завершить публикацию",
  "timestamp": "2026-04-21T12:00:00Z",
  "status": "failed",
  "error_code": "CAPTCHA_REQUIRED",
  "account": "acc_123",
  "task_id": 27
}
```

### SMS_CONFIRM_REQUIRED

```json
{
  "event": "SMS_CONFIRM_REQUIRED",
  "message": "Требуется подтверждение по SMS",
  "timestamp": "2026-04-21T12:05:00Z",
  "account": "acc_456",
  "task_id": 12
}
```

### CRITICAL_ERROR

```json
{
  "event": "CRITICAL_ERROR",
  "message": "Критическая ошибка процесса",
  "timestamp": "2026-04-21T12:10:00Z",
  "level": "error",
  "project": "avito_main",
  "host": "10.0.0.5"
}
```

## G. Пример curl

```bash
curl -X POST "http://localhost:3000/webhook" ^
  -H "Authorization: Bearer SECRET123" ^
  -H "Content-Type: application/json" ^
  -d "{\"event\":\"PUBLISH_ERROR\",\"message\":\"Тест\",\"timestamp\":\"2026-04-21T12:00:00Z\"}"
```

В bash/Linux:

```bash
curl -X POST "http://localhost:3000/webhook" \
  -H "Authorization: Bearer SECRET123" \
  -H "Content-Type: application/json" \
  -d '{"event":"PUBLISH_ERROR","message":"Тест","timestamp":"2026-04-21T12:00:00Z"}'
```

## H. Логирование (`logs/webhook.log`)

Каждая строка файла — **один JSON-объект** (без многострочного форматирования).

### Поля записи

| Поле | Описание |
|------|----------|
| `timestamp_received` | Время получения запроса сервером (UTC, ISO-8601) |
| `ip` | IP клиента (с учётом `X-Forwarded-For` / `X-Real-IP`, если заданы) |
| `auth` | Результат проверки токена: `ok` или `failed` |
| `payload` | Входящий JSON целиком (как объект). При невалидном JSON — служебная структура с `invalid_json` / `raw_preview` |
| `processed` | Успешно ли завершена внутренняя обработка принятого webhook (включая асинхронный этап) |
| `telegram_sent` | Успешно ли доставлено сообщение в Telegram (в рамках текущей попытки обработки) |
| `telegram_error` | Текст ошибки Telegram или внутренней отправки; `null`, если ошибки нет |

### Что не логируется

- Значение заголовка `Authorization` и сам Bearer-токен
- `TELEGRAM_BOT_TOKEN` и другие секреты окружения
- Полные тела ответов Telegram (при ошибках пишется только краткое описание / сообщение API)

### Дедупликация и логи

События с одинаковой тройкой **`event` + `message` + `account`** (пустой `account` считается как отсутствующий) не отправляются в Telegram повторно, если с момента **успешной** последней отправки такого же ключа прошло меньше **60 секунд**. Запись в `webhook.log` при этом создаётся **на каждый** допустимый запрос; при пропуске из-за дедупликации `telegram_sent` будет `false`, `telegram_error` — `null`.

---

## Поведение 200 OK и асинхронность

Ответ **200** с JSON `ok`/`queued` означает только успешную **синхронную** валидацию и постановку в очередь. Итог отправки в Telegram отражается в файле логов (`telegram_sent`, `telegram_error`).
