## my-market-app

### Как запускать проект

- В терминале запустить docker контейнер redis командой docker run -p 6379:6379 -d cr.yandex/mirror/redis:latest 
- из корня проекта запустить mvn spring-boot:run -pl payment-service и mvn spring-boot:run -pl storefront-app
- Приложение открыть по адресу http://localhost:8080

### Как запускать проект с помощью docker compose

- Из корня проекта выполнить команду mvn clean package
- Из корня проекта выполнить команду docker compose up --build

### Как запускать тесты 

Из корня проекта в терминале запустить mvn test

### Как добавить товары в приложение

Нужно зайти на страницу http://localhost:8080/add-item
