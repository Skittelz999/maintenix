# Maintenix

Maintenix samlar fastigheter och arbetsorder i ett webbaserat gränssnitt. Backend är byggd med Spring Boot och PostgreSQL; frontend med React och Vite.

## Kör lokalt

1. Starta databasen: `docker compose up -d`.
2. Sätt `BOOTSTRAP_ADMIN_EMAIL` och `BOOTSTRAP_ADMIN_PASSWORD` i terminalen och starta backend från `backend` med `mvn spring-boot:run`. Ett administratörskonto skapas bara om databasen ännu saknar användare.
3. Kör `npm install` och `npm run dev` från `frontend`.
4. Öppna adressen som Vite skriver ut. Logga in som administratör och skapa en fastighet, en hyresgäst och en tekniker. Låt hyresgästen skapa en arbetsorder, tilldela den till teknikern och stäng ärendet efter att teknikern markerat det färdigt.

Mer information om gränssnittet finns i [frontend/README.md](frontend/README.md).
