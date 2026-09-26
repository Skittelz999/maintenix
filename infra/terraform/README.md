# Maintenix AWS network och PostgreSQL

En fristående Terraform-root för nätverksbasen, utan externa moduler. Standardvärdena skapar ett nytt IPv4-VPC i `eu-north-1` med ett publikt och ett privat subnet i vardera `eu-north-1a` och `eu-north-1b`.

Detta skapar VPC, subnets, internet gateway, route tables, security groups och en liten PostgreSQL RDS-instans med DB subnet group och TLS-parametergrupp. RDS skapar och hanterar sitt masterlösenord i Secrets Manager. Befintligt ECR och GitHub OIDC ändras inte. Ingen applikation, ECS, ALB, ACM, DNS, NAT Gateway eller VPC endpoint skapas.

## Nätverksgränser

| Källa | Mål | Tillåten trafik |
| --- | --- | --- |
| Internet, IPv4 | ALB security group | TCP 443 |
| ALB security group | ECS security group | TCP 8080, inklusive health checks |
| ECS security group | RDS security group | TCP 5432 |
| ECS security group | Publika HTTPS-endpoints, IPv4 | Utgående TCP 443 |

Endast publika subnet har en default route till internet gateway. Privata subnet är avsedda för RDS och har en egen route table per AZ med endast lokal VPC-routing. Automatisk tilldelning av publika IP-adresser på subnet-nivå är avstängd; framtida Fargate-tasks får i stället publika IP-adresser genom tjänstens uttryckliga `assign_public_ip = true`. ALB hanterar sina publika adresser själv. VPC:s default security group töms på regler.

