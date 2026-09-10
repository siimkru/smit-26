# SMIT IT-teenuste infoagent

See projekt on Spring Booti ja Spring AI põhine piiratud sise-IT infoagent. Agent vastab eesti keeles ainult repos olevast teadmusbaasist, kasutab OpenAI mudelit allikalõikude valimiseks ning tagastab iga toetatud vastusega kontrollitud allikad ja inimloetavad viited. Avaliku faktilise vastuse koostab Java kood kanoonilistest teadmusbaasi lõikudest; mudeli loodud faktilist proosat API-sse ei edastata.

Projekt kasutab Java 21, Spring Boot 3.4.5, Spring AI 1.0.0, OpenAI mudelit ja Gradle 8.14.3 Wrapperit. Lühike tarnitav arhitektuuri- ja turvakokkuvõte on failis [docs/submission-summary.md](docs/submission-summary.md).

## Arhitektuur ja valikud

Üks sünkroonne Spring Booti rakendus teenindab REST API-t. Viis sünteetilist Markdown-faili laaditakse käivitamisel fikseeritud classpath-manifestist muutumatusse mällu. Spring AI-le registreeritakse täpselt kaks read-only tööriista: `listTopics` ja `searchKnowledgeBase`. Üldist failisüsteemi-, võrgu-, andmebaasi- ega käsutööriista ei ole.

OpenAI mudel tagastab suletud sisemise otsuse (`ANSWER`, `LIST_TOPICS`, `CLARIFY` või `REFUSE`) ja valitud lõikude ID-d. Rakendus lubab ainult sama päringu tööriistakutsetega saadud kanoonilisi ID-sid, kontrollib nende seost küsimusega ning koostab `answer`-i, `sources`-i, viited ja usaldustaseme ise. Staatiline märksõnaotsing sobib viie väikese dokumendi jaoks ja hoiab lahenduse auditeeritavana; vektorandmebaas ja embeddings ei ole selle ülesande jaoks vajalikud.

Valikuline `sessionId` hoiab kuni neli viimast valideeritud küsimuse-vastuse paari protsessi mälus. Ajalugu aitab mõista järelküsimust, kuid allikad otsitakse iga päringu ajal uuesti. Sessioon puudub pärast rakenduse restarti.

## Eeldused ja konfiguratsioon

Kohalikuks käivitamiseks on vaja Java 21. Gradle paigaldust ei ole vaja, sest repos on Wrapper. Agent vajab OpenAI kasutamiseks kahte keskkonnamuutujat:

| Muutuja | Nõutud | Vaikeväärtus | Otstarve |
|---|---:|---|---|
| `OPENAI_API_KEY` | agendipäringuks | puudub | OpenAI autentimine; võtit ei tohi reposse lisada |
| `OPENAI_MODEL` | agendipäringuks | puudub | organisatsiooni poolt lubatud OpenAI mudeli nimi |
| `OPENAI_TEMPERATURE` | ei | `0.2` | mudeli temperatuur |

Fail `.env.example` sisaldab ainult ohutuid näidisväärtusi. Kopeeri see lokaalselt `.env`-iks, täida väärtused ja laadi need shelli; Spring Boot ise `.env` faili ei loe.

```sh
cp .env.example .env
# täida .env lokaalselt; ära commiti seda
set -a
. ./.env
set +a
./gradlew bootRun
```

Ilma võtme või mudelita rakendus käivitub ja tervisekontroll töötab, kuid agendipäring tagastab sanitiseeritud HTTP 503 vastuse.

## API kasutamine

Tervisekontroll ei kutsu OpenAI-d:

```sh
curl http://localhost:8080/api/v1/health
```

```json
{"status":"UP"}
```

Agendilt küsimiseks kasuta `POST /api/v1/agent/ask`. `question` on kohustuslik ja kuni 2000 tähemärki. `sessionId` on valikuline ning võib sisaldada 1–128 ASCII tähte, numbrit, alakriipsu või sidekriipsu.

```sh
curl -X POST http://localhost:8080/api/v1/agent/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"Kuidas taotleda ligipääsu GitLabile?","sessionId":"naide-7f3c"}'
```

Toetatud vastuse näide:

