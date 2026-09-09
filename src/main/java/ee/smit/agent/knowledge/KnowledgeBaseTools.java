package ee.smit.agent.knowledge;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only Spring AI callable functions in this package. Both delegate to the
 * fixed read-only repository and expose neither paths nor external clients.
 */
@Component
public class KnowledgeBaseTools {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseTools(KnowledgeBaseRepository repository) {
        this.repository = repository;
    }

    @Tool(name = "listTopics", description = "Loetleb teadmusbaasi lubatud IT-teenuste teemad.")
    public List<KnowledgePassage> listTopics() {
        return repository.listTopics();
    }

    @Tool(name = "searchKnowledgeBase", description = "Otsib lubatud teadmusbaasist küsimusega seotud lõike.")
    public List<KnowledgePassage> searchKnowledgeBase(
            @ToolParam(description = "Kasutaja küsimus või otsingusõnad, mitte failitee.") String query) {
        return repository.search(query);
    }
}
