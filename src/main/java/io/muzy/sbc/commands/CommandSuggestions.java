// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

// from skyhanni SuggestionProvider + SuggestionEntry: which words can come next in a command
final class CommandSuggestions {
    interface Entry {
        List<String> suggestions();

        default boolean isEntryFor(String argument) {
            for (String s : suggestions()) if (s.equalsIgnoreCase(argument)) return true;
            return false;
        }
    }

    record Literal(List<String> suggestions) implements Entry {}

    record Lazy(Supplier<List<String>> supplier) implements Entry {
        @Override
        public List<String> suggestions() {
            return supplier.get();
        }
    }

    record Composite(List<Entry> entries) implements Entry {
        @Override
        public List<String> suggestions() {
            List<String> out = new ArrayList<>();
            for (Entry e : entries) out.addAll(e.suggestions());
            return out;
        }

        @Override
        public boolean isEntryFor(String argument) {
            for (Entry e : entries) if (e.isEntryFor(argument)) return true;
            return false;
        }
    }

    record Parent(Entry parent, List<Entry> children) implements Entry {
        @Override
        public List<String> suggestions() {
            return parent.suggestions();
        }

        @Override
        public boolean isEntryFor(String argument) {
            return parent.isEntryFor(argument);
        }
    }

    record Conditional(BooleanSupplier condition, Entry entry) implements Entry {
        @Override
        public List<String> suggestions() {
            return condition.getAsBoolean() ? entry.suggestions() : List.of();
        }

        @Override
        public boolean isEntryFor(String argument) {
            return entry.isEntryFor(argument);
        }
    }

    static final class Builder {
        final List<Entry> entries = new ArrayList<>();

        Builder add(Entry e) {
            entries.add(e);
            return this;
        }

        Builder literal(String... words) {
            return add(new Literal(List.of(words)));
        }

        Builder lazy(Supplier<List<String>> supplier) {
            return add(new Lazy(supplier));
        }

        Builder conditional(BooleanSupplier condition, Consumer<Builder> children) {
            Builder b = new Builder();
            children.accept(b);
            return add(new Conditional(condition, new Composite(b.entries)));
        }

        Builder parent(List<String> words, Consumer<Builder> children) {
            Builder b = new Builder();
            children.accept(b);
            return add(new Parent(new Literal(words), b.entries));
        }
    }

    private final List<Entry> entries;

    CommandSuggestions(Consumer<Builder> build) {
        Builder b = new Builder();
        build.accept(b);
        entries = b.entries;
    }

    private static Entry forPath(List<Entry> list, List<String> path) {
        Entry found = null;
        for (Entry e : list) {
            if (e.isEntryFor(path.get(0))) {
                found = e;
                break;
            }
        }
        if (path.size() > 1) return found instanceof Parent p ? forPath(p.children(), path.subList(1, path.size())) : null;
        return found instanceof Parent p ? new Composite(p.children()) : null;
    }

    // command without the slash as typed so far -> what the current word can become
    List<String> get(String command) {
        String[] args = command.toLowerCase(Locale.ROOT).split(" ", -1);
        String last = args[args.length - 1];
        List<String> all = new ArrayList<>();
        if (args.length != 1) {
            Entry e = forPath(entries, List.of(args).subList(0, args.length - 1));
            if (e != null) all.addAll(e.suggestions());
        } else {
            for (Entry e : entries) all.addAll(e.suggestions());
        }
        List<String> out = new ArrayList<>();
        for (String s : all) if (s.toLowerCase(Locale.ROOT).startsWith(last) && !out.contains(s)) out.add(s);
        return out;
    }
}
