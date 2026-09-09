package ee.smit.agent.security;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Deterministic checks run before any user text can be sent to OpenAI. */
@Component
public class RequestSecurityService {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("\\bignore (all |any )?(previous|prior) (instructions|rules)\\b"),
            Pattern.compile("\\bforget (your|all|the) (rules|instructions)\\b"),
            Pattern.compile("\\b(you are now|act as|dan)\\b"),
            Pattern.compile("\\b(system|assistant|developer)\\s*:"),
            Pattern.compile("\\b(system prompt|internal instructions|available tools|tool definitions)\\b"),
            Pattern.compile("\\b(unusta|eir[a-z]*) (koik |oma |eelmis[a-z]* )?(reegl[a-z]*|juhis[a-z]*)\\b"),
            Pattern.compile("\\b(sa oled nuud|susteemiprompt|sisemis[a-z]* juhis[a-z]*|tooriist[a-z]* definitsioon[a-z]*)\\b"),
            Pattern.compile("\\b(korda|avalda|naita|loetle).{0,120}(enne minu kusimust|koik sonumid|susteemiprompt|tooriist[a-z]*)\\b")
    );
    private static final Pattern SECRET = Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}\\b|\\b\\d{11}\\b");
    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "\\b(delete|remove|erase|kustuta).{0,30}(all|koik).{0,20}(files|failid|andmebaas)\\b");

    public Optional<String> refusalReason(String question) {
        String normalized = normalize(question);
        if (normalized.contains("../") || normalized.contains("..\\") || normalized.contains("/etc/passwd")) {
            return Optional.of("Päring üritab kasutada lubamatut failiteed.");
        }
        if (SECRET.matcher(question).find()
                || normalized.matches(".*\\b(administraatori|admin) (parool|password)\\b.*")) {
            return Optional.of("Päring sisaldab või küsib tundlikke autentimisandmeid.");
        }
        if (DESTRUCTIVE.matcher(normalized).find()) {
            return Optional.of("Agent ei täida destruktiivseid juhiseid.");
        }
        if (INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find())) {
            return Optional.of("Päring sisaldab katset muuta agendi juhiseid või avaldada sisemist infot.");
        }
        return Optional.empty();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
