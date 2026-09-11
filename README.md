# SMIT IT-teenuste infoagent

See projekt on Spring Booti ja Spring AI põhine piiratud sise-IT infoagent. Agent vastab eesti keeles ainult repos olevast teadmusbaasist, kasutab OpenAI mudelit allikalõikude valimiseks ning tagastab iga toetatud vastusega kontrollitud allikad ja inimloetavad viited. Avaliku faktilise vastuse koostab Java kood kanoonilistest teadmusbaasi lõikudest; mudeli loodud faktilist proosat API-sse ei edastata.

Projekt kasutab Java 21, Spring Boot 4.1.1, Spring AI 2.0.1, OpenAI mudelit ja Gradle 9.1.0 Wrapperit. Lühike tarnitav arhitektuuri- ja turvakokkuvõte on failis [docs/submission-summary.md](docs/submission-summary.md).

## Arhitektuur ja valikud

Üks sünkroonne Spring Booti rakendus teenindab REST API-t. Viis sünteetilist Markdown-faili laaditakse käivitamisel fikseeritud classpath-manifestist muutumatusse mällu. Spring AI-le registreeritakse täpselt kaks read-only tööriista: `listTopics` ja `searchKnowledgeBase`. Üldist failisüsteemi-, võrgu-, andmebaasi- ega käsutööriista ei ole.

OpenAI mudel tagastab suletud sisemise otsuse (`ANSWER`, `LIST_TOPICS`, `CLARIFY` või `REFUSE`) ja valitud lõikude ID-d. Rakendus lubab ainult sama päringu tööriistakutsetega saadud kanoonilisi ID-sid, kontrollib nende seost küsimusega ning koostab `answer`-i, `sources`-i, viited ja usaldustaseme ise. Staatiline märksõnaotsing sobib viie väikese dokumendi jaoks ja hoiab lahenduse auditeeritavana; vektorandmebaas ja embeddings ei ole selle ülesande jaoks vajalikud.

Otsinguküsimus ja järelküsimuse jaoks moodustatud kontekstipäring on piiratud sama 2 000 tähemärgiga nagu API küsimus.

Valikuline `sessionId` hoiab kuni neli viimast valideeritud küsimuse-vastuse paari protsessi mälus. Ajalugu aitab mõista järelküsimust, kuid allikad otsitakse iga päringu ajal uuesti. Sessioon aegub 30 minuti tegevusetuse järel ja puudub pärast rakenduse restarti.

## Eeldused ja konfiguratsioon

Kohalikuks käivitamiseks on vaja Java 21. Gradle paigaldust ei ole vaja, sest repos on Wrapper. Agent vajab OpenAI kasutamiseks kahte keskkonnamuutujat:

| Muutuja | Nõutud | Vaikeväärtus | Otstarve |
|---|---:|---|---|
| `OPENAI_API_KEY` | agendipäringuks | puudub | OpenAI autentimine; võtit ei tohi reposse lisada |
| `OPENAI_MODEL` | agendipäringuks | puudub | organisatsiooni poolt lubatud OpenAI mudeli nimi |
| `OPENAI_TEMPERATURE` | ei | `0.2` | mudeli temperatuur |
| `OPENAI_TIMEOUT` | ei | `30s` | OpenAI HTTP-päringu maksimaalne kestus |

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

Järelküsimuste jaoks genereeri kliendis ennustamatu sessiooni ID ja kasuta sama väärtust järgnevates küsimustes. Ära kasuta kasutajanime, e-posti aadressi ega muud äraarvatavat tunnust sessiooni ID-na.

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

Tööriistade allowlist on koodis fikseeritud. Mõlemad tööriistad loevad ainult käivitamisel laaditud staatilist teadmusbaasi, ei kirjuta andmeid, ei käivita käske ega tee väliseid päringuid. Mudeli valitud allikas peab esinema praeguse päringu tõendite hulgas, võrduma repos laaditud kanoonilise lõiguga ja toetama küsimuses küsitud sisulisi detaile. Pelk teemakattuvus ei ole piisav: kui näiteks GitLabi kohta küsitud tasu või lisatingimust lõigus ei ole, keeldub rakendus vastamast. `refused:false` vastus lubatakse ainult siis, kui `sources` ei ole tühi ja `answer` sisaldab iga allikafaili inimloetavat viidet.

