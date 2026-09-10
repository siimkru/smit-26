# Nõuete maatriks

`docs/assignment.md` on ainus normatiivne nõuete allikas. See maatriks seob ülesande kohustuslikud nõuded valmis lahenduse ja kontrolliga; see ei lisa uusi nõudeid.

| Ülesande osa | Rakenduse tõend | Kontroll |
|---|---|---|
| Java, Spring Boot, Spring AI, OpenAI, Gradle Wrapper, REST/JSON | `build.gradle`, Wrapper, `SmitAgentApplication`, `SpringAiOpenAiGateway`, API-kontrollerid | `./gradlew test`; võtmega `./gradlew integrationTest` |
| Võti ei ole repos; mudel ja parameeter on seadistatavad | `.gitignore`, `.env.example`, `application.yml`, `OpenAiAgentProperties` | võtmeta context-test; repo ja konfiguratsiooni audit |
| `POST /api/v1/agent/ask` leping | `AgentController`, `AskRequest`, `AskResponse`, `Source` | API-01, API-02 ja API-04 |
| `GET /api/v1/health` ilma OpenAI-ta | `HealthController` | API-03 |
| Tühi ja liiga pikk sisend peatub enne LLM-i | Bean Validation, `RequestSecurityService` | API-01, API-02 ja SEC-07; mock ei saa kutset |
| Vähemalt viis teemat eraldi failides | viis faili `src/main/resources/knowledge-base/` all | `KnowledgeBaseRepositoryTest`; UC-01, UC-03, UC-04 ja UC-05 |
| Spring AI tool calling | `SpringAiOpenAiGateway` annab `ToolCallbackProvider`-i `ChatClient`-ile | päris integratsioonitestid |
| Ainult lubatud read-only KB-tööriistad | `ToolAllowlist`, `KnowledgeBaseToolConfiguration`, `KnowledgeBaseTools` | `KnowledgeBaseToolsTest`; SEC-02 ja SEC-06 |
| Süsteemi- ja kasutajaroll on eraldi | `agent-system.txt`, `AgentPromptFactory` | `AgentPromptFactoryTest`; SEC-01, SEC-03 ja SEC-05 |
| Agent vastab eesti keeles ja ainult KB põhjal | eestikeelne prompt ja KB; `GroundedResponseAssembler` koostab avaliku teksti kanoonilistest lõikudest | positiivsed UC-testid, eriti UC-08; UC-10 ja UC-12 |
| Küsimuse detail peab olema tõendatud, mitte ainult teema seotud | `KnowledgeBaseRepository` täieliku toe kontroll, aliaste loend ja `GroundedResponseAssembler` | `KnowledgeBaseRepositoryTest`, `AgentOrchestratorTest`, `GroundedResponseAssemblerTest`; GROUND-01 ja GROUND-02 |
| `refused:false` nõuab allikaid ja inimloetavaid viiteid | `CurrentTurnEvidence`, `GroundedResponseAssembler` | `GroundedResponseAssemblerTest`; API-04 |
| Mitme allika ja allikaküsimuse tugi | valitud lõigud säilitatakse eraldi `Source` kirjetena | UC-05, UC-07 ja UC-13 |
| Prompt injection ja sisemiste juhiste avaldamise kaitse | fail-fast mustrid, fikseeritud süsteemiprompt, suletud mudeliotsus, rakenduse väljundivalideerimine | SEC-01–SEC-06 ja SEC-08 nii REST-i kui mudelini jõudvate variantidega |
| Tundlikku infot ei saadeta teadlikult OpenAI-le | parooli, võtme, tokeni, privaatvõtme ja isikukoodi kontrollid enne mudelit; sünteetiline KB | `RequestSecurityServiceTest`; UC-11 |
| Täisküsimust ei logita | turvalogi sisaldab ainult kategooriat ja pikkust | logi püüdmise unit-test |
| Valikuline korduvkasutatav sessioon | piiratud `SessionStore`; tõendid otsitakse järelküsimusel uuesti | `SessionStoreTest`, `AgentOrchestratorTest`, UC-06 ja UC-13 |
| Unit-testid on võtmeta ja mockivad välised sõltuvused | `src/test`, tühi mudelikonfiguratsioon ja gateway test-double'id | `./gradlew test` |
| Päris integratsioonitestid on eraldi | `src/integrationTest`, `integrationTest` Gradle task | API-04, UC-01–UC-13, SEC-01–SEC-06 ja SEC-08 |
| Eraldi HTML raportid | Gradle `test` ja `integrationTest` raportite konfiguratsioon | `build/reports/tests/test/` ja `build/reports/tests/integrationTest/` |
| GitHub Actions käivitab testid ja avaldab raportid | `.github/workflows/tests.yml` | `unit-test-html-report` ja `integration-test-html-report` artefaktid |
| Genereeritud raporteid ei commitita | `.gitignore` ignoreerib `build/` | `git check-ignore build/reports/tests/test/index.html` |
| README sisaldab käivitamist, API-t, konfiguratsiooni, turvet, andmetöötlust, testimist ja piiranguid | `README.md` | dokumentatsiooni audit |
| Maksimaalselt ühe lehe kokkuvõte | `docs/submission-summary.md` | dokumentatsiooni audit |
| Lokaalsed staatilised ja katvuskontrollid | Checkstyle, PMD, SpotBugs/FindSecBugs ja JaCoCo `build.gradle`-is | `./gradlew check`; praegune CI neid eraldi ei käivita |

## Kohustuslike stsenaariumide jälgitavus

| ID-d | Asukoht ja tulemus |
|---|---|
| API-01, API-02, API-03, SEC-07 | `AgentApiTest`; võtmeta unit-testid, HTTP staatus ja mudelikõne puudumine |
| API-04 | `AgentRestIntegrationTest`; avaliku JSON struktuur päris OpenAI voos |
| UC-01–UC-08 | sama integratsiooniklass; toetatud, allikaga ja eestikeelne käitumine ning sessiooni järelküsimus |
| UC-09–UC-13 | sama integratsiooniklass; keeldumised, väljamõeldud allika puudumine ja allika järelküsimus |
| GROUND-01, GROUND-02 | `AgentRestIntegrationTest`; teadaoleva teemaga seotud, kuid lõigus toetamata detail lükatakse tagasi |
| SEC-01–SEC-06, SEC-08 | sama integratsiooniklass; ülesande originaalsisend peatub fail-fast ning semantiline variant läbib päris mudelivoo sama ohutu tulemusega |

Rate limiting on ülesandes soovituslik ja seda ei ole lisatud. Genereeritud raportid jäävad lokaalsesse `build/` kataloogi või GitHub Actionsi artefaktidesse ega kuulu lähtekoodi.
