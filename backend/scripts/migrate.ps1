$ErrorActionPreference = 'Stop'

Set-Location $PSScriptRoot\..

mvn -pl db-migrations -Dflyway.url=jdbc:postgresql://localhost:5432/aieap -Dflyway.user=aieap -Dflyway.password=aieap flyway:migrate