```json
{
  "answer": "GitLabi ligipääsu taotlemiseks logi sisse SMIT teenuste portaali ja vali „Ligipääsutaotlus” → „GitLab”. Täida töine põhjendus; taotlus saadetakse juhile kinnitamiseks. Pärast juhi kinnitust luuakse ligipääs tavaliselt 1–2 tööpäeva jooksul. [allikas: gitlab-access.md]",
  "sources": [
    {
      "file": "gitlab-access.md",
      "title": "GitLabi ligipääs",
      "excerpt": "GitLabi ligipääsu taotlemiseks logi sisse SMIT teenuste portaali ja vali „Ligipääsutaotlus” → „GitLab”. Täida töine põhjendus; taotlus saadetakse juhile kinnitamiseks. Pärast juhi kinnitust luuakse ligipääs tavaliselt 1–2 tööpäeva jooksul."
    }
  ],
  "confidence": "high",
  "refused": false,
  "refusalReason": null
}
```

Toetuseta või ohtlik küsimus saab HTTP 200 vastuse, millel on `refused:true`, tühi `sources` ja eestikeelne `refusalReason`. Näiteks:

```json
{
  "answer": "Ma ei saa sellele küsimusele vastata. Teadmusbaasis ei ole küsimusele piisavat infot.",
  "sources": [],
  "confidence": null,
  "refused": true,
  "refusalReason": "Teadmusbaasis ei ole küsimusele piisavat infot."
}
```

Tühi, puuduv või üle 2000 märgi pikkune küsimus ja vigane JSON saavad HTTP 400. Puuduv OpenAI konfiguratsioon või teenuse tõrge saab HTTP 503. Veavastused ei kajasta kasutaja täissisendit ega teenuse sisemisi veateateid.

## Teadmusbaas ja süsteemiprompt

Teadmusbaasi failid asuvad `src/main/resources/knowledge-base/` kataloogis ja käsitlevad GitLabi ligipääsu, Kubernetesi juurutamist, CI/CD pipeline'i, koodireview'd ning ligipääsude haldust. Iga teema on eraldi Markdown-failis. Rakendus ei ava kasutaja antud failiteid.

Eestikeelne süsteemiprompt asub `src/main/resources/prompts/agent-system.txt`. Spring AI sõnumiloendis on see `system` rollis; kasutaja küsimus ja sessiooniajalugu jäävad eraldi `user` ja `assistant` rollidesse. Prompt määrab keele, skoopi, tööriistad, keeldumise, allikate ja sisemise JSON-otsuse reeglid. Rakenduse valideerimine jõustab samad põhiinvariandid mudelist sõltumatult.

## Turvalisus

Enne OpenAI kutset kontrollitakse sisendi pikkust ning blokeeritakse teadaolevad prompt injection'i, rolli ümberkirjutamise, sisemiste juhiste või tööriistade avaldamise, path traversal'i, destruktiivsete juhiste ja ilmsete saladuste mustrid. Segatud õiguspärane ja ründav küsimus lükatakse tervikuna tagasi. Turvalogisse jõuavad ainult kategooria ja sisendi pikkus, mitte küsimus ega tuvastatud saladuse väärtus.

Tööriistade allowlist on koodis fikseeritud. Mõlemad tööriistad loevad ainult käivitamisel laaditud staatilist teadmusbaasi, ei kirjuta andmeid, ei käivita käske ega tee väliseid päringuid. Mudeli valitud allikas peab esinema praeguse päringu tõendite hulgas ja võrduma repos laaditud kanoonilise lõiguga. `refused:false` vastus lubatakse ainult siis, kui `sources` ei ole tühi ja `answer` sisaldab iga allikafaili inimloetavat viidet.

## Andmete töötlemine

Aktsepteeritud küsimus saadetakse OpenAI-le koos süsteemiprompti ja kahe lubatud tööriista skeemidega. Tööriista kasutamisel saadetakse mudelile ka sünteetilise teadmusbaasi vastavad lõigud. Kui klient kasutab `sessionId`-d, võidakse kuni neli varasemat aktsepteeritud küsimust ja valideeritud vastust saata järelküsimuse kontekstina uuesti OpenAI-le. OpenAI API võtit kasutatakse teenusega autentimiseks; seda ei lisata prompti, vastusesse ega rakenduse logidesse.

