package network.foodsharing.common.text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mention tokens in forum posts and chat messages: {@code @[Display Name](user:<uuid>)} (A36; the client renders them
 * as user chips, {@code message-body.ts}). Plain-text places (activity feed excerpts, search snippets, push and mail
 * texts) show them as {@code @Display Name}; they are converted before any cutting, so no half token remains.
 */
public final class MentionText {

    /** Group 1 the display name, group 2 the user id. */
    public static final Pattern TOKEN = Pattern.compile(
            "@\\[([^\\]\\n]{1,200})\\]\\(user:([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})\\)");

    private MentionText() {
    }

    /** {@code text} with every mention token replaced by {@code @Name}; null stays null. */
    public static String plain(String text) {
        if (text == null || text.indexOf("@[") < 0) {
            return text;
        }
        return TOKEN.matcher(text).replaceAll(match -> Matcher.quoteReplacement("@" + match.group(1)));
    }
}
