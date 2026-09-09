package ee.smit.agent.response;

import ee.smit.agent.agent.AgentDecision;
import ee.smit.agent.agent.AgentExchange;
import ee.smit.agent.api.AskResponse;
import ee.smit.agent.api.Source;
import ee.smit.agent.knowledge.KnowledgeBaseRepository;
import ee.smit.agent.knowledge.KnowledgePassage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;

/** Final trust boundary: only canonical current-turn KB text reaches factual API answers. */
@Component
public class GroundedResponseAssembler {

    private static final String GROUNDING_FAILURE =
            "Vastust ei saanud usaldusväärselt siduda teadmusbaasi allikaga.";
    private final Set<String> allTopicIds;
    private final Set<String> deployTopicIds;
    private final KnowledgeBaseRepository repository;

    public GroundedResponseAssembler(KnowledgeBaseRepository repository) {
        this.repository = repository;
        this.allTopicIds = repository.listTopics().stream()
                .map(KnowledgePassage::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.deployTopicIds = repository.listTopics().stream()
                .filter(passage -> passage.file().equals("kubernetes-deploy.md") || passage.file().equals("cicd.md"))
                .map(KnowledgePassage::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public AskResponse assemble(String question, List<AgentExchange> history, AgentDecision decision,
                                Map<String, KnowledgePassage> currentEvidence) {
        if (decision == null || decision.action() == null) {
            return refusal(GROUNDING_FAILURE);
        }

        final String normalizedAction = decision.action().trim().toUpperCase(Locale.ROOT);
        return switch (normalizedAction) {
            case "ANSWER" -> answer(decision.selectedPassageIds(), currentEvidence,
                    eligibleAnswerIds(question, history));
            case "LIST_TOPICS" -> isTopicListQuestion(question)
                    ? topicList(decision.selectedPassageIds(), currentEvidence)
                    : refusal(GROUNDING_FAILURE);
            case "CLARIFY" -> isDeployQuestion(question)
                    ? clarification(decision.selectedPassageIds(), currentEvidence)
                    : refusal(GROUNDING_FAILURE);
            case "REFUSE" -> refusal(mapRefusalReason(decision.refusalReason()));
            default -> refusal(GROUNDING_FAILURE);
        };
    }

    public AskResponse refusal(String reason) {
        String safeReason = reason == null || reason.isBlank()
                ? "Teadmusbaasis ei ole küsimusele piisavat infot."
                : reason;
        return new AskResponse("Ma ei saa sellele küsimusele vastata. " + safeReason,
                List.of(), null, true, safeReason);
    }

    private AskResponse answer(List<String> selectedIds, Map<String, KnowledgePassage> evidence,
                               Set<String> eligibleIds) {
        List<KnowledgePassage> passages = resolve(selectedIds, evidence);
        if (passages.isEmpty() || passages.stream().anyMatch(passage -> !eligibleIds.contains(passage.id()))) {
            return refusal(GROUNDING_FAILURE);
        }
        String answer = passages.stream()
                .map(passage -> passage.excerpt() + " [allikas: " + passage.file() + "]")
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return grounded(answer, passages, "high");
    }

    private AskResponse topicList(List<String> selectedIds, Map<String, KnowledgePassage> evidence) {
        List<KnowledgePassage> passages = resolve(selectedIds, evidence);
        Set<String> selected = passages.stream().map(KnowledgePassage::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!selected.equals(allTopicIds)) {
            return refusal(GROUNDING_FAILURE);
        }
        String answer = "Teadmusbaasis on järgmised teemad:\n" + passages.stream()
                .map(passage -> "- " + passage.title() + " [allikas: " + passage.file() + "]")
                .collect(java.util.stream.Collectors.joining("\n"));
        return grounded(answer, passages, "high");
    }

    private AskResponse clarification(List<String> selectedIds, Map<String, KnowledgePassage> evidence) {
        List<KnowledgePassage> passages = resolve(selectedIds, evidence);
        Set<String> selected = passages.stream().map(KnowledgePassage::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!selected.equals(deployTopicIds)) {
            return refusal(GROUNDING_FAILURE);
        }
        String citations = passages.stream().map(KnowledgePassage::file).distinct()
                .map(file -> "[allikas: " + file + "]")
                .collect(java.util.stream.Collectors.joining(" "));
        String answer = "Palun täpsusta, kas küsimus puudutab Kubernetesi juurutamist või CI/CD pipeline'i. " + citations;
        return grounded(answer, passages, "low");
    }

    private AskResponse grounded(String answer, List<KnowledgePassage> passages, String confidence) {
        List<Source> sources = passages.stream()
                .map(passage -> new Source(passage.file(), passage.title(), passage.excerpt()))
                .toList();
        if (sources.isEmpty() || sources.stream().anyMatch(source -> !answer.contains(source.file()))) {
            return refusal(GROUNDING_FAILURE);
        }
        return new AskResponse(answer, sources, confidence, false, null);
    }

    private List<KnowledgePassage> resolve(List<String> selectedIds, Map<String, KnowledgePassage> evidence) {
        if (selectedIds == null || selectedIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>(selectedIds);
        List<KnowledgePassage> passages = new ArrayList<>();
        for (String id : uniqueIds) {
            KnowledgePassage passage = evidence.get(id);
            if (passage == null) {
                return List.of();
            }
            passages.add(passage);
        }
        return List.copyOf(passages);
    }

    private String mapRefusalReason(String modelReason) {
        if (modelReason == null) {
            return "Teadmusbaasis ei ole küsimusele piisavat infot.";
        }
        return switch (modelReason.trim().toUpperCase(Locale.ROOT)) {
            case "OUT_OF_SCOPE" -> "Küsimus jääb IT-teenuste teadmusbaasi ulatusest välja.";
            case "NOT_FOUND" -> "Teadmusbaasis ei ole küsimusele piisavat infot.";
            case "UNSAFE" -> "Päring ei ole agendi turvareeglite järgi lubatud.";
            default -> GROUNDING_FAILURE;
        };
    }

    private Set<String> eligibleAnswerIds(String question, List<AgentExchange> history) {
        List<KnowledgePassage> direct = repository.search(question);
        if (!direct.isEmpty()) {
            return direct.stream().map(KnowledgePassage::id)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (!isFollowUp(question) || history.isEmpty()) {
            return Set.of();
        }
        String contextualQuery = question + " " + history.getLast().question();
        return repository.search(contextualQuery).stream().map(KnowledgePassage::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private boolean isTopicListQuestion(String question) {
        return normalize(question).contains("teem");
    }

    private boolean isDeployQuestion(String question) {
        String normalized = normalize(question);
        return normalized.contains("deploy") || normalized.contains("juurut");
    }

    private boolean isFollowUp(String question) {
        String normalized = normalize(question);
        return normalized.matches(".*\\b(see|seda|selle|sellest|aga)\\b.*")
                || normalized.contains("kui kaua")
                || normalized.startsWith("kust")
                || normalized.contains("mis edasi");
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
