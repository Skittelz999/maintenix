# Maintenix

Maintenix samlar fastigheter och arbetsorder i ett webbaserat gränssnitt. Backend är byggd med Spring Boot och PostgreSQL; frontend med React och Vite.

## Kör lokalt

1. Starta databasen: `docker compose up -d`.
2. Sätt `BOOTSTRAP_ADMIN_EMAIL` och `BOOTSTRAP_ADMIN_PASSWORD` i terminalen och starta backend från `backend` med `mvn spring-boot:run`. Ett administratörskonto skapas om den angivna e-postadressen saknas, även om andra användare redan finns. Befintliga konton och lösenord ändras aldrig automatiskt.
3. Kör `npm install` och `npm run dev` från `frontend`.
4. Öppna adressen som Vite skriver ut. Logga in som administratör och skapa en fastighet, en hyresgäst och en tekniker. Låt hyresgästen skapa en arbetsorder, tilldela den till teknikern och stäng ärendet efter att teknikern markerat det färdigt.

Mer information om gränssnittet finns i [frontend/README.md](frontend/README.md).

## Backend i produktion

Aktivera `application-prod.properties` med `SPRING_PROFILES_ACTIVE=prod`. Sätt `DB_URL` (en PostgreSQL JDBC-URL), `DB_USERNAME`, `DB_PASSWORD` och `JWT_SECRET` via driftmiljöns hemlighetshantering. Produktionsprofilen har inga reservvärden för dessa variabler. Använd en slumpmässigt genererad JWT-hemlighet med minst 32 byte och samma värde på alla instanser. `PORT` är valfri och har standardvärdet `8080`. Den lokala konfigurationen är oförändrad.

Terminera HTTPS i en betrodd reverse proxy/load balancer och tillåt endast trafik därifrån till backend. Produktionsprofilen använder Tomcats stöd för `X-Forwarded-For` och `X-Forwarded-Proto`; kontrollera proxyernas betrodda adresser och att klientens vidarebefordrade headers hanteras säkert. Se [Spring Boots proxydokumentation](https://docs.spring.io/spring-boot/how-to/webserver.html). Använd TLS med servercertifikatverifiering för databasanslutningen och håll databasen privat. Flyway kör migreringar vid start och Hibernate validerar schemat.

För första administratören kan `BOOTSTRAP_ADMIN_EMAIL` och `BOOTSTRAP_ADMIN_PASSWORD` sättas tillsammans. E-postadressen trimmas och normaliseras till gemener. Finns adressen redan lämnas hela kontot orört, även om det har en annan roll. Utelämna båda variablerna för att stänga av bootstrap; om bara en anges avbryts starten. Ta bort bootstrap-uppgifterna från driftmiljön efter att kontot skapats. Kör första bootstrap på en instans: samtidiga försök att skapa samma adress kan ge ett unikhetsfel, men skriver inte över ett befintligt konto.
