# SMIT IT teenuste info agent

See projekt on Spring Booti ja Spring AI põhine piiratud IT-teenuste infoagent. Agent kasutab OpenAI mudelit ainult otsustamiseks, millised allowlistitud tööriistaga leitud teadmusbaasi lõigud vastavad küsimusele. Avaliku faktilise vastuse, allikad ja viited koostab Java kood täpselt valideeritud teadmusbaasi sisust.

Projekt kasutab Java 21, Spring Boot 3.4.5, Spring AI 1.0.0 ja Gradle Wrapperit.

## Käivitamine

Määra keskkonnas `OPENAI_API_KEY` ja organisatsiooni poolt lubatud `OPENAI_MODEL`. `.env.example` sisaldab ainult näidisväärtusi; Spring Boot ei laadi `.env` faili ise, seega ekspordi väärtused shelli või kasuta IDE keskkonnaseadeid. Tervisekontroll käivitub ka võtmeta; agendipäring tagastab sel juhul HTTP 503.

```sh
./gradlew bootRun
curl http://localhost:8080/api/v1/health
```

Vastus on `{"status":"UP"}`. `POST /api/v1/agent/ask` võtab vastu `question` (kohustuslik, kuni 2000 tähemärki) ja valikulise `sessionId`-i. `sessionId` võib sisaldada 1–128 ASCII tähte, numbrit, alakriipsu või sidekriipsu. Tühi, puuduv või liiga pikk küsimus saab HTTP 400.

```sh
curl -X POST http://localhost:8080/api/v1/agent/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Kuidas taotleda ligipääsu GitLabile?"}'
```

Toetatud vastuses on `refused:false`, vähemalt üks valideeritud `sources` kirje ja vastuse tekstis viide kujul `[allikas: gitlab-access.md]`. Toetuseta või ohtlik päring tagastab HTTP 200 vastuse `refused:true`, tühja `sources` massiivi ja selge `refusalReason`-i. Mudeliteenuse puudumine või tõrge tagastab sanitiseeritud HTTP 503.

## Konfiguratsioon

OpenAI võti tuleb keskkonnamuutujast `OPENAI_API_KEY`; seda ei salvestata reposse. Mudel ja temperatuur on seadistatavad vastavalt `OPENAI_MODEL` ja `OPENAI_TEMPERATURE`. Spring AI mudelite automaatne loomine on välja lülitatud: rakendus loob piiratud `ChatClient`-i ise ainult siis, kui võti ja mudel on olemas, ning annab sellele ainult KB tööriistad.

Eestikeelne süsteemiprompt asub `src/main/resources/prompts/agent-system.txt`. Spring AI sõnumiloendis on süsteemijuhis `system` rollis ning küsimus ja sessiooniajalugu eraldi `user`/`assistant` rollides. Mudel tagastab sisemise otsuse ja lõigu-ID-d, mitte avalikku vastuseteksti.

## Teadmusbaas ja tööriistad

Teadmusbaas sisaldab viit sünteetilist Markdowni dokumenti kataloogis `src/main/resources/knowledge-base/`: GitLabi ligipääs, Kubernetesi juurutamine, CI/CD, koodireview ja ligipääsude haldus. Rakendus loeb käivitamisel ainult selle fikseeritud nimekirja classpath-ressursse ning hoiab tulemuse muutumatu mälustruktuurina.

Spring AI jaoks on registreeritud täpselt kaks read-only tööriista: `listTopics` ja `searchKnowledgeBase`. Otsingusõna on alati andmesisend, mitte failitee; teekujulised väärtused tagastavad tühja tulemuse. Tööriistad ei paku faililugemist, kirjutamist, käske ega väliseid süsteeme.

