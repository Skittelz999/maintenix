# Maintenix

[![CI](https://github.com/Skittelz999/maintenix/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Skittelz999/maintenix/actions/workflows/ci.yml)

Maintenix samlar fastigheter och arbetsorder i ett webbaserat gränssnitt. Backend är byggd med Spring Boot och PostgreSQL; frontend med React och Vite.

## CI

GitHub Actions kör vid push till `main` och pull requests mot `main`. CI verifierar backendens hela testsvit med Java 21 och Testcontainers, frontendens produktionsbygge med Node.js 24 LTS samt backendens Docker-image. Backendtester och frontendbygge körs parallellt; Docker-bygget startar först när backendtesterna lyckats. Inga egna repository secrets behövs, och inga images publiceras eller driftsätts.

## Kör lokalt

1. Starta databasen: `docker compose up -d`.
2. Sätt `BOOTSTRAP_ADMIN_EMAIL` och `BOOTSTRAP_ADMIN_PASSWORD` i terminalen och starta backend från `backend` med `mvn spring-boot:run`. Ett administratörskonto skapas om den angivna e-postadressen saknas, även om andra användare redan finns. Befintliga konton och lösenord ändras aldrig automatiskt.
3. Kör `npm install` och `npm run dev` från `frontend`.
4. Öppna adressen som Vite skriver ut. Logga in som administratör och skapa en fastighet, en hyresgäst och en tekniker. Låt hyresgästen skapa en arbetsorder, tilldela den till teknikern och stäng ärendet efter att teknikern markerat det färdigt.

Mer information om gränssnittet finns i [frontend/README.md](frontend/README.md).

## Backend i produktion

Aktivera `application-prod.properties` med `SPRING_PROFILES_ACTIVE=prod`. Sätt `DB_URL` (en PostgreSQL JDBC-URL), `DB_USERNAME`, `DB_PASSWORD` och `JWT_SECRET` via driftmiljöns hemlighetshantering. Produktionsprofilen har inga reservvärden för dessa variabler. Använd en slumpmässigt genererad JWT-hemlighet med minst 32 byte och samma värde på alla instanser. `PORT` är valfri och har standardvärdet `8080`. Den lokala konfigurationen är oförändrad.

Terminera HTTPS i en betrodd reverse proxy/load balancer och tillåt endast trafik därifrån till backend. Produktionsprofilen använder Tomcats stöd för `X-Forwarded-For` och `X-Forwarded-Proto`; kontrollera proxyernas betrodda adresser och att klientens vidarebefordrade headers hanteras säkert. Se [Spring Boots proxydokumentation](https://docs.spring.io/spring-boot/how-to/webserver.html). Använd TLS med servercertifikatverifiering för databasanslutningen och håll databasen privat. Flyway kör migreringar vid start och Hibernate validerar schemat.

Vid kommande AWS-deployment ska load balancern ha HTTPS på port 443 med ett giltigt certifikat; eventuell publik port 80 ska endast omdirigera till HTTPS. Backendens port 8080 ska vara privat och dess security group endast tillåta trafik från load balancerns security group. Databasen ska endast tillåta anslutningar från backend. Applikationens interna HTTP-anslutning och HTTP-health check förutsätter denna nätverksgräns; backend ska inte exponeras direkt mot internet.

Behåll `server.forward-headers-strategy=native` och Tomcats filtrering av betrodda proxyadresser. Anpassa vid behov `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` till deploymentens proxyadresser; sätt aldrig ett tomt värde, eftersom det litar på alla avsändare. Proxy/load balancer ska sätta korrekt `X-Forwarded-Proto` och hantera `X-Forwarded-For` så att klientens egna headers inte betraktas som betrodda. HTTPS-omdirigering sker i load balancern, så interna health checks kan fortsätta använda HTTP.

### PostgreSQL TLS

Använd följande form för `DB_URL` i produktion, med den riktiga RDS-endpointen som värdnamn:

```text
jdbc:postgresql://<rds-endpoint>:5432/maintenix?sslmode=verify-full&sslrootcert=/app/certs/rds-ca-bundle.pem
```

`verify-full` kontrollerar både certifikatkedjan och värdnamnet. `require` krypterar men verifierar inte serverns identitet; standardläget `prefer` kan dessutom falla tillbaka till okrypterat. Hämta aktuell CA-bundle från [AWS RDS-certifikatdokumentationen](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html) och gör den tillgänglig skrivskyddad på angiven sökväg i containern, läsbar för UID 10001. CA-bundlen är publik; lösenord och privata nycklar ska aldrig byggas in i imagen. För lokal Dockerkörning kan filen bind-mountas med `--mount type=bind,source=<absolut-ca-fil>,target=/app/certs/rds-ca-bundle.pem,readonly`.

Kontrollera också att RDS kräver TLS genom `rds.force_ssl=1`. Det ersätter inte klientens certifikat- och värdnamnsverifiering. Samma JDBC-URL används av Flyway. Innan publik drift ska anslutningen provas med rätt CA och därefter verifieras att fel CA eller värdnamn avvisas. Se [pgJDBC:s SSL-dokumentation](https://jdbc.postgresql.org/documentation/ssl/) och [RDS PostgreSQL TLS](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/PostgreSQL.Concepts.General.SSL.html). Lokal utveckling och Testcontainers använder fortsatt sin lokala databas utan TLS; produktions-URL:n behöver därför konfigureras uttryckligen enligt ovan.

### JWT och lokala hemligheter

JWT måste ha applikationens issuer (`maintenix`) och en giltig expiration. Signatur, HS256 och tidsvalidering behålls. Använd en separat slumpmässig `JWT_SECRET` för varje miljö; dela inte nyckeln med andra applikationer. Aktuell användarstatus och roll kontrolleras i databasen för varje autentiserat JWT-anrop.

Git ignorerar `.env`, `.env.*`, `*.env`, lokala `secrets`/`.secrets`/`.aws`-kataloger, privata nyckelfiler, keystores och `application-local.*`. Dockerbyggkontexten utesluter också dessa filer. Ignorering skyddar mot vanlig oavsiktlig incheckning, men stoppar inte `git add -f`, redan spårade filer eller hemligheter inskrivna i vanlig källkod. Granska därför alltid staged diff; lagra produktionsuppgifter i driftmiljöns hemlighetshantering.

För första administratören kan `BOOTSTRAP_ADMIN_EMAIL` och `BOOTSTRAP_ADMIN_PASSWORD` sättas tillsammans. E-postadressen trimmas och normaliseras till gemener. Finns adressen redan lämnas hela kontot orört, även om det har en annan roll. Utelämna båda variablerna för att stänga av bootstrap; om bara en anges avbryts starten. Ta bort bootstrap-uppgifterna från driftmiljön efter att kontot skapats. Kör första bootstrap på en instans: samtidiga försök att skapa samma adress kan ge ett unikhetsfel, men skriver inte över ett befintligt konto.

## Backend med Docker

Kör hela testsviten från `backend` med `./mvnw test` (Windows: `.\mvnw.cmd test`). Testerna använder PostgreSQL via Testcontainers och kräver Docker. Bygg sedan från projektroten:

```sh
docker build -t maintenix-backend:local ./backend
```

Imagen byggs med Java 21 och Maven Wrapper och kör endast JAR-filen med Java 21 JRE som en användare utan root-behörighet. Testkörningen hoppas över under själva imagebygget eftersom Testcontainers behöver en separat Docker-miljö; kör alltid testkommandot ovan före bygget.

Containern använder `SPRING_PROFILES_ACTIVE=prod` som standard. Sätt `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` och `JWT_SECRET` i körmiljön enligt produktionsavsnittet ovan. Databasens värdnamn måste kunna nås från containern; `localhost` avser containern själv. Om variablerna redan finns i terminalmiljön kan imagen startas så här:

```sh
docker run --rm -p 127.0.0.1:8080:8080 --env SPRING_PROFILES_ACTIVE=prod --env DB_URL --env DB_USERNAME --env DB_PASSWORD --env JWT_SECRET maintenix-backend:local
```

`PORT` är valfri (standard `8080`); ändras den måste även portmappning och plattformens health check uppdateras. Bootstrap-variablerna är valfria och skickas endast in när ett första administratörskonto behövs. Inga hemligheter ska anges i Dockerfile eller byggargument.

`GET /actuator/health` är publik och returnerar endast övergripande status, exempelvis `{"status":"UP"}`. Databasens hälsa ingår; otillgänglig databas kan därför ge HTTP 503. `/actuator/info` kräver en giltig JWT med rollen `ADMIN`; automatiska info-bidrag är avstängda. Övriga Actuator-endpoints är avstängda och deras sökvägar nekas.

Vid kommande AWS/ECS-driftsättning ska ALB:s health check använda HTTP GET `/actuator/health` på applikationens port och förvänta HTTP 200. Ge starten tid för JVM, databasanslutning och Flyway-migreringar. Ingen Docker `HEALTHCHECK` ingår; kontrollen utförs av plattformen utan att extra HTTP-verktyg installeras i runtime-imagen.
