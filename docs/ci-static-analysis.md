# CI staatilised kontrollid

Projektis on Java lähtekoodi kontrollid endiselt Gradle'i `check` task'is:
Checkstyle kontrollib vormingut ja stiilireegleid, PMD leiab Java koodilõhnasid,
SpotBugs koos FindSecBugsiga leiab baitkoodi- ja turvavigu ning JaCoCo jõustab
70% ridade katvuse. Uued kontrollid vastavad teistsugustele riskidele ega
dubleeri neid analüsaatoreid.

## Kontrollid ja vastutus

| Kontroll | Workflow või käsk | Milleks kasutatakse | Mida see ei asenda |
|---|---|---|---|
| CodeQL | `codeql.yml` | Semantiline Java turvaanalüüs, sh andmevoo ja taustal olevate haavatavuste leidmine | Checkstyle'i, PMD-d ega SpotBugsi |
| actionlint | `workflow-analysis.yml` | GitHub Actions YAML-i, avaldiste, kontekstide ja action-input'ide valideerimine | Java analüüsi |
| ShellCheck | actionlinti embedded shell kontroll | Workflow `run` skriptide shell-spetsiifiliste vigade leidmine | Rakenduse Java turvakontrolle |
| zizmor | `workflow-analysis.yml` | GitHub Actionsi turvamustrite, liigsete õiguste, skripti-injektsiooni ja mutable action-ref'ide audit | Workflow YAML-i süntaksikontrolli |
| Semgrep CE | `workflow-analysis.yml` | Keeleteadlikud Java ja konfiguratsiooni turvareeglid; sobib hiljem projekti enda keelureeglitele | Olemasolevaid Java analüsaatoreid tervikuna |
| Gradle dependency submission | `dependency-submission.yml` | Kogu Gradle'i sõltuvusgraafi GitHubisse saatmine, et Dependabot saaks pidevat sõltuvusvaadet kasutada | Pull request'i dependency-review kontrolli |
| OpenSSF Scorecard | `scorecards.yml` | Repositooriumi ja CI/CD tarneahela turvahügieeni perioodiline hindamine | Rakenduse lähtekoodi analüüsi |

OWASP Dependency-Checki ei lisatud: praegune GitHub dependency review ja Gradle'i
sõltuvusgraafi esitamine katavad selle väikese projekti vajaduse väiksema
hoolduskoormusega. SonarQube'i ega Qodana't ei lisatud, sest need dubleeriksid
olemasolevaid Checkstyle-, PMD- ja SpotBugs-kontrolle.

## Käivitumine

- `tests.yml` käivitab iga muudatuse puhul unit-testid, Checkstyle'i, PMD, SpotBugsi,
  FindSecBugsi, JaCoCo ja Gitleaksi.
- `codeql.yml` käivitub push'i, pull request'i ja kord nädalas; tulemused lähevad
  GitHub Code Scanningusse.
- `workflow-analysis.yml` kontrollib workflow-faile ning Java/configuration
  turvamustreid iga push'i ja pull request'i puhul.
- `dependency-submission.yml` saadab push'i järel Gradle'i dependency-graafi
  GitHubisse.
- `scorecards.yml` käivitub push'i ja kord nädalas ning avaldab SARIF-raporti.
- Päris OpenAI integratsioonitestid on failis `live-integration.yml` ja ainult
  `workflow_dispatch` sündmusel. Workflow kasutab `openai-integration` GitHub
  Environment'i, et API-võti ei oleks tavapärase pull request'i koodi käes.

Kolmandate osapoolte Actionsid on võimalusel commit-SHA-ga pin'itud; kommentaaris
on säilitatud loetav release-versioon. Semgrepi container on fikseeritud
versiooniga. Uuendamisel tuleb kontrollida release'i ja SHA vastavust ning
käivitada `./gradlew check`.
