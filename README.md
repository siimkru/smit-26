# SMIT IT teenuste info agent

See projekt on Spring Booti ja Spring AI põhine IT-teenuste infoagendi alus. Praeguses etapis on olemas REST API leping, sisendi valideerimine, tervisekontroll ja OpenAI konfiguratsioon; teadmusbaasi agent ning OpenAI kutse lisatakse järgmises etapis.

Projekt kasutab Java 21, Spring Boot 3.4.5, Spring AI 1.0.0 ja Gradle Wrapperit.

## Käivitamine

Kopeeri `.env.example` väärtused oma lokaalsesse keskkonda ning määra tegelik `OPENAI_API_KEY` ainult siis, kui agenti hiljem kasutatakse. `OPENAI_MODEL` peab olema organisatsiooni poolt lubatud mudel. Praegune tervisekontroll ei vaja võtit.

```sh
./gradlew bootRun
curl http://localhost:8080/api/v1/health
```

Vastus on `{"status":"UP"}`. `POST /api/v1/agent/ask` võtab vastu `question` (kohustuslik, kuni 2000 tähemärki) ja valikulise `sessionId`-i. Agent ei ole veel rakendatud ning korrektne päring saab praegu HTTP 501; tühi, puuduv või liiga pikk `question` saab HTTP 400.

```sh
curl -X POST http://localhost:8080/api/v1/agent/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Kuidas taotleda ligipääsu GitLabile?"}'
```

## Konfiguratsioon

OpenAI võti tuleb keskkonnamuutujast `OPENAI_API_KEY`; seda ei salvestata reposse. Mudel ja temperatuur on seadistatavad vastavalt `OPENAI_MODEL` ja `OPENAI_TEMPERATURE`. Kuni agenti ei ole, on Spring AI mudelite automaatne loomine välja lülitatud, et tervisekontroll töötaks ka võtmeta. Küsimusi ega API võtmeid see alus logidesse ei kirjuta.

## Testimine

```sh
./gradlew test
```

Unit-testid ei vaja OpenAI võtit ega tee võrgukutseid. Gradle HTML raport paikneb `build/reports/tests/test/index.html` ja seda ei commitita.
