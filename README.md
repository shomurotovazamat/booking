# Система онлайн-бронирования с развёртыванием в облачной инфраструктуре

Веб-приложение для бронирования помещений (переговорные, залы, рабочие места) по часовым
слотам. Разворачивается в Yandex Cloud одной командой Terraform, все компоненты работают
в Docker-контейнерах, состояние системы отслеживается через Prometheus и Grafana.

## Архитектура

```
                 Интернет
                    │
        ┌───────────┴────────────┐   Yandex Cloud: VPC + группа безопасности
        │ :80                    │ :3000
┌───────▼────────┐        ┌──────▼─────┐
│ frontend       │        │ Grafana    │
│ React + Nginx  │        └──────┬─────┘
└───────┬────────┘               │
        │ /api                   ▼
┌───────▼────────┐  метрики ┌────────────┐
│ backend        │◄─────────│ Prometheus │
│ Spring Boot    │          └──────┬─────┘
└───────┬────────┘                 │
        │ JDBC                     ▼
┌───────▼────────┐        ┌───────────────────┐
│ PostgreSQL     │◄───────│ postgres-exporter │
└────────────────┘        └───────────────────┘
```

### Логические единицы

| № | Единица | Технология | Назначение |
|---|---------|------------|------------|
| 1 | Frontend | React 18, Vite, Nginx | Интерфейс пользователя; Nginx отдаёт статику и проксирует `/api` |
| 2 | Backend | Java 21, Spring Boot 3 | REST API, бизнес-логика, защита от двойного бронирования |
| 3 | База данных | PostgreSQL 16 | Хранение ресурсов и бронирований |
| 4 | Сбор метрик | Prometheus, postgres-exporter | Опрос метрик backend и базы данных |
| 5 | Визуализация | Grafana | Готовый дашборд «Система бронирования: обзор» |
| 6 | Инфраструктура | Yandex Cloud, Terraform, Docker Compose | ВМ, сеть, группа безопасности, автозапуск |

## Структура репозитория

```
backend/            Spring Boot (Maven)
frontend/           React (Vite) + конфигурация Nginx
monitoring/         prometheus.yml, источники данных и дашборд Grafana
deploy/terraform/   инфраструктура Yandex Cloud
docker-compose.yml  запуск всех сервисов
```

## Локальный запуск

Нужны Docker и Docker Compose.

```bash
cp .env.example .env        # затем задайте свои пароли в .env
docker compose up -d --build
```

- приложение: http://localhost/
- Grafana: http://localhost:3000/ (логин и пароль из `.env`)
- Prometheus: http://localhost:9090/

Первая сборка занимает несколько минут (загрузка зависимостей Maven и npm).

Тесты backend: `cd backend && mvn test` (используется встроенная база H2).

## Развёртывание в Yandex Cloud