Küsimuse detailitaseme maandamine tähendab, et seotud teema leidmine üksi ei anna vastamiseks piisavat alust. Näiteks GitLabi dokument ei toeta vastust tasu või polügraafinõude kohta, kui sellist detaili lõigus ei ole.

## Andmete töötlemine

Aktsepteeritud küsimus saadetakse OpenAI-le koos süsteemiprompti ja kahe lubatud tööriista skeemidega. Tööriista kasutamisel saadetakse mudelile ka sünteetilise teadmusbaasi vastavad lõigud. Kui klient kasutab `sessionId`-d, võidakse kuni neli varasemat aktsepteeritud küsimust ja valideeritud vastust saata järelküsimuse kontekstina uuesti OpenAI-le. OpenAI API võtit kasutatakse teenusega autentimiseks; seda ei lisata prompti, vastusesse ega rakenduse logidesse.

Rakendus ei salvesta vestlusi kettale ega andmebaasi. Sessioonikontekst püsib ainult ühe rakendusprotsessi mälus. Sama `sessionId` teadja saab sama konteksti kasutada, sest ID ei ole autentimisvahend; päris kasutuses tuleb valida ettearvamatu ID. Teadmusbaas sisaldab ainult ülesande jaoks loodud sünteetilist infot. Kasutaja ei tohiks agenti saata paroole, API võtmeid, isikukoode ega muid tundlikke andmeid.

## Testimine

Unit-testid ei vaja OpenAI võtit ega tee päris võrgukutseid:

```sh
env -u OPENAI_API_KEY -u OPENAI_MODEL ./gradlew test
```

Need katavad sisendi valideerimise ja turvafiltri, API-01, API-02, API-03 ja SEC-07, teadmusbaasi otsingu, küsimuse detailitaseme maandamise, tööriistade allowlist'i ja path traversal'i tõkestamise, süsteemi- ja kasutajarollide eralduse, sessioonikonteksti ning allikate ja viidete rakendustaseme kontrolli.

Päris integratsioonitestid käivad eraldi lähtekogumi ja taskiga ning vajavad `OPENAI_API_KEY` ja `OPENAI_MODEL` väärtusi:

```sh
set -a
. ./.env
set +a
./gradlew integrationTest
```

Testid käivitavad rakenduse juhuslikul lokaalsel pordil ja läbivad REST → agent → Spring AI → OpenAI voo. Kaetud on API-04, UC-01–UC-13, GROUND-01–GROUND-02 ning SEC-01–SEC-06 ja SEC-08. Väited kontrollivad stabiilseid käitumisinvariante, allikafaile ja keeldumisi, mitte mudeli sõnastust. SEC-07 on võtmeta API-test, sest liiga pikk sisend peab peatuma enne mudelikõnet. Kui võti või mudel puudub, märgitakse integratsiooniklass vahele jäetuks; seda ei loeta päris mudeliga edukaks jooksuks.

Gradle genereerib eraldi inimloetavad HTML raportid:

- unit-testid: `build/reports/tests/test/index.html`
- integratsioonitestid: `build/reports/tests/integrationTest/index.html`

`build/` on `.gitignore`-is ning genereeritud raporteid ei commitita.

Testitulemuste arv sõltub testide ja parameetrite hetkeversioonist; reprodutseeritava tulemuse saamiseks käivita ülaltoodud käsud ja ava vastav HTML-raport.

## GitHub Actions ja testiraportid

CI koosneb kuuest eraldi workflow'st. [Tests](https://github.com/siimkru/smit-26/actions/workflows/tests.yml) käivitub push'i, pull request'i ja käsitsi käivitamise korral ning teeb Gitleaksi, pull request'i korral kõrge raskusastme piiriga dependency review kontrolli ja `./gradlew check` käsu. Unit-testide HTML-raport avaldatakse artefaktina `unit-test-html-report`.

Teised workflow'd on [Live OpenAI integration tests](https://github.com/siimkru/smit-26/actions/workflows/live-integration.yml), [CodeQL](https://github.com/siimkru/smit-26/actions/workflows/codeql.yml), [Workflow analysis](https://github.com/siimkru/smit-26/actions/workflows/workflow-analysis.yml), [Gradle dependency submission](https://github.com/siimkru/smit-26/actions/workflows/dependency-submission.yml) ja [OpenSSF Scorecard](https://github.com/siimkru/smit-26/actions/workflows/scorecards.yml). Täpne jaotus, õigused ja piirangud on dokumenteeritud failis [docs/ci-static-analysis.md](docs/ci-static-analysis.md).

