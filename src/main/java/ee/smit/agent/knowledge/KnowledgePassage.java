package ee.smit.agent.knowledge;

/**
 * A read-only passage from the knowledge base. The file name is metadata, not
 * an input path supplied by a caller.
 */
public record KnowledgePassage(String id, String file, String title, String excerpt) {
}
