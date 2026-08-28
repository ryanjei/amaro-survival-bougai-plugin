package jp.amaro.survival.youtube;

import java.util.Optional;
import java.util.logging.Logger;

public final class YouTubeRuntime implements AutoCloseable {
    public enum StartResult { STARTED, ALREADY_RUNNING, FAILED }
    public enum StopResult { STOPPED, ALREADY_STOPPED }

    @FunctionalInterface
    public interface ConnectionFactory {
        Optional<Connection> create() throws Exception;
    }

    public interface Connection extends AutoCloseable {
        void start();
        @Override void close();
    }

    private final ConnectionFactory factory;
    private final Logger logger;
    private Connection connection;

    public YouTubeRuntime(ConnectionFactory factory, Logger logger) {
        this.factory = factory;
        this.logger = logger;
    }

    public synchronized StartResult start() {
        if (connection != null) return StartResult.ALREADY_RUNNING;
        Connection candidate = null;
        try {
            Optional<Connection> created = factory.create();
            if (created.isEmpty()) return StartResult.FAILED;
            candidate = created.get();
            candidate.start();
            connection = candidate;
            return StartResult.STARTED;
        } catch (Exception exception) {
            if (candidate != null) {
                try { candidate.close(); }
                catch (RuntimeException closeFailure) { exception.addSuppressed(closeFailure); }
            }
            logger.warning("YouTube連携の初期化に失敗しました。Minecraftプラグインは継続します: " + safeMessage(exception));
            return StartResult.FAILED;
        }
    }

    public synchronized StopResult stop() {
        if (connection == null) return StopResult.ALREADY_STOPPED;
        Connection current = connection;
        connection = null;
        try { current.close(); }
        catch (RuntimeException exception) {
            logger.warning("YouTube連携の停止処理でエラーが発生しました: " + safeMessage(exception));
        }
        return StopResult.STOPPED;
    }

    public synchronized boolean running() {
        return connection != null;
    }

    @Override public void close() {
        stop();
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