Unit-testid ja staatilised kontrollid ei saa OpenAI-võtit. Live-workflow käivitub ainult käsitsi, kasutab `openai-integration` Environment'i, käivitab `./gradlew integrationTest` ja avaldab raporti artefaktina `integration-test-html-report` ka testi ebaõnnestumise korral. CodeQL käivitub push'i, pull request'i, käsitsi ja kord nädalas; workflow-analysis käivitub push'i, pull request'i ja käsitsi; dependency submission push'i ja käsitsi; Scorecard push'i, käsitsi ja kord nädalas.

Esitatud testitulemuste workflow-jooksud ja nende raportid:

- [Tests run #21](https://github.com/siimkru/smit-26/actions/runs/34548855748) — unit-testid ja staatilised kontrollid; artefakt `unit-test-html-report`.
- [Live OpenAI integration tests #2](https://github.com/siimkru/smit-26/actions/runs/34548967516) — päris OpenAI integratsioonitestid; artefakt `integration-test-html-report`.

Hindamiseks vajalik päris integratsioonijooks tuleb teha võtmega lokaalselt või seadistada `openai-integration` Environment'i saladused ja käivitada live-workflow käsitsi. Workflow lehtedel on nähtavad jooksude täpsed tulemused ja allalaaditavad raportid. Repo link on [github.com/siimkru/smit-26](https://github.com/siimkru/smit-26).

## Teadaolevad piirangud

- Lahendus järgib ülesande teadlikult väikest skoopi: eesmärk ei ole täiuslik tootmissüsteem ega keerukas RAG- või käitusinfrastruktuur. Alltoodud piirangud on seetõttu dokumenteeritud, mitte varjatult tootmiskindlateks eeldatud.
- Märksõnaotsing ja mustripõhine ründetuvastus on teadlikult lihtsad. Filter ei pruugi tuvastada kõiki parafraase, Unicode'i homoglüüfe, null-laiusega märke, kodeeritud ründeid või tundlike andmete vorme; mõju piirab mudelist sõltumatu kanoonilise väljundi kontroll.
- OpenAI otsus võib mudeli ja aja lõikes erineda; rakendus piirab mõju kanoonilise, rakenduse koostatud väljundiga.
- Sessioonid on kliendi valitud ID-ga, autentimata ja omanikuga sidumata. Sama ID teadja saab sessiooni konteksti jätkata. Need aeguvad 30 minuti tegevusetuse järel, on protsessipõhised, piiratud nelja vahetusega, kaovad restardil ja neid ei jagata instantside vahel.
- Praeguse päringu tõendeid hoiab `ThreadLocal`, mis eeldab dokumenteeritud sünkroonset mudeli- ja tööriistavoogu samal lõimel. Asünkroonse tool calling'u lisamisel tuleb see asendada selgelt edasiantava request-scoped kontekstiga ja lisada concurrency-testid.
- Rate limiting, mudelikõnede concurrency-limiit, HTTP serveri body-size'i lisapiir ning kulu- ja latentsusmõõdikud puuduvad. OpenAI HTTP-päringul on seadistatav 30-sekundiline vaike-timeout. Ülesanne märgib rate limiting'u soovituslikuks ega nõua tootmiskõlblikku käitusinfrastruktuuri; küsimuse 2000 märgi piir jääb rakendustaseme kaitseks.
- CI hoiab unit- ja live-integratsioonitestid eraldi; live-workflow nõuab kaitstud Environment'i saladusi ja käsitsi käivitamist.
- CI käivitab Gitleaksi kogu repole ja Git-ajaloole ning pull request'ide puhul GitHubi dependency review skanneri, mis peatab vähemalt kõrge raskusastmega teadaolevate sõltuvushaavatavustega muudatused. Repos on lisaks Gradle'i kaudu seadistatud Checkstyle, PMD, SpotBugs/FindSecBugs ja JaCoCo.
- Iga Markdown-fail laaditakse ühe kanoonilise lõiguna. See on viie lühikese faili jaoks piisav, kuid pikema teadmusbaasi korral muutuksid väljavõtted liiga laiaks ning failid tuleks jagada stabiilsete ID-dega väiksemateks lõikudeks.
- Rakendus sõltub agendipäringute ajal OpenAI saadavusest ning integratsioonitestid tarbivad päris API krediiti.
