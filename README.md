# Wallet Service

Тестовое задание: REST-сервис кошельков с PostgreSQL, Liquibase, Docker и корректной работой в конкурентной среде.

## Стек
- Java 17
- Spring Boot 3
- PostgreSQL
- Liquibase
- Docker / docker-compose
- Тесты: JUnit 5, MockMvc, Testcontainers

---

## Быстрый старт (Docker)

Поднять приложение + БД:

```bash
docker compose up --build
```

Сервис будет доступен на: `http://localhost:8080`

### Конфигурация без пересборки контейнеров
Все параметры настраиваются через переменные окружения (можно задать в `.env`, пример — `.env.example`):
- `APP_PORT` (по умолчанию 8080)
- `DB_PORT` (по умолчанию 5432)
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- параметры пула Hikari: `DB_POOL_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONN_TIMEOUT_MS` и т.д.

---

## Dev seed (для удобства проверки)

В `docker-compose.yaml` включён Liquibase context `dev`, поэтому при запуске через docker-compose создаётся тестовый кошелёк:

- `walletId`: `00000000-0000-0000-0000-000000000001`
- `balance`: `0.00`

Это сделано только для удобства ручной проверки в docker-compose (не для production).

---

## API

Базовый префикс: `/api/v1`

### POST `/api/v1/wallet`
Изменение баланса кошелька (DEPOSIT / WITHDRAW).

Request body:
```json
{
  "walletId": "UUID",
  "operationType": "DEPOSIT|WITHDRAW",
  "amount": 1000
}
```

Примечание: в исходном ТЗ есть опечатка `valletId`. Для совместимости сервис принимает и `walletId`, и `valletId`.

Пример запроса (Git Bash / Linux / macOS):
```bash
curl -i -X POST "http://localhost:8080/api/v1/wallet" \
  -H "Content-Type: application/json" \
  -d '{"walletId":"00000000-0000-0000-0000-000000000001","operationType":"DEPOSIT","amount":1000}'
```

Ответ 200:
```json
{
  "walletId": "00000000-0000-0000-0000-000000000001",
  "balance": 1000.00
}
```

### GET `/api/v1/wallets/{walletId}`
Получить баланс кошелька.

Пример:
```bash
curl -i "http://localhost:8080/api/v1/wallets/00000000-0000-0000-0000-000000000001"
```

Ответ 200:
```json
{
  "walletId": "00000000-0000-0000-0000-000000000001",
  "balance": 0.00
}
```

---

## Формат ошибок (единый)

Все ожидаемые ошибки возвращаются в едином формате:

```json
{
  "errorCode": "SOME_CODE",
  "message": "Описание",
  "timestamp": "2026-02-15T15:31:38.595Z",
  "path": "/api/v1/...",
  "details": {}
}
```

Коды ошибок:
- `INVALID_JSON` (400) — некорректный JSON
- `INVALID_VALUE` (400) — некорректное значение (например enum/uuid)
- `VALIDATION_ERROR` (400) — ошибка bean validation
- `WALLET_NOT_FOUND` (404) — кошелёк не найден
- `INSUFFICIENT_FUNDS` (409) — недостаточно средств
- `NOT_FOUND` (404) — неизвестный эндпоинт
- `INTERNAL_ERROR` (500) — непредвиденная ошибка (возвращается в едином формате)

---

## Конкурентность

Изменение баланса выполняется атомарно в PostgreSQL одним SQL-запросом (без схемы read-modify-write на стороне Java).
Это защищает от гонок при высокой конкуренции запросов на один кошелёк.

---

## Миграции

Liquibase автоматически применяет миграции при старте приложения.

---

## Тесты

Запуск тестов локально:
```bash
./mvnw clean test
```

Тесты — интеграционные, используют PostgreSQL через Testcontainers (потребуется установленный Docker).

---

## Примечания по исходному ТЗ

1) В примере запроса в ТЗ есть опечатка `valletId`. Для совместимости сервис принимает и `walletId`, и `valletId`.

2) В ТЗ не описан эндпоинт создания кошелька. В реализации считается, что кошелёк уже существует в БД (для удобства ручной проверки в docker-compose добавлен dev seed через Liquibase context `dev`).

3) Требование «не должно быть 5xx» в буквальном смысле недостижимо в реальной инфраструктуре (сеть/БД/ресурсы могут быть недоступны). В рамках приложения все ожидаемые ошибки (валидация, отсутствие кошелька, недостаток средств) обрабатываются и возвращаются в едином формате; непредвиденные ошибки возвращаются как `500 INTERNAL_ERROR` также в едином формате.

