package ee.smit.agent.security;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
    private static final Pattern HTML_NUMERIC_ENTITY = Pattern.compile("&#(?:x([0-9a-fA-F]{1,6})|([0-9]{1,7}));");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    private static final Pattern BASE64_TOKEN = Pattern.compile("(?<![A-Za-z0-9+/])[A-Za-z0-9+/]{20,}={0,2}(?![A-Za-z0-9+/])");
    private static final Map<Character, Character> CONFUSABLES = Map.ofEntries(
            Map.entry('а', 'a'), Map.entry('е', 'e'), Map.entry('о', 'o'), Map.entry('р', 'p'),
            Map.entry('с', 'c'), Map.entry('х', 'x'), Map.entry('у', 'y'), Map.entry('і', 'i'),
            Map.entry('к', 'k'), Map.entry('м', 'm'), Map.entry('н', 'h'), Map.entry('т', 't'),
            Map.entry('Α', 'A'), Map.entry('α', 'a'), Map.entry('Ε', 'E'), Map.entry('ε', 'e'),
            Map.entry('Ι', 'I'), Map.entry('ι', 'i'), Map.entry('Ο', 'O'), Map.entry('ο', 'o'),
            Map.entry('Ρ', 'P'), Map.entry('ρ', 'p'), Map.entry('Χ', 'X'), Map.entry('χ', 'x'));
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
            "\\b(?:password|passwd|parool(?:i|iks)?|salasona(?:ks)?|saladus(?:eks)?|"
                    + "api[ _-]?(?:key|voti)|(?:access[ _-]?)?token|secret)"
                    + "(?:[\\\"']?\\s*(?::|=)\\s*[\\\"']?\\S+"
                    + "|\\s+(?:(?:\\bis\\b|\\bon\\b|\\bwould\\b|\\boleks\\b)\\s+)?\\S+)");
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

        String normalized = normalize(decodeEncodedForms(question));
        if (looksLikeForbiddenPath(normalized)) {
            return Optional.of(new Violation("FORBIDDEN_PATH", "Päring üritab kasutada lubamatut failiteed."));
        }
        if (SECRET_VALUE_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(question).find()
                || pattern.matcher(normalized).find())
                || LABELED_SECRET.matcher(normalized).find()
                || LABELED_PERSONAL_ID.matcher(normalized).find()
                || containsSensitiveRequest(normalized)) {
            return Optional.of(new Violation("SENSITIVE_INPUT",
                    "Päring sisaldab või küsib tundlikke autentimis- või isikuandmeid."));
        }
        if (DESTRUCTIVE.matcher(normalized).find()) {
            return Optional.of(new Violation("DESTRUCTIVE_REQUEST", "Agent ei täida destruktiivseid juhiseid."));
        }
        if (containsInjection(normalized) || containsEncodedInjection(question)) {
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
        String compatibilityNormalized = Normalizer.normalize(
                        Normalizer.normalize(value, Normalizer.Form.NFKC), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("\\p{Cf}", "")
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(compatibilityNormalized.length());
        compatibilityNormalized.chars()
                .mapToObj(character -> (char) character)
                .forEach(character -> result.append(CONFUSABLES.getOrDefault(character, character)));
        return result.toString();
    }

    private String decodeEncodedForms(String value) {
        String decoded = value;
        try {
            for (int pass = 0; pass < 3; pass++) {
                String previous = decoded;
                if (decoded.contains("%")) {
                    decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                }
                decoded = decodeNumericHtmlEntities(decoded);
                decoded = decodeUnicodeEscapes(decoded);
                if (decoded.equals(previous)) {
                    break;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Malformed percent encoding remains data and is handled by the
            // remaining validation and grounding boundaries.
        }
        return decoded;
    }

    private String decodeNumericHtmlEntities(String value) {
        Matcher matcher = HTML_NUMERIC_ENTITY.matcher(value);
        StringBuffer decoded = new StringBuffer();
        while (matcher.find()) {
            String digits = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            int codePoint = Integer.parseInt(digits, matcher.group(1) != null ? 16 : 10);
            String replacement = Character.isValidCodePoint(codePoint)
                    ? new String(Character.toChars(codePoint)) : matcher.group();
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(decoded);
        return decoded.toString();
    }

    private String decodeUnicodeEscapes(String value) {
        Matcher matcher = UNICODE_ESCAPE.matcher(value);
        StringBuffer decoded = new StringBuffer();
        while (matcher.find()) {
            char replacement = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(decoded, Matcher.quoteReplacement(String.valueOf(replacement)));
        }
        matcher.appendTail(decoded);
        return decoded.toString();
    }

    private boolean containsInjection(String normalized) {
        return INJECTION_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    private boolean containsEncodedInjection(String question) {
        Matcher matcher = BASE64_TOKEN.matcher(question);
        while (matcher.find()) {
            try {
                String decoded = new String(java.util.Base64.getDecoder().decode(matcher.group()), StandardCharsets.UTF_8);
                if (decoded.chars().allMatch(character -> character == '\n' || character == '\r' || character == '\t'
                        || character >= 0x20) && containsInjection(normalize(decoded))) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // A non-Base64 token is ordinary user data.
            }
        }
        return false;
    }

    private record Violation(String category, String reason) {
    }
}
