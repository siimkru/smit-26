package ee.smit.agent.security;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deterministic checks run before any user text can be sent to OpenAI. */
@Component
public class RequestSecurityService {

    public static final int MAX_QUESTION_LENGTH = 2_000;

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestSecurityService.class);
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("\\b(?:ignore|forget|disregard)\\s+(?:(?:all|any|the|your|previous|prior)\\s+){0,4}(?:instructions|rules|guardrails)\\b"),
            Pattern.compile("\\b(?:ignoreeri|eira|unusta)\\s+(?:(?:koik[a-z]*|oma|eelmis[a-z]*)\\s+){0,4}(?:reegl[a-z]*|juhis[a-z]*)\\b"),
            Pattern.compile("\\b(?:you are now|act as|pretend to be|sa oled nuud|kaitu nagu|dan)\\b"),
            Pattern.compile("(?:^|\\s|[<\\[])(?:system|assistant|developer)\\s*(?::|>|\\])"),
            Pattern.compile("\\b(?:override|rewrite|replace|change|disable|bypass)\\b[^\\r\\n]{0,100}\\b(?:system prompt|instructions|rules|tools|safeguards|restrictions)\\b"),
            Pattern.compile("\\b(?:kirjuta umber|muuda|asenda|keela|hiili mooda)\\b[^\\r\\n]{0,100}\\b(?:susteemiprompt[a-z]*|juhis[a-z]*|reegl[a-z]*|tooriist[a-z]*|piirang[a-z]*)\\b"),
            Pattern.compile("\\b(?:kirjuta|seadista|defineeri)\\b[^\\r\\n]{0,80}\\b(?:susteemiprompt[a-z]*|prompt[a-z]*|juhis[a-z]*|reegl[a-z]*|tooriist[a-z]*)\\b[^\\r\\n]{0,30}\\b(?:umber|uuesti)\\b"),
            Pattern.compile("\\b(?:system prompt|internal instructions|available tools|tool definitions|susteemiprompt[a-z]*|sisemis[a-z]* juhis[a-z]*|tooriist[a-z]* definitsioon[a-z]*)\\b"),
            Pattern.compile("\\b(?:what|which|millis[a-z]*)\\b[^\\r\\n]{0,60}\\b(?:tools|tooriist[a-z]*)\\b[^\\r\\n]{0,40}\\b(?:call|use|kasuta|kutsu)[a-z]*\\b"),
            Pattern.compile("\\b(?:repeat|print|show|reveal|return|list|korda|avalda|naita|tagasta|loetle)\\b[^\\r\\n]{0,160}\\b(?:messages sent before|messages[^\\r\\n]{0,40}before|sonum[a-z]*[^\\r\\n]{0,40}enne|before my question|koik sonumid|enne minu kusimust|system prompt|available tools|tool definitions|susteemiprompt[a-z]*|tooriist[a-z]*)\\b")
    );
    private static final List<Pattern> SECRET_VALUE_PATTERNS = List.of(
            Pattern.compile("\\bsk-[A-Za-z0-9_-]{16,}\\b"),
            Pattern.compile("\\bAKIA[0-9A-Z]{16}\\b"),
            Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{20,}\\b"),
            Pattern.compile("-----BEGIN [A-Z ]{0,40}PRIVATE KEY-----"),
            Pattern.compile("\\bBearer\\s+[A-Za-z0-9._~+/=-]{12,}\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\d)\\d{11}(?!\\d)")
    );
    private static final Pattern LABELED_SECRET = Pattern.compile(
            "\\b(?:password|passwd|parool|api[ _-]?(?:key|voti)|access[ _-]?token|secret|saladus)"
                    + "\\s*(?::|=|\\bis\\b|\\bon\\b)\\s*\\S+");
    private static final Pattern LABELED_PERSONAL_ID = Pattern.compile(
            "\\b(?:isikukood|personal code|national id)\\s*(?::|=|\\bon\\b|\\bis\\b)?"
                    + "\\s*[0-9][0-9 -]{9,15}[0-9]\\b");
    private static final Pattern SENSITIVE_REQUEST_ACTION = Pattern.compile(
            "\\b(?:give|show|reveal|return|find|list|anna|naita|avalda|tagasta|otsi|mis on|what is)\\b");
    private static final Pattern SENSITIVE_REQUEST_SECRET = Pattern.compile(
            "\\b(?:(?:administrator|administraator|administraatori|admin)\\s+)?"
                    + "(?:password|passwd|parool|api[ _-]?(?:key|voti)|token|credentials|saladus)\\b");
    private static final Pattern DESTRUCTIVE = Pattern.compile(
            "\\b(delete|remove|erase|kustuta)\\b[^\\r\\n]{0,30}\\b(all|koik)\\b[^\\r\\n]{0,20}\\b(files|failid|andmebaas)\\b");
    private static final Pattern ABSOLUTE_PATH = Pattern.compile(
            "(?:^|\\s|['\"])(?:[a-z]:[\\\\/]|/(?:[a-z0-9._-]+(?:/|$))+|\\\\\\\\)",
            Pattern.CASE_INSENSITIVE);

    public Optional<String> refusalReason(String question) {
        Optional<Violation> violation = detect(question);
        violation.ifPresent(value -> LOGGER.warn(
                "Security gate refused request; category={}; questionLength={}",
                value.category(), question == null ? 0 : question.length()));
        return violation.map(Violation::reason);
    }

    private Optional<Violation> detect(String question) {
        if (question == null || question.isBlank()) {
            return Optional.of(new Violation("INVALID_INPUT", "Päring peab sisaldama küsimust."));
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            return Optional.of(new Violation("INPUT_TOO_LONG",
                    "Päring ületab lubatud pikkuse " + MAX_QUESTION_LENGTH + " tähemärki."));
        }

        String normalized = normalize(question);
        if (looksLikeForbiddenPath(normalized)) {
            return Optional.of(new Violation("FORBIDDEN_PATH", "Päring üritab kasutada lubamatut failiteed."));
        }
        if (SECRET_VALUE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(question).find())
                || LABELED_SECRET.matcher(normalized).find()
                || LABELED_PERSONAL_ID.matcher(normalized).find()
                || containsSensitiveRequest(normalized)) {
            return Optional.of(new Violation("SENSITIVE_INPUT",
                    "Päring sisaldab või küsib tundlikke autentimis- või isikuandmeid."));
        }
        if (DESTRUCTIVE.matcher(normalized).find()) {
            return Optional.of(new Violation("DESTRUCTIVE_REQUEST", "Agent ei täida destruktiivseid juhiseid."));
        }
        if (INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find())) {
            return Optional.of(new Violation("PROMPT_INJECTION",
                    "Päring sisaldab katset muuta agendi juhiseid või avaldada sisemist infot."));
        }
        return Optional.empty();
    }

    private boolean looksLikeForbiddenPath(String normalized) {
        return normalized.contains("../")
                || normalized.contains("..\\")
                || normalized.contains("/etc/passwd")
                || normalized.contains("\\etc\\passwd")
                || normalized.indexOf('\0') >= 0
                || ABSOLUTE_PATH.matcher(normalized).find();
    }

    private boolean containsSensitiveRequest(String normalized) {
        Matcher secretMatcher = SENSITIVE_REQUEST_SECRET.matcher(normalized);
        while (secretMatcher.find()) {
            Matcher actionMatcher = SENSITIVE_REQUEST_ACTION.matcher(normalized);
            while (actionMatcher.find()) {
                if (secretMatcher.start() >= actionMatcher.end()
                        && secretMatcher.start() - actionMatcher.end() <= 100) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private record Violation(String category, String reason) {
    }
}
