// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// the tiny lisp hotmlayout.json writes perk numbers in. skyblocker SkillTreeBuilder + LispExtensions run it
// on a library, this only runs what the file really uses
final class TreeLisp {
    private record Sym(String name) {}

    private record Fn(List<String> params, Object body) {}

    private final Map<String, Fn> functions = new HashMap<>();

    // the files prelude: lines of (defun name (params) body)
    TreeLisp(List<String> prelude) {
        for (String line : prelude) {
            try {
                if (parse(line) instanceof List<?> l && l.size() == 4 && l.get(0) instanceof Sym s && s.name().equals("defun")
                        && l.get(1) instanceof Sym name && l.get(2) instanceof List<?> params) {
                    List<String> names = new ArrayList<>();
                    for (Object p : params) names.add(((Sym) p).name());
                    functions.put(name.name(), new Fn(names, l.get(3)));
                }
            } catch (RuntimeException ignored) {
            }
        }
    }

    // Double, String (":COAL" -> "COAL", same for a bare name nothing is bound to), Boolean, List, or null
    Object run(String source, Map<String, Double> bindings) {
        if (source == null || source.isBlank()) return null;
        try {
            return eval(parse(source), new HashMap<>(bindings));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Object parse(String source) {
        int[] at = {0};
        return read(source, at);
    }

    private static Object read(String s, int[] at) {
        while (at[0] < s.length() && Character.isWhitespace(s.charAt(at[0]))) at[0]++;
        char c = s.charAt(at[0]);
        if (c == '(') {
            at[0]++;
            List<Object> out = new ArrayList<>();
            while (true) {
                while (Character.isWhitespace(s.charAt(at[0]))) at[0]++;
                if (s.charAt(at[0]) == ')') {
                    at[0]++;
                    return out;
                }
                out.add(read(s, at));
            }
        }
        if (c == '"') {
            int end = s.indexOf('"', at[0] + 1);
            String text = s.substring(at[0] + 1, end);
            at[0] = end + 1;
            return text;
        }
        int start = at[0];
        while (at[0] < s.length() && !Character.isWhitespace(s.charAt(at[0])) && s.charAt(at[0]) != '(' && s.charAt(at[0]) != ')') at[0]++;
        String word = s.substring(start, at[0]);
        if (word.startsWith(":")) return word.substring(1);
        try {
            return Double.parseDouble(word);
        } catch (NumberFormatException e) {
            return new Sym(word);
        }
    }

    private Object eval(Object ast, Map<String, Object> env) {
        if (ast instanceof Sym s) {
            if (s.name().equals("true")) return Boolean.TRUE;
            if (s.name().equals("false")) return Boolean.FALSE;
            return env.containsKey(s.name()) ? env.get(s.name()) : s.name();
        }
        if (!(ast instanceof List<?> list)) return ast;
        String op = ((Sym) list.get(0)).name();
        if (op.equals("if")) return eval(list.get(Boolean.TRUE.equals(eval(list.get(1), env)) ? 2 : 3), env);
        List<Object> args = new ArrayList<>();
        for (int i = 1; i < list.size(); i++) args.add(eval(list.get(i), env));
        Fn fn = functions.get(op);
        if (fn != null) {
            Map<String, Object> inner = new HashMap<>(env);
            for (int i = 0; i < fn.params().size(); i++) inner.put(fn.params().get(i), args.get(i));
            return eval(fn.body(), inner);
        }
        return switch (op) {
            case "lt" -> num(args, 0) < num(args, 1);
            case "gt" -> num(args, 0) > num(args, 1);
            case "=" -> num(args, 0) == num(args, 1);
            case "+" -> {
                double v = 0;
                for (int i = 0; i < args.size(); i++) v += num(args, i);
                yield v;
            }
            case "*" -> {
                double v = 1;
                for (int i = 0; i < args.size(); i++) v *= num(args, i);
                yield v;
            }
            case "-" -> num(args, 0) - num(args, 1);
            case "/" -> num(args, 0) / num(args, 1);
            case "pow" -> Math.pow(num(args, 0), num(args, 1));
            case "round" -> (double) Math.round(num(args, 0));
            case "ceil" -> Math.ceil(num(args, 0));
            case "floor" -> Math.floor(num(args, 0));
            case "list.new" -> args;
            case "list.at" -> ((List<?>) args.get(0)).get((int) num(args, 1));
            default -> throw new IllegalArgumentException(op);
        };
    }

    private static double num(List<Object> args, int i) {
        return (Double) args.get(i);
    }
}
