package jp.amaro.survival.command;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class TestAdminAuthorizer {
    private final Path file;
    private final String configuredName;
    private UUID configuredUuid;

    public TestAdminAuthorizer(Path file) {
        this.file = file;
        Properties values = new Properties();
        if (Files.isRegularFile(file)) try (Reader reader = Files.newBufferedReader(file)) { values.load(reader); } catch (IOException e) { throw new IllegalStateException("test admin設定を読み込めません", e); }
        configuredName = values.getProperty("player-name", "").trim();
        String uuid = values.getProperty("player-uuid", "").trim();
        if (!uuid.isEmpty()) try { configuredUuid = UUID.fromString(uuid); } catch (IllegalArgumentException e) { throw new IllegalStateException("test admin UUIDが不正です", e); }
    }

    public boolean isAuthorized(CommandSender sender) {
        if (sender instanceof ConsoleCommandSender || sender.hasPermission(AdminTestCommand.PERMISSION)) return true;
        if (!(sender instanceof Player player) || configuredName.isEmpty()) return false;
        if (configuredUuid != null) return configuredUuid.equals(player.getUniqueId());
        if (!configuredName.equalsIgnoreCase(player.getName())) return false;
        configuredUuid = player.getUniqueId();
        persist();
        return true;
    }

    private void persist() {
        try {
            Files.createDirectories(file.getParent());
            Properties values = new Properties(); values.setProperty("player-name", configuredName); values.setProperty("player-uuid", configuredUuid.toString());
            Path temporary = file.resolveSibling(file.getFileName() + ".new");
            try (Writer writer = Files.newBufferedWriter(temporary)) { values.store(writer, "ASBP local test administrator"); }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { throw new IllegalStateException("test admin UUIDを保存できません", e); }
    }
}
