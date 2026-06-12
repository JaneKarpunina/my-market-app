## my-market-app

### Как запускать проект

- В терминале запустить docker контейнер redis командой docker run -p 6379:6379 -d cr.yandex/mirror/redis:latest
- чтобы запустить keycloak нужно из корня проекта выполнить команду docker-compose -f docker-compose-keycloak.yml up -d
- чтобы настроить keycloak выполните следющие действия:
  1. Откройте http://localhost:8085, нажмите Administration Console и введите логин/пароль (admin / admin).
  2. В левом верхнем углу нажмите на раскрывающийся список Master и нажмите Create Realm. Назовите его my-shop-realm.
  3. Перейдите в раздел Clients (в левом меню) и нажмите Create client.Настройте клиент для Витрины:Client ID: shop-client. Нажмите Next.
  4. Capability config: Выключите переключатель Standard flow. Включите переключатель Service accounts roles (это активация Client Credentials Flow). Нажмите Save.
  5. Перейдите во вкладку Credentials появившегося клиента. Скопируйте Client Secret. Это пароль, с помощью которого Витрина будет подтверждать свою личность перед Keycloak при запросах к платежам.
  6. Вставьте Client Secret в проперти spring.security.oauth2.client.registration.payment-service-client.client-secret в файле application.properties модуля storefront-app
- из корня проекта запустить mvn spring-boot:run -pl payment-service и mvn spring-boot:run -pl storefront-app
- Приложение открыть по адресу http://localhost:8080

### Как запускать тесты 

Из корня проекта в терминале запустить mvn test

### Как добавить товары в приложение

Нужно зайти на страницу http://localhost:8080/add-item
