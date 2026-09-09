package ee.smit.agent.knowledge;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable repository over a fixed classpath manifest.  It intentionally has
 * no operation that accepts a resource name or filesystem path.
 */
@Repository
public class KnowledgeBaseRepository {

    private static final Map<String, List<String>> MANIFEST = Map.of(
            "gitlab-access.md", List.of("gitlab", "git", "ligipaas", "access"),
            "kubernetes-deploy.md", List.of("kubernetes", "k8s", "deploy", "juurutamine"),
            "cicd.md", List.of("ci/cd", "cicd", "pipeline", "ehitamine"),
            "code-review.md", List.of("code review", "koodireview", "merge", "pull request"),
            "access-management.md", List.of("ligipaasu haldus", "oigused", "kasutajakonto")
    );

    private static final List<String> FILE_ORDER = List.of(
            "gitlab-access.md", "kubernetes-deploy.md", "cicd.md", "code-review.md", "access-management.md"
    );
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+", Pattern.CASE_INSENSITIVE);

    private final List<Document> documents;
    private final List<KnowledgePassage> topics;

    public KnowledgeBaseRepository() {
        this.documents = FILE_ORDER.stream().map(this::loadDocument).toList();
        this.topics = documents.stream().map(Document::passage).toList();
    }

    public List<KnowledgePassage> listTopics() {
        return topics;
    }

    /**
     * Performs deterministic token matching over the fixed, already-loaded
     * documents. The query is data only; it is never used as a resource path.
     */
    public List<KnowledgePassage> search(String query) {
        if (query == null || query.isBlank() || looksLikePath(query)) {
            return List.of();
        }

        Set<String> queryTokens = tokens(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        return documents.stream()
                .map(document -> new ScoredPassage(document.passage(), score(document, queryTokens)))
                .filter(result -> result.score() > 0)
                .sorted(Comparator.comparingInt(ScoredPassage::score).reversed()
                        .thenComparing(result -> result.passage().file()))
                .limit(5)
                .map(ScoredPassage::passage)
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
                tokens(title + " " + excerpt + " " + String.join(" ", MANIFEST.get(file))));
    }

    private int score(Document document, Set<String> queryTokens) {
        int score = 0;
        for (String token : queryTokens) {
            if (document.tokens().stream().anyMatch(documentToken -> matches(documentToken, token))) {
                score++;
            }
        }
        return score;
    }

    private boolean matches(String documentToken, String queryToken) {
        return documentToken.equals(queryToken) || stem(documentToken).equals(stem(queryToken));
    }

    /* Small, predictable normalization for common Estonian case endings; this
       is intentionally not a general language-processing dependency. */
    private String stem(String token) {
        return token.replaceFirst("(idele|idega|isse|ist|ile|iga|i)$", "");
    }

    private boolean looksLikePath(String value) {
        return value.contains("../") || value.contains("..\\") || value.contains("/") || value.contains("\\") || value.indexOf('\0') >= 0;
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

    private record Document(KnowledgePassage passage, Set<String> tokens) {
    }

    private record ScoredPassage(KnowledgePassage passage, int score) {
    }
}
