package ee.smit.agent.knowledge;

/**
 * A canonical, read-only passage from the curated knowledge base.  The file
 * name is deliberately metadata, not an input path supplied by a caller.
 */
public record KnowledgePassage(String id, String file, String title, String excerpt) {
}
