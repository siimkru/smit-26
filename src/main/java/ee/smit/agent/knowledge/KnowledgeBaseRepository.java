package ee.smit.agent.knowledge;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable repository over a fixed classpath manifest.  It intentionally has
 * no operation that accepts a resource name or filesystem path.
 */
@Repository
public class KnowledgeBaseRepository {

    /** Keep retrieval aligned with the maximum question accepted by the API. */
    public static final int MAX_SEARCH_QUERY_LENGTH = 2_000;

    private static final Map<String, List<String>> MANIFEST = Map.of(
            "gitlab-access.md", List.of("gitlab", "git", "ligipaas", "access"),
            "kubernetes-deploy.md", List.of("kubernetes", "k8s", "deploy", "juurutamine"),
            "cicd.md", List.of("ci/cd", "cicd", "pipeline", "ehitamine", "deploy", "juurutamine"),
            "code-review.md", List.of("code review", "koodireview", "merge", "pull request"),
            "access-management.md", List.of("ligipaasu haldus", "oigused", "kasutajakonto")
    );
    private static final Map<String, List<String>> TOPIC_ANCHORS = Map.of(
            "gitlab-access.md", List.of("gitlab"),
            "kubernetes-deploy.md", List.of("kubernetes", "k8s", "helm", "deploy", "juurutamine"),
            "cicd.md", List.of("ci/cd", "cicd", "pipeline", "deploy", "juurutamine"),
            "code-review.md", List.of(
                    "code review", "koodireview", "merge", "pull request", "ulevaataja", "kontrollida", "uhendamine"),
            "access-management.md", List.of("oigused", "kasutajakonto", "roll")
    );
    /* Question wording is deliberately curated for this five-topic KB. Unknown
       substantive terms must not turn a merely related topic into evidence. */
    private static final Map<String, List<String>> SUPPORTED_ALIASES = Map.of(
            "gitlab-access.md", List.of(
                    "taotleda", "taotlus", "juurdepaas", "juurdepaasu", "saamine", "juhis", "juhised",
                    "kinnitaja", "kinnitab", "kinnitus", "kestus", "kaua", "aeg", "paev", "paeva"),
            "kubernetes-deploy.md", List.of(
                    "kasutusele votmine", "votta", "paigaldamine", "protsess", "juhis", "juhised"),
            "cicd.md", List.of(
                    "kasutusele votmine", "paigaldamine", "protsess", "tootab", "juhis", "juhised"),
            "code-review.md", List.of(
                    "koodi ulevaatus", "ule vaatama", "vaadata", "kontrollima", "kontrollida",
                    "uhendamine", "uhendamist", "juhis", "juhised"),
            "access-management.md", List.of(
                    "juurdepaas", "ligipaas", "taotleda", "taotlus", "saamine", "juhis", "juhised")
    );
    private static final Set<String> QUESTION_FRAMING = Set.of(
            "aga", "aega", "andke", "anna", "do", "enne", "get", "how", "i", "info", "jarel",
            "iga", "kaib", "kas", "kaudu", "kes", "kui", "kuidas", "kust", "ma", "mis", "mulle", "mul", "on",
            "palun", "parast", "peab", "probleem", "protsess", "saan", "saab", "saada", "see", "seda",
            "selle", "sellest", "teha", "toimub", "ule", "vajaksin", "vaja", "voi", "votab", "lisainfo"
    );

