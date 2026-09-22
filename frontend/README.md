# Maintenix frontend

Gränssnitt för inloggning, fastigheter och arbetsorder.

## Starta lokalt

1. Starta PostgreSQL från projektroten: `docker compose up -d`.
2. Ange `BOOTSTRAP_ADMIN_EMAIL` och `BOOTSTRAP_ADMIN_PASSWORD` i din terminal. Starta sedan backend i `backend` med `mvn spring-boot:run`. När databasen saknar användare skapas ett administratörskonto. Miljövariablerna kan därefter tas bort; kontot finns kvar i databasen.
3. Kör `npm install` och `npm run dev` i `frontend`.
4. Öppna adressen som Vite visar (normalt `http://localhost:5173`).

Vite skickar `/api` vidare till backend på port 8080. Logga in med administratörskontot, skapa en fastighet och skapa sedan en hyresgäst under **Hyresgäster**. Välj fastigheten i samma formulär. Administratören kan också koppla en befintlig hyresgäst till fler fastigheter. Hyresgästen kan därefter logga in och skapa en arbetsorder för sin fastighet.

Administratörer ser alla fastigheter och arbetsorder, hyresgäster ser sina fastigheter och tillhörande arbetsorder, och tekniker ser sina tilldelade arbetsorder. Administratörer och hyresgäster kan skapa arbetsorder.

För att prova hela arbetsflödet: skapa en tekniker under **Tekniker**, tilldela en ny arbetsorder på sidan **Arbetsorder**, logga in som teknikern och välj **Starta arbete** följt av **Markera färdig**. Logga sedan in som administratör och välj **Stäng ärende**. Teknikern kan även pausa och återuppta arbetet. Lösenord till nya konton lämnas separat till användaren.

Inloggningen sparas i flikens `sessionStorage` tills fliken stängs eller token går ut. För produktionsdrift behöver frontend och backend serveras under samma ursprung, eller en motsvarande proxy för `/api`.
