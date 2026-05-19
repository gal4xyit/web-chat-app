# SUPER CHACH

Система веб-чату для інтегрованої соціальної взаємодії

## Передреквізити

Перед тим, як почати, переконайтеся, що у вас встановлено:

*   [Git](https://git-scm.com/)
*   [Docker](https://www.docker.com/get-started)
*   [Docker Compose](https://docs.docker.com/compose/install/)

## Використання

1.  **Клонуйте репозиторій:**
    ```bash
    git clone https://github.com/gal4xyit/web-chat-app
    cd web-chat-app
    ```

2.  **Запустіть Keycloak та базу даних:**
    Використовуйте Docker Compose для запуску сервісів Keycloak та бази даних у фоновому режимі (`-d`):
    ```bash
    docker-compose up -d keycloak db
    ```

3.  **Налаштуйте користувачів у Keycloak:**
    *   Відкрийте панель адміністратора Keycloak: [http://localhost:8180](http://localhost:8180)
    *   Увійдіть, використовуючи облікові дані за замовчуванням:
        *   **Логін:** `admin`
        *   **Пароль:** `admin`
    *   Перейдіть до `chat-app-realm` (у Manage Realms)
    *   Створіть необхідних користувачів.
    *   Призначте користувачам ролі `USER` та/або `ADMIN` відповідно до потреб вашого застосунку.

4.  **Запустіть основний застосунок**

6.  **Перевірте роботу чату:**
    *   Відкрийте чат у вашому браузері: [http://localhost:8080](http://localhost:8080)

7.  **API Документація (Swagger):**
    *   Для перегляду доступних ендпоінтів API та їх опису, перейдіть за адресою:
        [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## Управління Базою Даних (Додатково)

Для прямого доступу до бази даних PostgreSQL через термінал `psql` всередині Docker контейнера, виконайте:

```bash
docker exec -it dbContainer psql -U admin -d chatDB
```

Або використайте СУБД на ваш вибір(user: `admin`, pass: `admin123`)
