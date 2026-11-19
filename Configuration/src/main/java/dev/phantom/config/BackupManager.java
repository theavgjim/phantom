package dev.phantom.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling per-profile backups in {@code backups/}. Automatic backups are taken at most once per
 * {@link #AUTO_INTERVAL_MS} before a profile file is overwritten; the newest {@link #KEEP} are kept.
 */
public final class BackupManager {
    public static final long AUTO_INTERVAL_MS = 10 * 60 * 1000L;
    public static final int KEEP = 10;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path dir;
    private final Map<String, Long> lastBackup = new ConcurrentHashMap<>();

    public BackupManager(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    /** Backs up {@code source} if the last automatic backup of this profile is old enough. */
    public void maybeBackup(String profile, Path source) throws IOException {
        long now = System.currentTimeMillis();
        Long last = lastBackup.get(profile);
        if (last != null && now - last < AUTO_INTERVAL_MS) return;
        backup(profile, source);
    }

    public Path backup(String profile, Path source) throws IOException {
        if (!Files.isRegularFile(source)) return null;
        Files.createDirectories(dir);
        Path target = dir.resolve(profile + "_" + LocalDateTime.now().format(STAMP) + ".json");
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        lastBackup.put(profile, System.currentTimeMillis());
        prune(profile);
        return target;
    }

    public List<Path> list(String profile) throws IOException {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, profile + "_*.json")) {
            for (Path p : ds) out.add(p);
        }
        out.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        return out;
    }

    private void prune(String profile) throws IOException {
        List<Path> all = list(profile);
        for (int i = KEEP; i < all.size(); i++) Files.deleteIfExists(all.get(i));
    }
}
