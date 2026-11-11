// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.commands;

import com.google.gson.Gson;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// from skyhanni CustomTodo: a timer that starts when some line of text shows up, per profile
// field names are skyhannis on purpose so its templates and community todos load as is (base64 of
// "SH:CUSTOMTODO/" + json). dont rename
public final class Todo {
    private static final Gson GSON = new Gson();
    private static final String TEMPLATE_PREFIX = "SH:CUSTOMTODO/", NEU_TEMPLATE_PREFIX = "NEU:CUSTOMTODO/";
    private static final long MS_IN_A_DAY = 24 * 60 * 60 * 1000L;

    public enum TriggerMatcher { REGEX, STARTS_WITH, CONTAINS, EQUALS }

    public enum TriggerTarget { CHAT, ACTION_BAR, TAB_LIST, SIDEBAR }

    public enum MatchType { MATCH, NO_MATCH, ANTI_MATCH }

    public String label = "";
    public int timer;
    public String trigger = "", antiTrigger = "", icon = "";
    public boolean isResetOffset;
    public int showWhen;
    public boolean showOnlyWhenReady;
    public TriggerTarget triggerTarget = TriggerTarget.CHAT;
    public TriggerMatcher triggerMatcher = TriggerMatcher.CONTAINS;
    public Map<String, Long> readyAt = new HashMap<>();
    public boolean isEnabled = true, ignoreColorCodes = true;
    public int totalTriggers = 1;
    public Map<String, Integer> triggersLeft = new HashMap<>();
    public boolean cronEnabled;
    public String cronExpression = "";
    public boolean downloaded;
    public String downloadedId = "";
    private transient Pattern regex, antiRegex;
    private transient String regexFrom, antiFrom, cronFrom;
    private transient long cronReady, cronNext;

    public boolean isValid() {
        return isEnabled && timer >= 0 && ((!trigger.isBlank() && !cronEnabled) || (cronEnabled && !cronExpression.isBlank()));
    }

    public void setDoneNow(String profile, long now) {
        int left = triggersLeft.getOrDefault(profile, 1);
        if (left > 1 && !cronEnabled) {
            triggersLeft.put(profile, left - 1);
            return;
        }
        triggersLeft.put(profile, totalTriggers);
        if (isResetOffset) {
            long at = now - now % MS_IN_A_DAY + timer * 1000L;
            readyAt.put(profile, at < now ? at + MS_IN_A_DAY : at);
        } else readyAt.put(profile, cronEnabled ? now : now + timer * 1000L);
    }

    public void antiTriggered(String profile, long now) {
        if (cronEnabled) return;
        int left = triggersLeft.getOrDefault(profile, totalTriggers);
        if (readyAt.getOrDefault(profile, Long.MIN_VALUE) > now) {
            readyAt.put(profile, 0L);
            triggersLeft.put(profile, 1);
        } else if (left < totalTriggers) {
            triggersLeft.put(profile, left + 1);
        }
    }

    private static String time(long ms) {
        return Reminders.format(ms);
    }

    // skyhanni getTimerString + getTimerCronString. null when theres nothing to show rn
    public String timer(String profile, long now) {
        if (!isValid()) return null;
        Long ready = readyAt.get(profile);
        if (ready == null) return null;
        if (cronEnabled) return cronTimer(profile, ready, now);
        int triggers = triggersLeft.getOrDefault(profile, totalTriggers);
        long until = ready - now;
        if (until <= 0) return totalTriggers == 1 ? "§aReady" : "§a" + triggers + " Left";
        if (showOnlyWhenReady) return null;
        if (showWhen != 0 && until / 1000 > showWhen) return null;
        return "§c" + time(until);
    }

    private String cronTimer(String profile, long ready, long now) {
        if (!cronExpression.equals(cronFrom) || ready != cronReady) {
            cronFrom = cronExpression;
            cronReady = ready;
            cronNext = Cron.nextAfter(cronExpression, ready);
        }
        long next = cronNext;
        if (next < 0) return null;
        long end = next + timer * 1000L;
        if (next <= now && end > now) return "§aReady";
        if (end <= now) readyAt.put(profile, now);
        if (showOnlyWhenReady) return null;
        if (showWhen != 0 && (next - now) / 1000 > showWhen) return null;
        return "§c" + time(Math.max(0, next - now));
    }

    private static Pattern compiled(String text) {
        try {
            return Pattern.compile(text);
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    // skyhanni CustomTodosGui.matchString, text already with or without colors the way ignoreColorCodes wants
    public MatchType match(String text) {
        if (!isValid()) return MatchType.NO_MATCH;
        boolean anti = !antiTrigger.isBlank();
        switch (triggerMatcher) {
            case REGEX -> {
                if (!trigger.equals(regexFrom)) {
                    regexFrom = trigger;
                    regex = compiled(trigger);
                }
                if (regex == null) return MatchType.NO_MATCH;
                if (regex.matcher(text).matches()) return MatchType.MATCH;
                if (!anti) return MatchType.NO_MATCH;
                if (!antiTrigger.equals(antiFrom)) {
                    antiFrom = antiTrigger;
                    antiRegex = compiled(antiTrigger);
                }
                if (antiRegex != null && antiRegex.matcher(text).matches()) return MatchType.ANTI_MATCH;
            }
            case STARTS_WITH -> {
                if (text.startsWith(trigger)) return MatchType.MATCH;
                if (anti && text.startsWith(antiTrigger)) return MatchType.ANTI_MATCH;
            }
            case CONTAINS -> {
                if (text.contains(trigger)) return MatchType.MATCH;
                if (anti && text.contains(antiTrigger)) return MatchType.ANTI_MATCH;
            }
            case EQUALS -> {
                if (text.equals(trigger)) return MatchType.MATCH;
                if (anti && text.equals(antiTrigger)) return MatchType.ANTI_MATCH;
            }
        }
        return MatchType.NO_MATCH;
    }

    // skyhanni fromTemplateOrNull, state cleared + switched on
    public static Todo fromTemplate(String data) {
        try {
            String raw = new String(Base64.getDecoder().decode(data.trim()), StandardCharsets.UTF_8);
            String json;
            if (raw.startsWith(TEMPLATE_PREFIX)) json = raw.substring(TEMPLATE_PREFIX.length());
            else if (raw.startsWith(NEU_TEMPLATE_PREFIX)) json = raw.substring(NEU_TEMPLATE_PREFIX.length());
            else return null;
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            o.remove("readyAt");
            o.remove("triggersLeft");
            o.remove("position");
            Todo t = GSON.fromJson(o, Todo.class);
            if (t == null) return null;
            t.isEnabled = true;
            if (t.readyAt == null) t.readyAt = new HashMap<>();
            if (t.triggersLeft == null) t.triggersLeft = new HashMap<>();
            if (t.triggerTarget == null) t.triggerTarget = TriggerTarget.CHAT;
            if (t.triggerMatcher == null) t.triggerMatcher = TriggerMatcher.CONTAINS;
            if (t.totalTriggers < 1) t.totalTriggers = 1;
            return t;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String toTemplate() {
        com.google.gson.JsonObject o = GSON.toJsonTree(this).getAsJsonObject();
        o.add("readyAt", new com.google.gson.JsonObject());
        o.add("triggersLeft", new com.google.gson.JsonObject());
        o.addProperty("downloaded", false);
        o.addProperty("downloadedId", "");
        return Base64.getEncoder().encodeToString((TEMPLATE_PREFIX + GSON.toJson(o)).getBytes(StandardCharsets.UTF_8));
    }
}