ALB får endast initiera utgående anslutningar till ECS på 8080. ECS får ansluta till RDS på 5432 och till IPv4-destinationer på TCP 443 för exempelvis ECR, S3-image-lager och CloudWatch Logs. HTTPS-regeln är inte en domänbegränsning: även andra destinationer på TCP 443 är tillåtna. RDS har inga regler för att initiera utgående trafik. Security groups är stateful, så svar på tillåtna anslutningar fungerar ändå. AWS:s VPC-resolver påverkas inte av vanliga security-group-regler. Se [AWS security-group-dokumentation](https://docs.aws.amazon.com/vpc/latest/userguide/security-group-rules.html).

Ingen port 80 öppnas. Om HTTP→HTTPS-omdirigering senare önskas ska port 80 öppnas endast på ALB och dess listener endast omdirigera till HTTPS. Backendens interna port 8080 ska fortsätta vara privat.

## Enkel arkitektur för portfolio/demo

Framtida ECS/Fargate-tasks placeras i de befintliga publika subneten med `assign_public_ip = true` och ECS-gruppen. Då når de AWS:s publika HTTPS-endpoints genom internet gateway utan NAT Gateway eller VPC endpoints. Detta undviker ytterligare gateway-/endpoint-resurser och deras kostnader för en liten demo. Det gör inte driften kostnadsfri; budgetera även för publika IPv4-adresser och de framtida ALB-, Fargate- och RDS-resurserna. Se [AWS:s Fargate-nätverksguide](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-outbound.html).

En publik task-IP öppnar inte port 8080 för internet. ECS-gruppen accepterar fortfarande endast TCP 8080 från ALB-gruppen. ALB använder taskens privata IP som target (`target_type = "ip"`) och är den enda publika ingången till applikationen. Tasks ansluter till RDS inom VPC:t; databastrafiken går inte via internet gateway.

RDS använder endast privata subnet, RDS-gruppen och uttryckligen `publicly_accessible = false`. Endast ECS-gruppen får ansluta på TCP 5432. Lägg inte till bredare security groups på ECS eller RDS: regler från flera grupper adderas. Framtida ECS måste fortfarande konfigureras enligt ovan när tjänsten skapas. Inga NAT Gateways, VPC endpoints eller extra infrastrukturlager behövs för denna design.

## PostgreSQL för demo

| Inställning | Standard |
| --- | --- |
| Motor | PostgreSQL 17, automatiska minor-uppgraderingar |
| Instans | `db.t4g.micro`, Single-AZ |
| Lagring | 20 GiB gp3, krypterad med AWS-hanterad KMS-nyckel |
| Databas / masteranvändare | `maintenix` / `maintenix_admin` |
| Backuper | En dags automatiska backuper medan instansen finns |
| TLS | `rds.force_ssl = 1` i parametergruppen `postgres17` |
| Kostnadskontroll | Ingen lagringsautoskalning, Multi-AZ, replika, Performance Insights eller Enhanced Monitoring |
| Radering | Deletion protection av; ingen slutlig snapshot; automatiska backuper tas bort |

Variablerna `db_name`, `db_username`, `db_instance_class` och `db_allocated_storage` finns i `terraform.tfvars.example`. Använd inte SQL-reserverade namn för masteranvändaren. Instansklass och motorkombination måste finnas i regionen. PostgreSQL-majorversionen och parametergruppens family hålls tillsammans i `database.tf`; RDS väljer aktuell standard-minor inom 17 vid skapandet. Betald RDS Extended Support är avstängd, så uppgradera majorversionen innan standardstödet tar slut. Lagring kan höjas men inte krympas på en befintlig instans. Liten burstable-instans innebär begränsad kapacitet; följ CPU credits och ledig lagring.

Detta är inte hög tillgänglighet: nätverket spänner över två AZ men databasen kör i en AZ. Underhåll och fel kan ge avbrott. RDS, lagring och Secrets Manager medför kostnader även när ingen applikation använder dem; anta inte att kontot omfattas av free tier. Kontrollera [RDS PostgreSQL-priser](https://aws.amazon.com/rds/postgresql/pricing/) och [Secrets Manager-priser](https://aws.amazon.com/secrets-manager/pricing/) för ditt konto och `eu-north-1` före apply.

`manage_master_user_password = true` låter RDS skapa lösenordet direkt i Secrets Manager. Terraform varken genererar eller läser lösenordet; ingen lösenordsvariabel eller secret-version-data-source behövs. Endast ARN exponeras som output. Använd AWS-hanterade krypteringsnycklar; ingen extra KMS-nyckel skapas. RDS roterar som standard lösenordet var sjunde dag och tar bort den hanterade hemligheten när databasen raderas. Se [RDS och Secrets Manager](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/rds-secrets-manager.html).

## Förutsättningar och autentisering

- Terraform `>= 1.9, < 2.0`, AWS CLI v2 och åtkomst till avsett AWS-konto.
- AWS-provider låses med `.terraform.lock.hcl`; behåll filen i Git. Uppgraderingar görs uttryckligen med `terraform init -upgrade` och granskas.
- Använd en kortlivad IAM Identity Center/SSO-session eller en redan autentiserad CloudShell-session. Lägg inga access keys i Terraform, tfvars eller GitHub secrets.
- Kör med en separat, godkänd infrastrukturroll med rätt att hantera VPC-resurser, RDS-instans, DB subnet group, parametergrupp och taggar. RDS-integrationen kräver även `secretsmanager:CreateSecret`, `secretsmanager:TagResource` och `kms:DescribeKey`; kontots första RDS-instans kan kräva rätt att skapa RDS service-linked role. Använd AWS-dokumentationen ovan för kontots exakta IAM-policy. ECR-publiceringsrollen ska inte breddas för detta steg. Rätt att läsa secret-värdet ges separat och endast till de operatörer/körroller som behöver det.

Exempel för en redan konfigurerad SSO-profil:

```sh
aws sso login --profile maintenix-infra
aws sts get-caller-identity --profile maintenix-infra
```

Sätt sedan `AWS_PROFILE` till `maintenix-infra` i terminalen: `export AWS_PROFILE=maintenix-infra` i Bash eller `$env:AWS_PROFILE = "maintenix-infra"` i PowerShell. Provider hämtar sessionen från AWS:s standardkedja; inga credentials anges i koden.

## Init, kontroll och plan

Kör från `infra/terraform`. Kopiera `terraform.tfvars.example` till `terraform.tfvars` och anpassa projekt, miljö och CIDR om det behövs. Standardvärdena är redan kompletta för `eu-north-1`. CIDR måste vara icke-överlappande, ligga inom VPC-CIDR och inte krocka med nät som ska anslutas senare. AZ-nycklarna måste motsvara minst två tillgängliga AZ i kontots valda region. Variabler validerar format, region och minsta antal AZ; AWS kontrollerar faktisk AZ-tillgänglighet och CIDR-överlapp/innehåll vid skapandet.

```sh
terraform init
terraform fmt -check -recursive
terraform validate
terraform test
terraform plan "-out=maintenix.tfplan"
terraform show maintenix.tfplan
```

`terraform test` använder en mockad AWS-provider och testar nätverksgränserna samt RDS-konfigurationen utan credentials eller AWS-anrop. Testets `apply` sker endast mot mocken. Det ersätter inte en riktig `plan` i ditt konto. Kontrollera även regional tillgänglighet före apply:

```sh
aws rds describe-orderable-db-instance-options --region eu-north-1 --engine postgres --db-instance-class db.t4g.micro --query 'OrderableDBInstanceOptions[].EngineVersion' --output table
```

Resultatet ska inkludera PostgreSQL 17-versioner. Anpassa kommando och tfvars tillsammans om du väljer annan region eller klass. Planen ska bara innehålla nätverket och de tre RDS-resurserna ovan; RDS skapar secret som en del av instansskapandet. Om nätverket redan är applicerat, behåll samma state och kontrollera att subnets och säkerhetsregler inte ersätts. Granska alltid eventuella databasersättningar eftersom demo-konfigurationen inte skyddar data vid radering.

## Apply och outputs

```sh
terraform apply maintenix.tfplan
terraform output
```

Apply använder exakt den sparade planen och frågar inte igen om godkännande. Kör det bara efter att planen granskats. RDS-skapandet kan ta flera minuter. Outputs är `vpc_id`, `public_subnet_ids`, `private_subnet_ids`, `security_group_ids`, `db_identifier`, `db_address`, `db_port`, `db_name` och `db_master_secret_arn`. Subnet-ID:n är grupperade per AZ; SG-ID:n har nycklarna `alb`, `ecs`, `rds`. Inga lösenord returneras.

Verifiera därefter i AWS Console att endast publika route tables har `0.0.0.0/0 → igw`, att privata subnet saknar publika IP-inställningar och internetroute, samt att de tre security groups har exakt den avsedda trafikkedjan. Ingen applikation ska köras av detta steg.

Kontrollera också RDS: status `Available`, Public access `No`, Storage encrypted `Yes`, rätt private subnet group/RDS security group samt TLS-parametergruppens status `in-sync`. `rds.force_ssl` anges med `pending-reboot`; vid en senare ändring av en befintlig databas kan en planerad reboot krävas. Vid ny instans används gruppen från starten.

## Anslutning och TLS

Databasen kan inte nås direkt från internet, en vanlig laptop eller en vanlig publik CloudShell-session. En klient behöver nätverksåtkomst inom VPC:t och den befintliga ECS-gruppen. Ingen sådan klient skapas i detta steg. Öppna inte 5432 för din publika IP för att testa; anslutningsprovet görs när en godkänd klient eller framtida ECS-task finns.

Hämta aktuell RDS CA-bundle enligt [AWS TLS-dokumentation](https://docs.aws.amazon.com/AmazonRDS/latest/UserGuide/UsingWithRDS.SSL.html). Framtida backend ska använda:

```text
DB_URL=jdbc:postgresql://<db_address>:5432/<db_name>?sslmode=verify-full&sslrootcert=/app/certs/rds-ca-bundle.pem
```

Använd DNS-hostnamnet från `db_address`, inte en IP-adress. `verify-full` verifierar CA-kedjan och värdnamnet; serverns `rds.force_ssl` kräver kryptering men ersätter inte klientens certifikatkontroll. CA-filen ska vara läsbar för containerns UID 10001. Varken CA-montering eller applikationsdeployment implementeras här.

En behörig operatör kan hitta de hanterade masteruppgifterna i Secrets Manager med ARN från `terraform output -raw db_master_secret_arn`. Skriv inte ut dem i CI-loggar eller lägg dem i Terraform. Från en godkänd VPC-klient kan `psql` användas med interaktiv lösenordsprompt:

```sh
psql "host=<db_address> port=5432 dbname=<db_name> user=<db_username> sslmode=verify-full sslrootcert=/path/to/rds-ca-bundle.pem" -W
```

Verifiera med `SHOW rds.force_ssl;` och `SELECT ssl FROM pg_stat_ssl WHERE pid = pg_backend_pid();`. En anslutning med `sslmode=disable` ska avvisas; fel CA/hostnamn ska också avvisas av klienten. Dessa tester kräver riktig AWS-anslutning och ingår inte i de mockade Terraform-testerna.

Masterkontot är för administration och initiala migreringar. Ta ställning till en separat databasroll för applikationen vid ECS-steget. Om framtida ECS får databasuppgifter som miljövariabler via Secrets Manager uppdateras de inte automatiskt i redan körande tasks när lösenordet roteras; ordna omläsning eller ersättning av tasks innan applikationen tas i drift. Ingen egen rotationsautomation eller applikationsintegration läggs till nu.

## State och destroy

Denna root använder lokal state för en första manuell körning av en operatör. State, sparade planer, `.terraform/` och riktiga tfvars ignoreras av Git; exempel och provider-lockfil checkas in. State och planer kan innehålla känslig information och ska förvaras säkert. Behåll en säker backup av state; att radera state raderar inte AWS-resurserna. Byt inte `environment` i samma state för att skapa en parallell miljö.

Innan flera personer eller CI kör apply, migrera state till en separat konfigurerad krypterad remote backend med låsning och begränsad åtkomst. Ingen state-bucket eller extra IAM-resurs skapas här. Kör inte samtidiga apply från olika lokala state-kopior.

För att ta bort databasen och nätverksbasen, från samma katalog och med samma state, variabler och AWS-konto:

```sh
terraform plan -destroy "-out=destroy.tfplan"
terraform show destroy.tfplan
terraform apply destroy.tfplan
```

Alternativt kan `terraform destroy` användas med dess interaktiva bekräftelse. **Demo-databasen och dess automatiska backuper raderas utan slutlig snapshot. RDS tar även bort sin hanterade hemlighet.** Ta en manuell snapshot eller export före destroy om data ska sparas; manuella snapshots kan medföra kvarvarande kostnader och raderas separat. När framtida workloads använder nätverket måste deras beroenden först avvecklas eller hanteras i en samordnad plan. Destroy här tar inte bort det separat skapade ECR-repositoryt eller OIDC-rollen.
