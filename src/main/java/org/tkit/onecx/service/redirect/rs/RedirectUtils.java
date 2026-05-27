package org.tkit.onecx.service.redirect.rs;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public class RedirectUtils {

    /**
     * Converts a HostForwardRule to JSON representation with pattern and replacement pattern.
     *
     * @param rule the host forward rule
     * @return JSON object string representation
     */
    public static String hostForwardRuleToJson(RedirectConfig.HostForwardRule rule) {
        String protocolPattern = rule.protocolReplacePattern().orElse(null);
        return "{\"hostPattern\":" + jsonString(rule.hostPattern())
                + ",\"hostReplacePattern\":" + jsonString(rule.hostReplacePattern())
                + ",\"protocolReplacePattern\":" + (protocolPattern == null ? "null" : jsonString(protocolPattern)) + "}";
    }

    private RedirectUtils() {
    }

    /**
     * Sorts the given client rules by their numeric index key and serializes them
     * to a JSON array string for use in the redirect template.
     */
    public static String rulesToJson(Map<String, RedirectConfig.ClientRule> clientRules) {
        return clientRules.entrySet().stream()
                .sorted(Comparator.comparing(e -> {
                    try {
                        return Integer.parseInt(e.getKey());
                    } catch (NumberFormatException ex) {
                        return Integer.MAX_VALUE;
                    }
                }))
                .map(e -> "{\"pattern\":" + jsonString(e.getValue().pattern())
                        + ",\"replacePattern\":" + jsonString(e.getValue().replacePattern()) + "}")
                .collect(Collectors.joining(",", "[", "]"));
    }

    /**
     * Wraps a value in JSON double-quotes, escaping any contained double-quotes.
     */
    public static String jsonString(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