Iga päring kogub tööriistaga tegelikult tagastatud lõigud eraldi request-local ledger'isse. Ainult samas päringus leitud kanoonilisi ID-sid saab vastuses kasutada. Puuduv, väljamõeldud või varasema sessiooni ID põhjustab keeldumise. Valikuline sessioon hoiab mälus kuni neli valideeritud küsimuse-vastuse paari; see kaob restardil ning sama `sessionId` kasutajad jagavad konteksti. Sessiooniajalugu saadetakse järelküsimusel OpenAI-le, kuid seda ei käsitleta tõendina.

## Turvalisus ja OpenAI-le saadetavad andmed

Enne mudelikõnet blokeeritakse üle 2000 märgi pikkune sisend ning prompt injection'i, rolli ümberkirjutamise, prompti/tööriistade avaldamise, path traversal'i, destruktiivsete juhiste ja ilmsete saladuste mustrid. Tundliku sisendi kontroll hõlmab muu hulgas märgendatud paroole ja API võtmeid, bearer-tokeneid, privaatvõtmeid ning 11-kohalisi isikukoode. Turvalogisse kirjutatakse ainult keeldumise kategooria ja sisendi pikkus, mitte täielik küsimus ega leitud saladuse väärtus. Aktsepteeritud küsimus, kuni neli sama sessiooni varasemat küsimust ja valideeritud vastust, süsteemiprompt ning allowlistitud tööriistade skeemid/tulemused võidakse saata OpenAI-le. Ära saada agenti päris paroole, API võtmeid ega isikuandmeid.

Tööriista otsinguargument on piiratud 500 märgiga; traversal- ja absoluutsed failiteed lükatakse tagasi ning ühtegi kasutaja määratud teed ei avata. Piirangud: mustripõhine tundliku info ja rünnete tuvastus ei tuvasta kõiki variante; sessioonid on lokaalsed, mälupõhised ja autentimata; rate limiting puudub; teadmusbaasi otsing on väikese fikseeritud korpuse deterministlik märksõnaotsing.

## Testimine

```sh
./gradlew test
./gradlew integrationTest
```

Unit-testid ei vaja OpenAI võtit ega tee võrgukutseid. Need katavad API-01, API-02, API-03 ja SEC-07 stsenaariumid ning API valideerimist, turvafiltrit, rollide eraldust, KB otsingut ja allowlist'i, sessiooni järelkonteksti ning praeguse päringu allikate valideerimist.

`integrationTest` on eraldi Gradle task ja lähtekogum. See käivitab rakenduse juhuslikul lokaalsel pordil ning testib päris REST → agent → Spring AI → OpenAI voogu. Kaetud on API-04, UC-01–UC-13 ja SEC-01–SEC-06 ning SEC-08; testinimed säilitavad stsenaariumi ID-d ja kontrollivad käitumist, allikaid, viiteid ning keeldumisi, mitte mudeli täpset sõnastust. Iga SEC-test kontrollib nii ülesandes antud sisendi fail-fast keeldumist kui ka sama ründe-eesmärgiga parafraasi, mis jõuab tõendatult päris mudeligatewayni. Testiklass jäetakse vahele, kui `OPENAI_API_KEY` või `OPENAI_MODEL` puudub. Võtmega käivitamiseks ekspordi mõlemad väärtused enne taski käivitamist. Integratsiooniprofiil kasutab mudeliteülese ühilduvuse jaoks temperatuuri `1.0`; rakenduse tavakonfiguratsiooni `OPENAI_TEMPERATURE` jääb seejuures muutmata.

Eraldi HTML raportid tekivad asukohtades:

- unit-testid: `build/reports/tests/test/index.html`
- integratsioonitestid: `build/reports/tests/integrationTest/index.html`

Raportid asuvad ignoreeritud `build/` kataloogis ja neid ei commitita. GitHub Actions käivitab mõlemad taskid ning avaldab raportid eraldi `unit-test-html-report` ja `integration-test-html-report` artefaktidena. Kui OpenAI saladusi pole seadistatud, näitab integratsiooniraport vahele jäetud teste; päris mudeliga jooksuks tuleb seadistada GitHubi `OPENAI_API_KEY` secret ja `OPENAI_MODEL` secret või repository variable.
