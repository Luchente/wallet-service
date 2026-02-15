# Wallet Service
Тестовое задание: сервис кошельков с REST API, PostgreSQL и Liquibase миграциями. Цель — показать корректную работу в конкурентной среде (высокая нагрузка на один кошелёк), единый формат ошибок и запуск через docker-compose.***
## Стек
Java 17, Spring Boot 3, PostgreSQL, Liquibase, Docker / docker-compose.***
## Быстрый старт (Docker)
Команда запуска (приложение + база):  
docker compose up --build  
После старта приложение доступно по адресу: http://localhost:8080  
База данных (порт наружу): localhost:5432***
## Конфигурация без пересборки контейнеров
Все параметры задаются через переменные окружения (см. docker-compose.yaml и .env.example). Можно менять порты/логины/пароли/URL подключения без пересборки образов.***
### Примеры переменных
APP_PORT — порт приложения (по умолчанию 8080)  
DB_PORT — порт PostgreSQL на хосте (по умолчанию 5432)  
POSTGRES_DB — имя базы (по умолчанию wallet)  
POSTGRES_USER — пользователь БД (по умолчанию wallet)  
POSTGRES_PASSWORD — пароль БД (по умолчанию wallet)  
DB_URL — JDBC URL приложения (по умолчанию jdbc:postgresql://localhost:5432/wallet)  
DB_USER — пользователь для подключения приложения (по умолчанию wallet)  
DB_PASSWORD — пароль для подключения приложения (по умолчанию wallet)***
## Локальный запуск из IDE
Проект собирается/запускается в Docker на Java 17. Для локального запуска из IDE рекомендуется также Java 17 (чтобы не было расхождения окружений). При запуске из IDE убедитесь, что PostgreSQL поднят (например: docker compose up -d db).***
## Важно про создание кошелька
В ТЗ отсутствует эндпоинт создания кошелька, поэтому для ручной проверки кошелёк создаётся напрямую в БД. Пример команды (Docker):  
docker exec -it wallet-service-db-1 psql -U wallet -d wallet -c "insert into wallets(id, balance) values ('00000000-0000-0000-0000-000000000001', 0) on conflict do nothing;"***
## API
Базовый URL: /api/v1***
### POST /api/v1/wallet
Изменение баланса кошелька (DEPOSIT / WITHDRAW).  
Request body:  
{ "walletId": "UUID", "operationType": "DEPOSIT|WITHDRAW", "amount": 1000 }  
Примечание: в исходном ТЗ есть опечатка valletId. Для совместимости сервис принимает оба варианта: walletId и valletId.***
Пример DEPOSIT:  
curl -X POST http://localhost:8080/api/v1/wallet -H "Content-Type: application/json" -d '{"walletId":"00000000-0000-0000-0000-000000000001","operationType":"DEPOSIT","amount":1000}'***
Пример WITHDRAW:  
curl -X POST http://localhost:8080/api/v1/wallet -H "Content-Type: application/json" -d '{"walletId":"00000000-0000-0000-0000-000000000001","operationType":"WITHDRAW","amount":50}'***
Ответ 200:  
{ "walletId":"00000000-0000-0000-0000-000000000001", "balance":1000.00 }***
### GET /api/v1/wallets/{walletId}
Получить баланс кошелька.  
Пример:  
curl http://localhost:8080/api/v1/wallets/00000000-0000-0000-0000-000000000001***
## Формат ошибок
Все ожидаемые ошибки возвращаются в едином формате:  
{ "errorCode":"SOME_CODE", "message":"Описание", "timestamp":"2026-02-15T10:16:44.498913860Z", "path":"/api/v1/wallet", "details":{} }***
### Коды ошибок
INVALID_JSON — невалидный JSON  
INVALID_VALUE — неверное значение в JSON (например, неизвестный enum)  
VALIDATION_ERROR — не прошла валидация полей  
WALLET_NOT_FOUND — кошелёк не найден  
INSUFFICIENT_FUNDS — недостаточно средств  
INTERNAL_ERROR — внутренняя ошибка сервера (форматируется единообразно)***
## Конкурентность (Atomic Update)
Изменение баланса реализовано атомарно на стороне PostgreSQL одной командой UPDATE без read-modify-write в Java. Это уменьшает риск гонок (lost update) при высоком RPS на один кошелёк. Используется условие, предотвращающее уход в минус, и возврат нового баланса через RETURNING.  
Пример SQL (упрощённо):  
UPDATE wallets SET balance = balance + :delta WHERE id = :id AND balance + :delta >= 0 RETURNING balance;***
### Компромисс по точности ошибок
Если UPDATE не обновил строку, причина может быть две: кошелька нет (404) или недостаточно средств (409). Для корректного ответа выполняется дополнительная проверка существования кошелька (ещё один запрос). Это сделано осознанно ради точного формата ошибок.***
## Проверка вручную (быстрый чек)
1) Поднять сервис: docker compose up --build
2) Создать кошелёк в БД (команда выше)
3) Сделать DEPOSIT и GET balance
4) Проверить 409 на WITHDRAW с большой суммой
5) Проверить 404 на несуществующий walletId
6) Проверить 400 на невалидный JSON***
