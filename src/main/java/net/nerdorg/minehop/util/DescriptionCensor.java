package net.nerdorg.minehop.util;

import net.nerdorg.minehop.config.ConfigWrapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DescriptionCensor {
    private static final int MAX_DESCRIPTION_LENGTH = 240;
    private static final List<String> BANNED_WORDS = List.of(
            "fuck",
            "shit",
            "bitch",
            "asshole",
            "bastard",
            "cunt",
            "dick",
            "pussy",
            "nigger",
            "faggot",
            "slut",
            "whore",
            "retard"
    );

    private DescriptionCensor() {
    }

    public static String sanitizeAndMaybeCensor(String raw) {
        String sanitized = sanitize(raw);
        if (sanitized.isEmpty()) {
            return sanitized;
        }
        if (ConfigWrapper.config == null || !ConfigWrapper.config.censor_user_map_descriptions) {
            return sanitized;
        }
        return censor(sanitized);
    }

    public static String censorProfanity(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return censor(raw);
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('\t', ' ')
                .replace("~", " ")
                .trim();
        text = text.replaceAll(" +", " ");
        if (text.length() > MAX_DESCRIPTION_LENGTH) {
            text = text.substring(0, MAX_DESCRIPTION_LENGTH);
        }
        return text;
    }

    private static String censor(String input) {
        String censored = input;
        for (String bannedWord : BANNED_WORDS) {
            Pattern pattern = Pattern.compile(
                    "(?<![A-Za-z0-9])[A-Za-z0-9]*" + Pattern.quote(bannedWord) + "[A-Za-z0-9]*(?![A-Za-z0-9])",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(censored);
            StringBuffer buffer = new StringBuffer();
            while (matcher.find()) {
                String hit = matcher.group();
                matcher.appendReplacement(buffer, maskWord(hit));
            }
            matcher.appendTail(buffer);
            censored = buffer.toString();
        }
        return censored;
    }

    private static String maskWord(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }
        return "*".repeat(word.length());
    }
}
