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

    @Test void controlsYouTubeRuntimeAndReportsRuntimeSeparatelyFromAutoStart() {
        FakeOperations operations = new FakeOperations(); List<Component> messages = new ArrayList<>();
        AdminTestCommand handler = new AdminTestCommand(operations);

        handler.onCommand(sender(true, messages), command(), "asbp", new String[]{"youtube", "on"});
        handler.onCommand(sender(true, messages), command(), "asbp", new String[]{"youtube", "off"});
        handler.onCommand(sender(true, messages), command(), "asbp", new String[]{"youtube", "status"});

        assertEquals(1, operations.youtubeStarts);
        assertEquals(1, operations.youtubeStops);
        assertTrue(messages.stream().map(Component::toString).anyMatch(text -> text.contains("running")));
        assertTrue(messages.stream().map(Component::toString).anyMatch(text -> text.contains("disabled")));
    }

    @Test void controlsInterferenceRuntimeWithoutBlockingManualInterference() {
        FakeOperations operations = new FakeOperations(); List<Component> messages = new ArrayList<>();
        AdminTestCommand handler = new AdminTestCommand(operations);

        handler.onCommand(sender(true, messages), command(), "asbp", new String[]{"interference", "on"});
        handler.onCommand(sender(true, messages), command(), "asbp", new String[]{"interference", "off"});
        handler.onCommand(sender(true, messages), command(), "asbp", new String[]{"test", "interference", "darkness"});

        assertEquals(1, operations.interferenceStarts);
        assertEquals(1, operations.interferenceStops);
        assertEquals(InterferenceType.DARKNESS, operations.type);
    }

    @Test void completesYouTubeRuntimeCommandsWithoutBreakingFakeCommand() {
        AdminTestCommand handler = new AdminTestCommand(new FakeOperations());
        assertEquals(List.of("youtube"), handler.onTabComplete(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"you"}));
        assertEquals(List.of("on", "off", "status"), handler.onTabComplete(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"youtube", ""}));
        assertEquals(List.of("on", "off", "status"), handler.onTabComplete(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"interference", ""}));
        assertEquals(List.of("fake"), handler.onTabComplete(sender(true, new ArrayList<>()), command(), "asbp", new String[]{"test", "youtube", ""}));
    }

    @Test void statusDoesNotConfuseRuntimeWithAutoStartValues() {
        FakeOperations operations = new FakeOperations();
        operations.youtubeRunning = false; operations.youtubeAutoStart = true;
        operations.interferenceRunning = false; operations.interferenceAutoStart = true;
        List<Component> messages = new ArrayList<>();

        new AdminTestCommand(operations).onCommand(sender(true, messages), command(), "asbp", new String[]{"test", "status"});

        List<String> text = messages.stream().map(Component::toString).toList();
        assertTrue(text.stream().anyMatch(value -> value.contains("YouTube Runtime") && value.contains("stopped")));
        assertTrue(text.stream().anyMatch(value -> value.contains("YouTube Auto Start") && value.contains("enabled")));
        assertTrue(text.stream().anyMatch(value -> value.contains("Interference Runtime") && value.contains("disabled")));
        assertTrue(text.stream().anyMatch(value -> value.contains("Interference Auto Start") && value.contains("enabled")));
    }

    @Test void statusCarriesOwnedMobCountAndCapacity() {
        FakeOperations operations = new FakeOperations(); List<Component> messages = new ArrayList<>();
        new AdminTestCommand(operations).onCommand(sender(true, messages), command(), "asbp", new String[]{"test", "mobs", "count"});
        assertTrue(messages.stream().map(Component::toString).anyMatch(text -> text.contains("78") && text.contains("2")));
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
        int gaugeAdded; int youtubeStarts; int youtubeStops; int interferenceStarts; int interferenceStops;
        InterferenceType type; String author; String message;
        boolean youtubeRunning = true; boolean youtubeAutoStart;
        boolean interferenceRunning = true; boolean interferenceAutoStart;
        @Override public StatusSnapshot status() { return new StatusSnapshot(true, youtubeRunning, youtubeAutoStart,
                interferenceRunning, interferenceAutoStart, 4, 10, 40, false, 78, 2, 3); }
        @Override public YouTubeRuntimeResult startYouTube() { youtubeStarts++; return YouTubeRuntimeResult.STARTED; }
        @Override public YouTubeRuntimeResult stopYouTube() { youtubeStops++; return YouTubeRuntimeResult.STOPPED; }
        @Override public InterferenceRuntimeResult enableInterference() { interferenceStarts++; return InterferenceRuntimeResult.ENABLED; }
        @Override public InterferenceRuntimeResult disableInterference() { interferenceStops++; return InterferenceRuntimeResult.DISABLED; }
        @Override public void addGauge(int count) { gaugeAdded += count; }
        @Override public void applyInterference(InterferenceType type) { this.type = type; }
        @Override public void startRaid() {}
        @Override public void stopRaid() {}
        @Override public RaidSnapshot raidStatus() { return new RaidSnapshot(false, 0, 0); }
        @Override public int cleanupOwnedMobs() { return 0; }
        @Override public void fakeYouTubeComment(String author, String message) { this.author = author; this.message = message; }
    }
}
