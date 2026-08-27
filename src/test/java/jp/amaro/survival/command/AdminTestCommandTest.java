package jp.amaro.survival.command;

import jp.amaro.survival.domain.InterferenceType;
import net.kyori.adventure.text.Component;
import org.bukkit.command.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class AdminTestCommandTest {
    @Test void rejectsSenderWithoutAdminPermission() {
        FakeOperations operations = new FakeOperations(); List<Component> messages = new ArrayList<>();
        new AdminTestCommand(operations).onCommand(sender(false, messages), command(), "asbp", new String[]{"test", "gauge", "add", "1"});
        assertEquals(0, operations.gaugeAdded); assertFalse(messages.isEmpty());
    }

    @Test void configuredAdministratorCanUseCommandWithoutBukkitPermission() {
        FakeOperations operations = new FakeOperations(); CommandSender sender = sender(false, new ArrayList<>());
        new AdminTestCommand(operations, candidate -> candidate == sender).onCommand(sender, command(), "asbp", new String[]{"test", "gauge", "add", "1"});
        assertEquals(1, operations.gaugeAdded);
    }

    @Test void resolvesInterferenceTypeAndUsesProductionOperation() {
        FakeOperations operations = new FakeOperations();
        new AdminTestCommand(operations).onCommand(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"test", "interference", "darkness"});
        assertEquals(InterferenceType.DARKNESS, operations.type);
    }

    @Test void gaugeAddAcceptsValidAndRejectsInvalidCount() {
        FakeOperations operations = new FakeOperations(); AdminTestCommand handler = new AdminTestCommand(operations);
        handler.onCommand(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"test", "gauge", "add", "9"});
        assertEquals(9, operations.gaugeAdded);
        handler.onCommand(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"test", "gauge", "add", "0"});
        handler.onCommand(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"test", "gauge", "add", "101"});
        assertEquals(9, operations.gaugeAdded);
    }

    @Test void fakeCommentUsesSharedCommentOperation() {
        FakeOperations operations = new FakeOperations();
        new AdminTestCommand(operations).onCommand(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"test", "youtube", "fake", "testuser", "hello", "world"});
        assertEquals("testuser", operations.author); assertEquals("hello world", operations.message);
    }

    @Test void statusShowsOnlyOwnedMobCountInJapanese() {
        FakeOperations operations = new FakeOperations(); List<Component> messages = new ArrayList<>();
        new AdminTestCommand(operations).onCommand(sender(true, messages), command(), "asbp", new String[]{"test", "mobs", "count"});
        String rendered=messages.toString(); assertTrue(rendered.contains("ASBP所有Mob") && rendered.contains("78体")); assertFalse(rendered.contains("remaining") || rendered.contains("/ 80"));
    }

    private static CommandSender sender(boolean permitted, List<Component> messages) {
        return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(), new Class[]{CommandSender.class}, (proxy, method, args) -> {
            if (method.getName().equals("hasPermission")) return permitted;
            if (method.getName().equals("sendMessage") && args != null && args.length > 0 && args[0] instanceof Component component) { messages.add(component); return null; }
            if (method.getReturnType() == boolean.class) return false;
            return null;
        });
    }

    private static Command command() { return new Command("asbp") { @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; } }; }

    private static final class FakeOperations implements AdminOperations {
        int gaugeAdded; InterferenceType type; String author; String message;
        @Override public StatusSnapshot status() { return new StatusSnapshot(true, false, true, 4, 10, 40, false, 78, 3); }
        @Override public void addGauge(int count) { gaugeAdded += count; }
        @Override public void applyInterference(InterferenceType type) { this.type = type; }
        @Override public void startRaid() {}
        @Override public void stopRaid() {}
        @Override public RaidSnapshot raidStatus() { return new RaidSnapshot(false, 0, 0); }
        @Override public int cleanupOwnedMobs() { return 0; }
        @Override public void fakeYouTubeComment(String author, String message) { this.author = author; this.message = message; }
    }
}
