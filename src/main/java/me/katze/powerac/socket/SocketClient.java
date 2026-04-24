package me.katze.powerac.socket;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.InetAddress;
import java.net.UnknownHostException;

import lombok.Getter;
import lombok.Setter;
import me.katze.powerac.PowerAC;
import me.katze.powerac.object.socket.AuthMessage;
import me.katze.powerac.object.socket.AuthInfo;
import me.katze.powerac.object.socket.AiResultMessage.AiResultModelMessage;
import me.katze.powerac.object.socket.PingMessage;
import me.katze.powerac.object.socket.ServerStatusMessage;
import me.katze.powerac.object.socket.SocketEnvelope;
import me.katze.powerac.object.socket.SocketPlayerInfo;
import me.katze.powerac.socket.handler.ISocketHandler;
import me.katze.powerac.socket.handler.impl.AiResultHandler;
import me.katze.powerac.socket.handler.impl.AuthErrorHandler;
import me.katze.powerac.socket.handler.impl.AuthOkHandler;
import me.katze.powerac.socket.handler.impl.PongHandler;
import me.katze.powerac.socket.handler.impl.ServerStatusOkHandler;
import me.katze.powerac.utility.VersionUtility;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class SocketClient {

    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 1;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;
    private static final long CONNECT_TIMEOUT_SECONDS = 60L;
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30L;
    private static final long HEARTBEAT_TIMEOUT_SECONDS = 90L;
    private static final long REQUEST_TIMEOUT_SECONDS = 45L;
    private static final Gson GSON = new Gson();

    private final PowerAC plugin;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentLinkedQueue<PendingMessage> queue =
        new ConcurrentLinkedQueue<>();
    private final Map<String, PendingMessage> pendingResponses =
        new ConcurrentHashMap<>();
    private final Map<String, ISocketHandler> handlers = new HashMap<>();

    private final AtomicBoolean manualDisconnect = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean authenticated = new AtomicBoolean(false);
    private final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private final AtomicBoolean serverStateConfirmed = new AtomicBoolean(false);

    private volatile WebSocket webSocket;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile ScheduledFuture<?> heartbeatFuture;
    @Getter
    private volatile AuthInfo socketAuthInfo;
    private volatile long lastPongAtMs = 0L;
    private volatile int consecutiveFailures = 0;
    @Setter
    @Getter
    private volatile int playerLimit = 15;

    public SocketClient(PowerAC plugin) {
        this.plugin = plugin;
        this.httpClient =
            new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(0L, TimeUnit.MILLISECONDS)
                .writeTimeout(0L, TimeUnit.MILLISECONDS)
                .pingInterval(30L, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .build();
        registerHandlers();
    }

    public void connect() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        manualDisconnect.set(false);
        authenticated.set(false);
        serverStateConfirmed.set(false);
        socketAuthInfo = null;
        if (!hasConfiguredApiKey()) {
            handleMissingApiKey();
            return;
        }
        openSocket();
    }

    public void reconnect() {
        manualDisconnect.set(false);
        running.set(true);
        reconnecting.set(false);
        serverStateConfirmed.set(false);
        socketAuthInfo = null;
        consecutiveFailures = 0;
        cancelReconnect();
        closeCurrentSocket(1000, "manual reconnect");
        failAllPending("reconnecting");
        queue.clear();
        if (!hasConfiguredApiKey()) {
            handleMissingApiKey();
            return;
        }
        openSocket();
    }

    public void disconnect() {
        manualDisconnect.set(true);
        authenticated.set(false);
        serverStateConfirmed.set(false);
        socketAuthInfo = null;
        running.set(false);
        reconnecting.set(false);
        consecutiveFailures = 0;
        cancelReconnect();
        cancelHeartbeat();
        failAllPending("socket disconnected");
        queue.clear();
        closeCurrentSocket(1000, "plugin disconnect");
    }

    public void shutdown() {
        disconnect();
        scheduler.shutdownNow();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }

    public boolean isAuthenticated() {
        return authenticated.get();
    }

    public boolean isServerStateConfirmed() {
        return serverStateConfirmed.get();
    }

    public boolean canSendAiCheck(UUID playerUuid) {
        return authenticated.get() &&
        serverStateConfirmed.get() &&
        playerUuid != null &&
        plugin.getPlayerManager() != null &&
        plugin.getPlayerManager().isTrackedOnline(playerUuid);
    }

    public String getPlan() {
        AuthInfo authInfo = socketAuthInfo;
        return authInfo != null ? authInfo.getPlan() : "inactive";
    }

    public void syncServerState() {
        schedule(() -> {
            if (!running.get() || !authenticated.get() || webSocket == null) {
                return;
            }
            WebSocket current = webSocket;
            if (current == null) {
                return;
            }
            if (!current.send(buildServerStatusPayload())) {
                scheduleReconnect();
            }
        });
    }

    public void sendMessage(
        String requestId,
        String payload,
        ResponseHandler handler
    ) {
        if (requestId == null || requestId.trim().isEmpty() || payload == null) {
            return;
        }
        PendingMessage pending = new PendingMessage(requestId, payload, handler);
        if (handler != null) {
            pending.timeoutFuture =
                scheduler.schedule(
                    () -> completePending(
                        requestId,
                        "error",
                        false,
                        false,
                        -1D,
                        "socket request timeout",
                        "",
                        java.util.Collections.<AiResultModelMessage>emptyList()
                    ),
                    REQUEST_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                );
            pendingResponses.put(requestId, pending);
        }
        queue.add(pending);
        flushQueue();
    }

    private void openSocket() {
        if (!running.get()) {
            return;
        }
        if (!hasConfiguredApiKey()) {
            handleMissingApiKey();
            return;
        }
        try {
            Request request = new Request.Builder().url(resolveSocketUrl()).build();
            webSocket = httpClient.newWebSocket(request, new Listener());
        } catch (Exception exception) {
            handleConnectionFailure(exception, null);
        }
    }

    private String resolveSocketUrl() {
        return PowerAC.getInstance().getPrimaryUrl();
    }

    private void flushQueue() {
        schedule(() -> {
            WebSocket current = webSocket;
            if (!authenticated.get() || current == null) {
                return;
            }
            PendingMessage pending;
            while ((pending = queue.poll()) != null) {
                if (!current.send(pending.payload)) {
                    queue.add(pending);
                    scheduleReconnect();
                    return;
                }
            }
        });
    }

    private void sendAuth(WebSocket socket) {
        if (!hasConfiguredApiKey()) {
            handleMissingApiKey();
            return;
        }

        socket.send(
            GSON.toJson(
                new AuthMessage(
                    "auth",
                    plugin.getApiKey() == null ? "" : plugin.getApiKey(),
                    plugin.getPluginVersion(),
                    plugin.getServer().getBukkitVersion()
                )
            )
        );
    }

    private String buildServerStatusPayload() {
        String serverUuid = null;
        AuthInfo authInfo = socketAuthInfo;
        if (
            authInfo != null &&
            authInfo.getServerUuid() != null &&
            !authInfo.getServerUuid().trim().isEmpty()
        ) {
            serverUuid = authInfo.getServerUuid();
        }
        List<SocketPlayerInfo> players = plugin.getPlayerManager() == null
            ? new ArrayList<SocketPlayerInfo>()
            : plugin.getPlayerManager().createSocketPlayerSnapshot();
        return GSON.toJson(
            new ServerStatusMessage(
                "server_status",
                serverUuid,
                plugin.getDescription().getVersion(),
                plugin.getServer().getBukkitVersion(),
                players.size(),
                players
            )
        );
    }

    public void handleAuthOk(AuthInfo authInfo) {
        consecutiveFailures = 0;
        reconnecting.set(false);
        authenticated.set(true);
        serverStateConfirmed.set(false);
        socketAuthInfo = authInfo;
        lastPongAtMs = System.currentTimeMillis();
        if (authInfo.getPlayerLimit() != null) {
            this.setPlayerLimit(authInfo.getPlayerLimit());
        }

        if (authInfo.getLatestVersion() != null && !authInfo.getLatestVersion().trim().isEmpty()) {
            if (!VersionUtility.matchesRelease(authInfo.getLatestVersion(), plugin.getPluginVersion())) {
                plugin.getLogger().warning("==================================================");
                plugin.getLogger().warning("A new version of PowerAC is available (" + authInfo.getLatestVersion() + ")!");
                plugin.getLogger().warning("Please download the update at: https://powerac.net/dashboard");
                plugin.getLogger().warning("==================================================");
            }
        }

        plugin.getLogger().info("Socket connected and authenticated.");
        startHeartbeat();
        syncServerState();
        flushQueue();
    }

    public void handleAuthError(String message) {
        authenticated.set(false);
        serverStateConfirmed.set(false);
        socketAuthInfo = null;
        plugin.getLogger().severe("Socket authentication failed: " + message);
    }

    public void handleServerStatusOk(int playerLimit, int requestsPerHour) {
        this.setPlayerLimit(playerLimit);
        serverStateConfirmed.set(true);
    }

    public void handlePong() {
        lastPongAtMs = System.currentTimeMillis();
    }

    public void handleAiResult(
        String requestId,
        String status,
        boolean detected,
        boolean limitReached,
        double probability,
        String reason,
        String checkUuid,
        List<AiResultModelMessage> results
    ) {
        completePending(
            requestId,
            status,
            detected,
            limitReached,
            probability,
            reason,
            checkUuid,
            results
        );
    }

    private void completePending(
        String requestId,
        String status,
        boolean detected,
        boolean limitReached,
        double probability,
        String reason,
        String checkUuid,
        List<AiResultModelMessage> results
    ) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return;
        }
        PendingMessage pending = pendingResponses.remove(requestId);
        if (pending == null) {
            return;
        }
        if (pending.timeoutFuture != null) {
            pending.timeoutFuture.cancel(false);
        }
        if (pending.handler != null) {
            pending.handler.handle(
                status,
                detected,
                probability,
                limitReached,
                reason,
                checkUuid,
                results
            );
        }
    }

    private void failAllPending(String reason) {
        for (PendingMessage pending : pendingResponses.values()) {
            if (pending.timeoutFuture != null) {
                pending.timeoutFuture.cancel(false);
            }
            if (pending.handler != null) {
                pending.handler.handle(
                    "error",
                    false,
                    -1D,
                    false,
                    reason,
                    "",
                    java.util.Collections.<AiResultModelMessage>emptyList()
                );
            }
        }
        pendingResponses.clear();
    }

    private void scheduleReconnect() {
        if (
            manualDisconnect.get() ||
            !running.get() ||
            !reconnecting.compareAndSet(false, true)
        ) {
            return;
        }
        authenticated.set(false);
        serverStateConfirmed.set(false);
        failAllPending("reconnecting");
        queue.clear();
        cancelHeartbeat();
        closeCurrentSocket(1001, "reconnecting");
        consecutiveFailures += 1;
        long reconnectDelaySeconds = getReconnectDelaySeconds();
        plugin
            .getLogger()
            .warning(
                "Socket reconnect scheduled in " +
                reconnectDelaySeconds +
                "s (failures=" +
                consecutiveFailures +
                ")"
            );
        reconnectFuture =
            scheduler.schedule(() -> {
                reconnecting.set(false);
                if (manualDisconnect.get() || !running.get()) {
                    return;
                }
                openSocket();
            }, reconnectDelaySeconds, TimeUnit.SECONDS);
    }

    private long getReconnectDelaySeconds() {
        int failures = Math.max(0, consecutiveFailures - 1);
        long delay = (long) INITIAL_RECONNECT_DELAY_SECONDS << Math.min(failures, 6);
        return Math.min(delay, MAX_RECONNECT_DELAY_SECONDS);
    }

    private void startHeartbeat() {
        cancelHeartbeat();
        heartbeatFuture =
            scheduler.scheduleAtFixedRate(() -> {
                if (!running.get() || !authenticated.get()) {
                    return;
                }
                long now = System.currentTimeMillis();
                if (
                    lastPongAtMs > 0L &&
                    now - lastPongAtMs >
                    TimeUnit.SECONDS.toMillis(HEARTBEAT_TIMEOUT_SECONDS)
                ) {
                    scheduleReconnect();
                    return;
                }
                WebSocket current = webSocket;
                if (current == null) {
                    scheduleReconnect();
                    return;
                }
                if (!current.send(GSON.toJson(new PingMessage("ping", now)))) {
                    scheduleReconnect();
                    return;
                }
                syncServerState();
            }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelReconnect() {
        ScheduledFuture<?> current = reconnectFuture;
        reconnectFuture = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> current = heartbeatFuture;
        heartbeatFuture = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private void closeCurrentSocket(int code, String reason) {
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            current.close(code, reason);
            current.cancel();
        }
    }

    private boolean isCurrentSocket(WebSocket socket) {
        return socket != null && socket == webSocket;
    }

    private void schedule(Runnable runnable) {
        if (scheduler.isShutdown() || scheduler.isTerminated()) {
            return;
        }
        try {
            scheduler.execute(runnable);
        } catch (RejectedExecutionException ignored) {
        }
    }

    private void handleConnectionFailure(Throwable throwable, Response response) {
        logConnectionFailure(throwable, response);
        scheduleReconnect();
    }

    private void logConnectionFailure(Throwable throwable, Response response) {
        if (response != null) {
            plugin
                .getLogger()
                .warning(
                    "Socket endpoint returned HTTP " +
                    response.code() +
                    (response.message() == null || response.message().trim().isEmpty()
                        ? ""
                        : " (" + response.message() + ")")
                );
            return;
        }
        String message =
            throwable == null || throwable.getMessage() == null
                ? "socket failure"
                : throwable.getMessage();
        plugin
            .getLogger()
            .warning(
                "Socket failure: " +
                message +
                " (endpoint_ip=" + resolveEndpointIp() +
                ", account_id=" + getAccountIdOrUnknown() + ")"
            );
    }

    private String getAccountIdOrUnknown() {
        AuthInfo authInfo = socketAuthInfo;
        String accountId = authInfo != null ? authInfo.getAccountId() : null;
        if (accountId == null || accountId.trim().isEmpty()) {
            return "unknown";
        }
        return accountId.trim();
    }

    private String resolveEndpointIp() {
        try {
            String url = resolveSocketUrl();
            String host = url
                .replaceFirst("^wss?://", "")
                .replaceFirst("/.*$", "")
                .replaceFirst(":\\d+$", "");
            InetAddress address = InetAddress.getByName(host);
            return address.getHostAddress();
        } catch (UnknownHostException exception) {
            return "unknown";
        }
    }

    public interface ResponseHandler {
        void handle(
            String status,
            boolean detected,
            double probability,
            boolean limitReached,
            String reason,
            String checkUuid,
            List<AiResultModelMessage> results
        );
    }

    private static final class PendingMessage {

        private final String requestId;
        private final String payload;
        private final ResponseHandler handler;
        private ScheduledFuture<?> timeoutFuture;

        private PendingMessage(
            String requestId,
            String payload,
            ResponseHandler handler
        ) {
            this.requestId = requestId;
            this.payload = payload;
            this.handler = handler;
        }
    }

    private final class Listener extends WebSocketListener {

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (!isCurrentSocket(webSocket)) {
                return;
            }
            authenticated.set(false);
            serverStateConfirmed.set(false);
            socketAuthInfo = null;
            lastPongAtMs = System.currentTimeMillis();
            plugin
                .getLogger()
                .info(
                    "Socket opened: endpoint_ip=" +
                    resolveEndpointIp()
                );
            sendAuth(webSocket);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (!isCurrentSocket(webSocket)) {
                return;
            }
            try {
                JsonObject payload = JsonParser.parseString(text).getAsJsonObject();
                SocketEnvelope envelope = GSON.fromJson(payload, SocketEnvelope.class);
                String type = envelope == null
                    ? ""
                    : envelope.getTypeOrEmpty().toLowerCase(Locale.ROOT);
                ISocketHandler handler = handlers.get(type);
                if (handler != null) {
                    handler.handle(SocketClient.this, payload);
                }
            } catch (Exception exception) {
                plugin
                    .getLogger()
                    .warning(
                        "Failed to process socket message: " +
                        exception.getMessage()
                    );
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (!isCurrentSocket(webSocket)) {
                return;
            }
            authenticated.set(false);
            serverStateConfirmed.set(false);
            socketAuthInfo = null;
            cancelHeartbeat();
            if (!manualDisconnect.get()) {
                plugin
                    .getLogger()
                    .warning(
                        "Socket closed: code=" +
                        code +
                        ", reason=" +
                        (reason == null || reason.trim().isEmpty()
                            ? "unknown"
                            : reason) +
                        ", endpoint_ip=" +
                        resolveEndpointIp() +
                        ", account_id=" +
                        getAccountIdOrUnknown()
                    );
                failAllPending("socket closed");
                scheduleReconnect();
            }
        }

        @Override
        public void onFailure(
            WebSocket webSocket,
            Throwable throwable,
            Response response
        ) {
            if (!isCurrentSocket(webSocket)) {
                return;
            }
            authenticated.set(false);
            serverStateConfirmed.set(false);
            socketAuthInfo = null;
            cancelHeartbeat();
            if (!manualDisconnect.get()) {
                failAllPending(
                    throwable == null ? "socket failure" : throwable.getMessage()
                );
                handleConnectionFailure(throwable, response);
            }
        }
    }

    private boolean hasConfiguredApiKey() {
        String apiKey = plugin.getApiKey();
        if (apiKey == null) {
            return false;
        }
        String normalized = apiKey.trim();
        return !normalized.isEmpty() && !"YOUR_KEY_HERE".equalsIgnoreCase(normalized);
    }

    private void handleMissingApiKey() {
        authenticated.set(false);
        serverStateConfirmed.set(false);
        socketAuthInfo = null;
        running.set(false);
        reconnecting.set(false);
        cancelReconnect();
        cancelHeartbeat();
        closeCurrentSocket(1000, "api key is not configured");
        failAllPending("api key is not configured");
        plugin.getLogger().warning("==================================================");
        plugin.getLogger().warning("API key is not configured! Please replace 'YOUR_KEY_HERE'");
        plugin.getLogger().warning("with your real license key from the dashboard:");
        plugin.getLogger().warning("https://powerac.net/dashboard");
        plugin.getLogger().warning("After updating the config, run '/powerac reload'");
        plugin.getLogger().warning("==================================================");
    }

    private void registerHandlers() {
        addHandler(new AuthOkHandler());
        addHandler(new AuthErrorHandler());
        addHandler(new PongHandler());
        addHandler(new AiResultHandler());
        addHandler(new ServerStatusOkHandler());
    }

    private void addHandler(ISocketHandler handler) {
        handlers.put(handler.getType().toLowerCase(Locale.ROOT), handler);
    }
}
