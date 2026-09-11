package ee.smit.agent.agent;

import ee.smit.agent.knowledge.KnowledgeBaseRepository;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared deterministic interpretation used by retrieval and response validation. */
public final class AgentQuestionSupport {

    private static final Pattern TOPIC_LIST_INTENT = Pattern.compile(
            "\\b(?:mis|millised|millistel|millistest|milliste)\\s+teem(?:adel|adest|adega|ad|a)\\b");
    private static final Set<String> TOPIC_LIST_ALLOWED_TERMS = Set.of(
            "mis", "millised", "millistel", "millistest", "milliste", "teemadel", "teemadest", "teemadega",
            "teemad", "teema", "teemade", "saad", "mulle", "infot", "anda", "oskad", "aidata",
            "gitlab", "kubernetes", "k8s", "cicd", "pipeline", "code", "review", "koodireview",
            "ligipaas", "haldus", "access");

    private AgentQuestionSupport() {
    }

    public static boolean isTopicListQuestion(String question) {
        return TOPIC_LIST_INTENT.matcher(normalize(question)).find();
    }

    public static boolean isSupportedTopicListQuestion(String question) {
        if (!isTopicListQuestion(question)) {
            return false;
        }
        return Arrays.stream(normalize(question).split("[^a-z0-9]+"))
                .filter(token -> !token.isBlank())
                .allMatch(TOPIC_LIST_ALLOWED_TERMS::contains);
    }

    public static boolean isFollowUp(String question) {
        String normalized = normalize(question);
        return normalized.matches(".*\\b(see|seda|selle|sellest|aga)\\b.*")
                || normalized.contains("kui kaua")
                || normalized.startsWith("kust")
                || normalized.contains("mis edasi");
    }

    public static String contextualQuery(String question, List<AgentExchange> history) {
        StringBuilder query = new StringBuilder(question);
        for (AgentExchange exchange : history) {
            int remaining = KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH - query.length() - 1;
            if (remaining <= 0) {
                break;
            }
            appendBounded(query, exchange.question(), remaining);
            remaining = KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH - query.length() - 1;
            if (remaining > 0) {
                appendSourceAnchors(query, exchange.answer(), remaining);
            }
        }
        return query.toString();
    }

    private static void appendSourceAnchors(StringBuilder query, String answer, int remaining) {
        String marker = "[allikas: ";
        int from = 0;
        while (remaining > 0) {
            int markerStart = answer.indexOf(marker, from);
            if (markerStart < 0) {
                return;
            }
            int fileStart = markerStart + marker.length();
            int fileEnd = answer.indexOf(']', fileStart);
            if (fileEnd < 0) {
                return;
            }
            String file = answer.substring(fileStart, fileEnd).replaceFirst("\\.md$", "");
            appendBounded(query, file, remaining);
            remaining = KnowledgeBaseRepository.MAX_SEARCH_QUERY_LENGTH - query.length() - 1;
            from = fileEnd + 1;
        }
    }

    private static void appendBounded(StringBuilder query, String value, int remaining) {
        query.append(' ').append(value, 0, Math.min(remaining, value.length()));
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
