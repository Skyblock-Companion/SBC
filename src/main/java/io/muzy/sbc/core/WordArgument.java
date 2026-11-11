// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.LiteralMessage;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// one word like brigadiers word arg, except the sub commands next to it: "/sbc speed set" is the set command,
// never a preset named "set". with a plain word next to those literals the game logs an "Ambiguity" warning
public final class WordArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType TAKEN = new SimpleCommandExceptionType(new LiteralMessage("That word is a command of its own"));
    private final Set<String> taken;

    private WordArgument(Set<String> taken) {
        this.taken = taken;
    }

    // a word thats none of these sub commands
    public static WordArgument except(String... subCommands) {
        java.util.HashSet<String> s = new java.util.HashSet<>();
        for (String c : subCommands) s.add(c.toLowerCase(Locale.ROOT));
        return new WordArgument(Set.copyOf(s));
    }

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        String word = reader.readUnquotedString();
        if (taken.contains(word.toLowerCase(Locale.ROOT))) {
            reader.setCursor(start);
            throw TAKEN.createWithContext(reader);
        }
        return word;
    }

    @Override
    public Collection<String> getExamples() {
        return List.of("word", "name_1");
    }
}
