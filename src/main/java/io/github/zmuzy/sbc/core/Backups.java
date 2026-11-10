// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

// copies of your settings + saved data: one every start (last 10 kept) and /sbc backup
public final class Backups {
    private static final Path DIR = Config.DIR.resolve("backups");

    private Backups() {}

    public static String make() {
        try {
            Config.save();
            Store.save();
            Path to = DIR.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")));
            Files.createDirectories(to);
            for (String f : List.of("config.json", "data.json")) {
                Path from = Config.DIR.resolve(f);
                if (Files.exists(from)) Files.copy(from, to.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            }
            prune();
            return to.getFileName().toString();
        } catch (IOException e) {
            return null;
        }
    }

    private static void prune() throws IOException {
        try (Stream<Path> s = Files.list(DIR)) {
            List<Path> all = new ArrayList<>(s.filter(Files::isDirectory).sorted(Comparator.comparing(Path::getFileName)).toList());
            for (int i = 0; i < all.size() - 10; i++)
                try (Stream<Path> files = Files.walk(all.get(i))) {
                    files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                }
        }
    }

    public static List<String> list() {
        try (Stream<Path> s = Files.list(DIR)) {
            return s.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted(Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    // puts a backup back. needs a restart bc the game rewrites the files on exit
    public static boolean restore(String name) {
        Path from = DIR.resolve(name);
        if (!Files.isDirectory(from)) return false;
        try {
            for (String f : List.of("config.json", "data.json"))
                if (Files.exists(from.resolve(f))) Files.copy(from.resolve(f), Config.DIR.resolve(f + ".restored"), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
