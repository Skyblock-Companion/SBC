// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// takes off hypixels action bar what our status bars already show (health, defense, mana), keeps the rest:
// ability names, healing, mana use, rift time, secrets. logic + patterns from skyblocker
public final class ActionBarFilter {
    private static final char HEALTH = '', DEFENSE = '', MANA = '', OVERFLOW = '', VITALITY = '', RIFT_TIME = '';
    static final Pattern STATUS = Pattern.compile("(?<status>.+?)(?: {2,}|$)");
    static final Pattern RIFT_TIME_STATUS = Pattern.compile("(?:[\\d,]+m)?[\\d,]+s[ф" + RIFT_TIME + "] Left");
    static final Pattern HEALTH_STATUS = Pattern.compile("(?<health>[\\d,]+)/(?<max>[\\d,]+)[❤" + HEALTH + "](?<healing>\\+([\\d,]+)[▁-▆])?");
    static final Pattern VITALITY_STATUS = Pattern.compile("(?<vitality>[\\d,]+)/(?<max>[\\d,]+)" + VITALITY);
    static final Pattern HEALING = Pattern.compile("(?:§[\\da-z])*[❤" + HEALTH + "]");
    static final Pattern DEFENSE_STATUS = Pattern.compile("(?<defense>[\\d,]+)[❈" + DEFENSE + "]( Defense)?");
    static final Pattern MANA_USE = Pattern.compile("-([\\d,]+) Mana \\(.*?\\)");
    private static final Pattern MANA_USE_PART = Pattern.compile("(?:§.)*-[\\d,]+ Mana \\(.*?\\)(?:§.)*(?: {2,}|$)");
    static final Pattern MANA_STATUS = Pattern.compile("(?<mana>[\\d,]+)/(?<max>[\\d,]+)[✎" + MANA + "] ?(?:Mana|(?<overflow>[\\d,]+)[ʬ" + OVERFLOW + "])?");

    private static final Patterns.P SKILL_GAIN = Patterns.p("Vitals.GAIN"), SKILL_GAIN_NUMBERS = Patterns.p("Vitals.GAIN2");

    private ActionBarFilter() {}

    private static String strip(String s) {
        return s.replaceAll("§.", "");
    }

    // from skyblocker StatusBarTracker.filterManaUse: "-20 Mana (Ability)" leaves the action bar
    public static String dropManaUse(String actionBar) {
        return MANA_USE_PART.matcher(actionBar).replaceAll("").stripTrailing();
    }

    public static String filter(String actionBar, boolean hideHealth, boolean hideDefense, boolean hideMana, boolean inRift) {
        return filter(actionBar, hideHealth, hideDefense, hideMana, false, inRift);
    }

    // action bar minus the chosen statuses, same string back when nothing changed
    public static String filter(String actionBar, boolean hideHealth, boolean hideDefense, boolean hideMana, boolean hideSkill, boolean inRift) {
        Matcher statuses = STATUS.matcher(actionBar);
        StringBuilder out = new StringBuilder();
        while (statuses.find()) {
            String plain = strip(statuses.group("status"));
            Matcher status;
            // "+12.5 Farming (96.7%)" or "+12.5 Farming (1,234/5,678)", same patterns as our skill bar
            if (hideSkill && (SKILL_GAIN.matcher(plain).find() || SKILL_GAIN_NUMBERS.matcher(plain).find())) {
                statuses.appendReplacement(out, "");
                continue;
            }
            if (inRift) {
                status = RIFT_TIME_STATUS.matcher(plain);
                if (status.find()) {
                    statuses.appendReplacement(out, "$0");
                    continue;
                }
            } else {
                status = HEALTH_STATUS.matcher(plain);
                if (status.find()) {
                    if (!hideHealth) statuses.appendReplacement(out, "$0");
                    else if (status.group("healing") == null) statuses.appendReplacement(out, "");
                    else {
                        // keep healing + its color
                        Matcher heal = HEALING.matcher(statuses.group());
                        if (!heal.find()) {
                            statuses.appendReplacement(out, "");
                            continue;
                        }
                        String rest = statuses.group().substring(heal.start());
                        if (!rest.startsWith("§")) rest = "§c" + rest;
                        statuses.appendReplacement(out, Matcher.quoteReplacement(rest));
                    }
                    continue;
                }
                status = DEFENSE_STATUS.matcher(plain);
                if (status.find()) {
                    statuses.appendReplacement(out, hideDefense ? "" : "$0");
                    continue;
                }
                status = VITALITY_STATUS.matcher(plain);
                if (status.find()) {
                    statuses.appendReplacement(out, hideHealth ? "" : "$0");
                    continue;
                }
            }
            status = MANA_USE.matcher(plain);
            if (status.find()) {
                statuses.appendReplacement(out, "$0");
                continue;
            }
            status = MANA_STATUS.matcher(plain);
            if (status.find()) {
                statuses.appendReplacement(out, hideMana ? "" : "$0");
                continue;
            }
            statuses.appendReplacement(out, "$0");
        }
        statuses.appendTail(out);
        return out.toString().trim();
    }
}