Rakendus ei salvesta vestlusi kettale ega andmebaasi. Sessioonikontekst püsib ainult ühe rakendusprotsessi mälus. Sama `sessionId` teadja saab sama konteksti kasutada, sest ID ei ole autentimisvahend; päris kasutuses tuleb valida ettearvamatu ID. Teadmusbaas sisaldab ainult ülesande jaoks loodud sünteetilist infot. Kasutaja ei tohiks agenti saata paroole, API võtmeid, isikukoode ega muid tundlikke andmeid.

## Testimine

Unit-testid ei vaja OpenAI võtit ega tee päris võrgukutseid:

```sh
env -u OPENAI_API_KEY -u OPENAI_MODEL ./gradlew test
```

Need katavad sisendi valideerimise ja turvafiltri, API-01, API-02, API-03 ja SEC-07, teadmusbaasi otsingu, tööriistade allowlist'i ja path traversal'i tõkestamise, süsteemi- ja kasutajarollide eralduse, sessioonikonteksti ning allikate ja viidete rakendustaseme kontrolli.

Päris integratsioonitestid käivad eraldi lähtekogumi ja taskiga ning vajavad `OPENAI_API_KEY` ja `OPENAI_MODEL` väärtusi:

```sh
set -a
. ./.env
set +a
./gradlew integrationTest
```

Testid käivitavad rakenduse juhuslikul lokaalsel pordil ja läbivad REST → agent → Spring AI → OpenAI voo. Kaetud on API-04, UC-01–UC-13 ning SEC-01–SEC-06 ja SEC-08. Väited kontrollivad stabiilseid käitumisinvariante, allikafaile ja keeldumisi, mitte mudeli sõnastust. SEC-07 on võtmeta API-test, sest liiga pikk sisend peab peatuma enne mudelikõnet. Kui võti või mudel puudub, märgitakse integratsiooniklass vahele jäetuks; seda ei loeta päris mudeliga edukaks jooksuks.

Gradle genereerib eraldi inimloetavad HTML raportid:

- unit-testid: `build/reports/tests/test/index.html`
- integratsioonitestid: `build/reports/tests/integrationTest/index.html`

`build/` on `.gitignore`-is ning genereeritud raporteid ei commitita.

Viimane lokaalne kontroll 10.09.2026: 61 unit-testi läbis võtmeta ning 21 integratsioonitesti läbis päris OpenAI võtmega; vahele jäetud, ebaõnnestunud ja veaga teste oli 0.

## GitHub Actions ja testiraportid

Workflow [Tests](https://github.com/siimkru/smit-26/actions/workflows/tests.yml) käivitab push'i, pull request'i ja käsitsi käivitamise korral alati unit-testid. Seejärel käivitab ta integratsioonitask'i; päris OpenAI testid aktiveeruvad ainult siis, kui GitHubis on `OPENAI_API_KEY` secret ning `OPENAI_MODEL` secret või repository variable. Mõlemad raportid laaditakse üles ka testitask'i ebaõnnestumise korral eraldi artefaktidena:

- `unit-test-html-report`
- `integration-test-html-report`

Kui CI saladusi ei ole, näitab integratsiooniraport vahele jäetud teste. Hindamiseks vajalik päris integratsioonijooks tuleb sel juhul teha võtmega lokaalselt või seadistada repository saladused ja käivitada workflow käsitsi. Repo link on [github.com/siimkru/smit-26](https://github.com/siimkru/smit-26).

Avaldatud workflow jooks koos mõlema HTML-artefaktiga: [GitHub Actions run 34404631418](https://github.com/siimkru/smit-26/actions/runs/34404631418). Selle jooksu unit-testid läbisid, kuid integratsiooniraport märgib kõik 21 testi vahele jäetuks, sest CI-s ei olnud OpenAI saladusi; päris mudeli tulemus on ülal dokumenteeritud lokaalne jooks.

## Teadaolevad piirangud

- Märksõnaotsing ja mustripõhine ründetuvastus on teadlikult lihtsad ning ei tunne kõiki parafraase või tundlike andmete vorme.
- OpenAI otsus võib mudeli ja aja lõikes erineda; rakendus piirab mõju kanoonilise, rakenduse koostatud väljundiga.
- Sessioonid on autentimata, protsessipõhised, kuni nelja vahetusega ja kaovad restardil; mitme instantsi vahel konteksti ei jagata.
- Rate limiting puudub, sest ülesanne märgib selle soovituslikuks.
- Rakendus sõltub agendipäringute ajal OpenAI saadavusest ning integratsioonitestid tarbivad päris API krediiti.
