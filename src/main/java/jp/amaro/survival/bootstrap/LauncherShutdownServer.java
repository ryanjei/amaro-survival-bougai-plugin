package jp.amaro.survival.bootstrap;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.Base64;
import java.util.concurrent.*;

public final class LauncherShutdownServer implements AutoCloseable {
    public static final int PORT = 8766;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Path handoff;
    private final Runnable shutdown;
    private byte[] expected;

    public LauncherShutdownServer(Path handoff, Runnable shutdown) throws IOException {
        this.handoff = handoff; this.shutdown = shutdown;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 0);
        server.createContext("/launcher/shutdown", this::handle);
        server.setExecutor(executor);
    }
    public void start() throws IOException {
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        expected = token.getBytes(StandardCharsets.US_ASCII);
        Files.createDirectories(handoff.getParent());
        Path temporary = handoff.resolveSibling(handoff.getFileName() + ".tmp");
        Files.writeString(temporary, token, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(temporary, handoff, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, handoff, StandardCopyOption.REPLACE_EXISTING); }
        server.start();
    }
    private synchronized void handle(HttpExchange exchange) throws IOException {
        int status = 401;
        if (exchange.getRemoteAddress().getAddress().isLoopbackAddress() && exchange.getRequestMethod().equals("POST") && exchange.getRequestURI().getPath().equals("/launcher/shutdown")) {
            String candidate = exchange.getRequestHeaders().getFirst("X-ASBP-Shutdown-Token");
            byte[] supplied = candidate == null ? new byte[0] : candidate.getBytes(StandardCharsets.US_ASCII);
            if (expected != null && MessageDigest.isEqual(expected, supplied)) { expected = null; Files.deleteIfExists(handoff); status = 202; }
        }
        byte[] body = (status == 202 ? "accepted" : "rejected").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, body.length); try (OutputStream out = exchange.getResponseBody()) { out.write(body); }
        if (status == 202) shutdown.run();
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); try { Files.deleteIfExists(handoff); } catch (IOException ignored) {} }
}