    private static final List<String> FILE_ORDER = List.of(
            "gitlab-access.md", "kubernetes-deploy.md", "cicd.md", "code-review.md", "access-management.md"
    );
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
            "(?:^|\\s|['\"])(?:[a-z]:[\\\\/]|/(?:[a-z0-9._-]+(?:/|$))+|\\\\\\\\)",
            Pattern.CASE_INSENSITIVE);

    private final List<Document> documents;
    private final List<KnowledgePassage> topics;
    private final Map<String, KnowledgePassage> passagesById;

    public KnowledgeBaseRepository() {
        this.documents = FILE_ORDER.stream().map(this::loadDocument).toList();
        this.topics = documents.stream().map(Document::passage).toList();
        this.passagesById = documents.stream().map(Document::passage)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        KnowledgePassage::id, passage -> passage));
    }

    public List<KnowledgePassage> listTopics() {
        return topics;
    }

    public Optional<KnowledgePassage> findById(String id) {
        return Optional.ofNullable(passagesById.get(id));
    }

    /**
     * Performs deterministic token matching over the fixed, already-loaded
     * documents. The query is data only; it is never used as a resource path.
     */
    public List<KnowledgePassage> search(String query) {
        if (query == null || query.isBlank() || query.length() > MAX_SEARCH_QUERY_LENGTH || looksLikePath(query)) {
            return List.of();
        }

        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        List<ScoredPassage> related = documents.stream()
                .map(document -> new ScoredPassage(document, score(document, queryTokens)))
                .filter(result -> result.score() > 0)
                .toList();
        if (!fullySupports(related.stream().map(ScoredPassage::document).toList(), queryTokens)) {
            return List.of();
        }
        return related.stream()
                .sorted(Comparator.comparingInt(ScoredPassage::score).reversed()
                        .thenComparing(result -> result.document().passage().file()))
                .limit(5)
                .map(result -> result.document().passage())
                .toList();
    }

    private Document loadDocument(String file) {
        ClassPathResource resource = new ClassPathResource("knowledge-base/" + file);
        String markdown;
        try (InputStream input = resource.getInputStream()) {
            markdown = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Puuduv või loetamatu teadmusbaasi dokument: " + file, exception);
        }

        List<String> lines = markdown.lines().toList();
        String title = lines.stream()
                .filter(line -> line.startsWith("# "))
                .findFirst()
                .map(line -> line.substring(2).trim())
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Teadmusbaasi dokumendil puudub pealkiri: " + file));
        String excerpt = lines.stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .reduce((first, second) -> first + " " + second)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("Teadmusbaasi dokument on tühi: " + file));

        return new Document(new KnowledgePassage(file + "#1", file, title, excerpt),
                tokens(title + " " + excerpt + " " + String.join(" ", MANIFEST.get(file))),
                tokens(String.join(" ", TOPIC_ANCHORS.get(file))));
    }

    private int score(Document document, Set<String> queryTokens) {
        boolean hasTopicAnchor = queryTokens.stream()
                .anyMatch(queryToken -> document.anchors().stream()
                        .anyMatch(anchor -> matches(anchor, queryToken)));
        if (!hasTopicAnchor) {
            return 0;
        }
        int score = 0;
        for (String token : queryTokens) {
            if (document.tokens().stream().anyMatch(documentToken -> matches(documentToken, token))) {
                score++;
            }
        }
        return score;
    }

    private boolean fullySupports(List<Document> relatedDocuments, Set<String> queryTokens) {
        if (relatedDocuments.isEmpty()) {
            return false;
        }
        Set<String> supported = new java.util.LinkedHashSet<>();
        for (Document document : relatedDocuments) {
            supported.addAll(document.tokens());
            supported.addAll(tokens(String.join(" ", SUPPORTED_ALIASES.get(document.passage().file()))));
        }
        return queryTokens.stream()
                .filter(token -> !QUESTION_FRAMING.contains(token))
                .allMatch(queryToken -> supported.stream().anyMatch(term -> matches(term, queryToken)));
    }

    private boolean matches(String documentToken, String queryToken) {
        return documentToken.equals(queryToken) || stem(documentToken).equals(stem(queryToken));
    }

    /* Small, predictable normalization for common Estonian case endings; this
       is intentionally not a general language-processing dependency. */
    private String stem(String token) {
        return token.replaceFirst("(miseks|mise|idele|idega|esse|asse|usse|isse|uks|ust|ast|est|ist|ile|iga|id|it|at|st|lt|le|ga|i)$", "");
    }

    private boolean looksLikePath(String value) {
        return value.contains("../")
                || value.contains("..\\")
                || value.contains("/etc/passwd")
                || value.contains("\\etc\\passwd")
                || value.indexOf('\0') >= 0
                || ABSOLUTE_PATH.matcher(value).find();
    }

    private Set<String> tokens(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        String[] parts = NON_ALPHANUMERIC.split(normalized);
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String part : parts) {
            if (part.length() >= 2) {
                result.add(part);
            }
        }
        return result;
    }

    private record Document(KnowledgePassage passage, Set<String> tokens, Set<String> anchors) {
    }

    private record ScoredPassage(Document document, int score) {
    }
}