Понадобятся: аккаунт Yandex Cloud с платёжным аккаунтом, [yc CLI](https://yandex.cloud/ru/docs/cli/quickstart),
[Terraform](https://yandex.cloud/ru/docs/tutorials/infrastructure-management/terraform-quickstart) и SSH-ключ
(`ssh-keygen -t ed25519`).

1. Загрузите проект в публичный git-репозиторий (например, GitHub).
2. Подготовьте переменные:

   ```bash
   cd deploy/terraform
   cp terraform.tfvars.example terraform.tfvars
   # в terraform.tfvars укажите cloud_id, folder_id и repo_url
   export YC_TOKEN=$(yc iam create-token)
   ```

3. Создайте инфраструктуру:

   ```bash
   terraform init
   terraform apply
   ```

   Terraform создаёт сеть, подсеть, группу безопасности (порты 22, 80, 3000) и ВМ
   (2 vCPU, 4 ГБ, Ubuntu 22.04). При первом старте cloud-init устанавливает Docker,
   клонирует репозиторий, генерирует `.env` со случайными паролями и запускает
   `docker compose up -d --build`. Через 5–10 минут приложение доступно по адресу
   из вывода `app_url`.

4. Пароль Grafana сгенерирован на ВМ:

   ```bash
   ssh deploy@<IP> "grep GRAFANA /opt/booking-system/.env"
   ```

Если `repo_url` не задан, ВМ создаётся только с Docker. Тогда скопируйте проект вручную
(`scp -r` или `git clone`), создайте `.env` и выполните `docker compose up -d --build`.

Удаление всех ресурсов: `terraform destroy`.

## Проверка работоспособности

Замените `<IP>` на адрес из `terraform output external_ip` (локально — `localhost`).

1. **Состояние сервисов.** На ВМ: `cd /opt/booking-system && docker compose ps` — шесть
   контейнеров в состоянии `running`, у `postgres` статус `healthy`.
2. **Backend и база данных.** `curl http://<IP>/api/health` → `{"status":"UP", ...}`
   с компонентом `db` в состоянии `UP`.
3. **Список ресурсов.** `curl http://<IP>/api/resources` → JSON с четырьмя помещениями.
4. **Интерфейс.** Откройте `http://<IP>/`, выберите помещение, дату и свободный слот,
   введите имя и e-mail, нажмите «Забронировать» — появится подтверждение с номером,
   слот станет недоступным.
5. **Защита от двойного бронирования.** Повторите запрос на то же время:

   ```bash
   curl -i -X POST http://<IP>/api/bookings -H 'Content-Type: application/json' -d '{
     "resourceId": 1, "customerName": "Тест", "customerEmail": "test@example.com",
     "startTime": "2026-12-01T10:00:00", "endTime": "2026-12-01T11:00:00"}'
   ```

   Первый вызов → `201 Created`, второй → `409 Conflict` («Это время уже занято»).
   Дата должна быть в будущем.
6. **Отмена.** В разделе «Мои бронирования» введите e-mail, нажмите «Отменить» — статус
   изменится на «Отменено», слот снова свободен.
7. **Prometheus.** Через SSH-туннель `ssh -L 9090:localhost:9090 deploy@<IP>` откройте
   http://localhost:9090/targets — цели `backend`, `postgres`, `prometheus` в состоянии `UP`.
8. **Grafana.** `http://<IP>:3000/` → Dashboards → Booking → «Система бронирования: обзор».
   После действий из пп. 4–6 растут счётчики «Создано бронирований», «Отменено»,
   «Отказов (время занято)» и график запросов к API.
9. **Отказоустойчивость.** `docker compose stop backend` — в Grafana панель «Backend»
   переходит в `DOWN`; после `docker compose start backend` возвращается в `UP`.
   Данные сохраняются после `docker compose restart` (том `postgres-data`).

## API

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/api/health` | Состояние backend и базы данных |
| GET | `/api/resources` | Список помещений |
| POST | `/api/resources` | Добавить помещение (заголовок `X-Admin-Token`) |
| GET | `/api/resources/{id}/availability?date=ГГГГ-ММ-ДД` | Часовые слоты и их занятость |
| POST | `/api/bookings` | Создать бронирование |
| GET | `/api/bookings?email=` | Бронирования пользователя |
| GET | `/api/bookings/{id}` | Одно бронирование |
| DELETE | `/api/bookings/{id}?email=` | Отменить бронирование |

Ошибки возвращаются в виде `{"detail": "текст"}`.

Правила: бронирование целыми часами, в пределах одного дня, с 9:00 до 21:00, только на
будущее время. Пересечения исключаются блокировкой строки ресурса (`SELECT … FOR UPDATE`)
внутри транзакции.

## Ограничения учебного проекта

- Нет регистрации пользователей: бронирования привязаны к e-mail.
- HTTP без TLS; для реальной эксплуатации нужен домен и сертификат.
- Все сервисы на одной ВМ; база данных не вынесена в управляемый сервис.
