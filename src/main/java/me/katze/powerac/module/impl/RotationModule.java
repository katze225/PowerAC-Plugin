package me.katze.powerac.module.impl;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import me.katze.powerac.module.Module;
import me.katze.powerac.module.ModuleInfo;
import me.katze.powerac.object.AIRotationData;
import me.katze.powerac.object.socket.AiCheckMessage;
import me.katze.powerac.object.socket.AiResultMessage.AiResultModelMessage;
import me.katze.powerac.object.socket.AiRotationSample;
import me.katze.powerac.object.socket.AuthInfo;
import me.katze.powerac.object.socket.TrainAccessCheckMessage;
import me.katze.powerac.object.socket.TrainBatchMessage;
import me.katze.powerac.object.socket.TrainClearMessage;
import me.katze.powerac.player.PowerPlayer;
import me.katze.powerac.utility.StringUtility;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@ModuleInfo("Rotation")
public final class RotationModule extends Module {

    private static final Gson GSON = new Gson();
    private static final int TRAIN_SAMPLES_REQUIRED = 300;

    private final Object lock = new Object();
    private List<AIRotationData> delayedBatch;
    private final List<AIRotationData> trainingSamples = new ArrayList<>();
    private int trainingLabel = -1;
    private Consumer<String> trainingNotifier;
    private boolean continuousTraining;
    private boolean inFlight;
    private boolean queued;
    private boolean sendScheduled;
    private boolean trainingInFlight;
    private boolean trainingActive;
    private int trainingSentBatches;

    public RotationModule(
        me.katze.powerac.PowerAC plugin,
        me.katze.powerac.manager.PlayerManager playerManager,
        PowerPlayer player
    ) {
        super(plugin, playerManager, player);
    }

    public void enqueueAiBatch(List<AIRotationData> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        if (collectTrainingSamples(batch)) {
            return;
        }
        boolean shouldSchedule = false;
        synchronized (lock) {
            if (inFlight || queued) {
                delayedBatch = batch;
                return;
            }
            delayedBatch = batch;
            queued = true;
            shouldSchedule = true;
        }
        if (shouldSchedule) {
            scheduleNextSend();
        }
    }

    public boolean startTrainingSession(CommandSender sender, int label, boolean continuous) {
        if (label != 0 && label != 1) {
            return false;
        }

        AuthInfo authInfo = getPlugin().getSocketClient().getSocketAuthInfo();
        String accountId = authInfo != null ? authInfo.getAccountId() : null;
        if (accountId == null || accountId.trim().isEmpty()) {
            sender.sendMessage(
                StringUtility.getString(
                    getPlugin()
                        .getConfigManager()
                        .getTrainMessage("resolve-account-id", "&cFailed to resolve account ID.")
                )
            );
            return true;
        }

        String requestId = UUID.randomUUID().toString();
        String accessPayload = GSON.toJson(
            new TrainAccessCheckMessage("train_access", getKey(), requestId)
        );
        getPlugin().getSocketClient().sendMessage(
            requestId,
            accessPayload,
            (status, detected, probability, limitReached, reason, checkUuid, results) ->
                runOnSender(sender, () -> {
                    if (!"ok".equalsIgnoreCase(status)) {
                        String details = reason == null || reason.trim().isEmpty()
                            ? status
                            : reason;
                        String lowered = details.toLowerCase(Locale.ROOT);
                        if (
                            lowered.contains("not allowed") ||
                            lowered.contains("disabled") ||
                            lowered.contains("account id is required")
                        ) {
                            sender.sendMessage(
                                StringUtility.getString(
                                    getPlugin()
                                        .getConfigManager()
                                        .getTrainMessage(
                                            "access-denied",
                                            "&cYou account do not have access to /powerac train."
                                        )
                                )
                            );
                        } else {
                            sender.sendMessage(
                                StringUtility.getString(
                                    getPlugin()
                                        .getConfigManager()
                                        .getTrainMessage(
                                            "access-check-failed",
                                            "&cTraining access check failed: {details}"
                                        )
                                        .replace("{details}", details)
                                )
                            );
                        }
                        return;
                    }

                    boolean started;
                    synchronized (lock) {
                        if (trainingActive || trainingInFlight) {
                            started = false;
                        } else {
                            if (getPlayer().getRotationTracker() != null) {
                                getPlayer().getRotationTracker().reset();
                            }
                            trainingSamples.clear();
                            delayedBatch = null;
                            queued = false;
                            trainingLabel = label;
                            trainingSentBatches = 0;
                            trainingNotifier = message ->
                                sender.sendMessage(StringUtility.getString(message));
                            continuousTraining = continuous;
                            trainingActive = true;
                            started = true;
                        }
                    }

                    if (!started) {
                        sender.sendMessage(
                            StringUtility.getString(
                                getPlugin()
                                    .getConfigManager()
                                    .getTrainMessage(
                                        "already-running",
                                        "&cA training session is already running."
                                    )
                            )
                        );
                        return;
                    }

                    sender.sendMessage(
                        StringUtility.getString(
                            getPlugin()
                                .getConfigManager()
                                .getTrainMessage(
                                    "collecting-started",
                                    "&aCollecting {samples} samples started{mode}. account_id: &f{account_id}"
                                )
                                .replace("{samples}", Integer.toString(TRAIN_SAMPLES_REQUIRED))
                                .replace("{mode}", continuous ? " in loop mode" : "")
                                .replace("{account_id}", accountId)
                        )
                    );
                })
        );
        return true;
    }

    public boolean stopTrainingSession() {
        synchronized (lock) {
            if (!trainingActive && !trainingInFlight) {
                return false;
            }
            trainingSamples.clear();
            delayedBatch = null;
            queued = false;
            trainingActive = false;
            trainingInFlight = false;
            continuousTraining = false;
            trainingLabel = -1;
            trainingSentBatches = 0;
            trainingNotifier = null;
            return true;
        }
    }

    public void clearTrainingData(CommandSender sender) {
        String requestId = UUID.randomUUID().toString();
        String payload = GSON.toJson(
            new TrainClearMessage("train_clear", getKey(), requestId)
        );
        getPlugin().getSocketClient().sendMessage(
            requestId,
            payload,
            (status, detected, probability, limitReached, reason, checkUuid, results) ->
                runOnSender(sender, () -> {
                    if ("ok".equalsIgnoreCase(status)) {
                        sender.sendMessage(
                            StringUtility.getString(
                                getPlugin()
                                    .getConfigManager()
                                    .getTrainMessage(
                                        "cleared",
                                        "&aYour trained model data has been cleared."
                                    )
                            )
                        );
                        return;
                    }
                    String details = reason == null || reason.trim().isEmpty()
                        ? status
                        : reason;
                    sender.sendMessage(
                        StringUtility.getString(
                            getPlugin()
                                .getConfigManager()
                                .getTrainMessage(
                                    "clear-failed",
                                    "&cFailed to clear trained model: {details}"
                                )
                                .replace("{details}", details)
                        )
                    );
                })
        );
    }

    public boolean isTrainingSessionRunning() {
        synchronized (lock) {
            return trainingActive || trainingInFlight;
        }
    }

    private void scheduleNextSend() {
        synchronized (lock) {
            if (sendScheduled) {
                return;
            }
            sendScheduled = true;
        }
        getPlugin().getTaskScheduler().runAsync(this::processQueue);
    }

    private void processQueue() {
        List<AIRotationData> toSend;
        synchronized (lock) {
            sendScheduled = false;
            if (!queued || delayedBatch == null) {
                return;
            }
            toSend = delayedBatch;
            delayedBatch = null;
            queued = false;
            inFlight = true;
        }
        sendToSocket(toSend);
    }

    private void sendToSocket(List<AIRotationData> batch) {
        String requestId = UUID.randomUUID().toString();
        String payload = buildAiPayload(requestId, batch);
        getPlugin().getSocketClient().sendMessage(
            requestId,
            payload,
            (status, detected, probability, limitReached, reason, checkUuid, results) -> {
                if (
                    "rejected".equalsIgnoreCase(status) ||
                    "error".equalsIgnoreCase(status)
                ) {
                    getPlugin().getLogger().warning(
                        "[PowerAC] Socket AI request for " +
                        (getPlayer().getName() == null ? "unknown" : getPlayer().getName()) +
                        " returned " +
                        status +
                        ": " +
                        reason
                    );
                }

                final List<AiResultModelMessage> finalResults = normalizeResults(
                    status,
                    detected,
                    limitReached,
                    probability,
                    reason,
                    checkUuid,
                    results
                );
                getPlugin().getTaskScheduler().runPlayer(
                    getPlayer().getUuid(),
                    () -> onAiResults(finalResults)
                );
                onRequestFinished();
            }
        );
    }

    private void onRequestFinished() {
        boolean shouldSchedule = false;
        synchronized (lock) {
            inFlight = false;
            if (delayedBatch != null && !queued) {
                queued = true;
                shouldSchedule = true;
            }
        }
        if (shouldSchedule) {
            scheduleNextSend();
        }
    }

    private String buildAiPayload(String requestId, List<AIRotationData> batch) {
        List<AiRotationSample> history = new ArrayList<>(batch.size());
        for (AIRotationData data : batch) {
            history.add(AiRotationSample.from(data));
        }
        return GSON.toJson(
            new AiCheckMessage(
                "ai_check",
                getKey(),
                requestId,
                getPlayer().getName() == null ? "" : getPlayer().getName(),
                getPlayer().getUuid().toString(),
                null,
                Boolean.valueOf(shouldUseGlobalModelForChecks()),
                Boolean.valueOf(shouldUsePrivateModelForChecks()),
                history
            )
        );
    }

    private boolean collectTrainingSamples(List<AIRotationData> batch) {
        List<AIRotationData> toSend = null;
        synchronized (lock) {
            if (!trainingActive || trainingInFlight) {
                return false;
            }
            trainingSamples.addAll(batch);
            if (trainingSamples.size() >= TRAIN_SAMPLES_REQUIRED) {
                toSend = new ArrayList<>(
                    trainingSamples.subList(0, TRAIN_SAMPLES_REQUIRED)
                );
                trainingSamples.clear();
                trainingInFlight = true;
                trainingActive = false;
            }
        }
        if (toSend != null) {
            sendTrainingBatch(toSend);
            return true;
        }
        return trainingActive;
    }

    private void sendTrainingBatch(List<AIRotationData> batch) {
        String requestId = UUID.randomUUID().toString();
        String payload = buildTrainPayload(requestId, batch);
        getPlugin().getSocketClient().sendMessage(
            requestId,
            payload,
            (status, detected, probability, limitReached, reason, checkUuid, results) -> {
                Consumer<String> notifier;
                boolean restart;
                synchronized (lock) {
                    notifier = trainingNotifier;
                    restart = continuousTraining;
                    trainingInFlight = false;
                    trainingSentBatches += "ok".equalsIgnoreCase(status) || "queued".equalsIgnoreCase(status) ? 1 : 0;
                    if (restart) {
                        trainingActive = true;
                        if (getPlayer().getRotationTracker() != null) {
                            getPlayer().getRotationTracker().reset();
                        }
                    } else {
                        trainingActive = false;
                        trainingLabel = -1;
                        trainingNotifier = null;
                        continuousTraining = false;
                    }
                }
                if (notifier == null) {
                    return;
                }
                runTrainingNotifier(notifier, restart, status, reason);
            }
        );
    }

    private String buildTrainPayload(String requestId, List<AIRotationData> batch) {
        List<AiRotationSample> history = new ArrayList<>(batch.size());
        for (AIRotationData data : batch) {
            history.add(AiRotationSample.from(data));
        }
        return GSON.toJson(
            new TrainBatchMessage(
                "train_batch",
                getKey(),
                requestId,
                getPlayer().getName() == null ? "" : getPlayer().getName(),
                getPlayer().getUuid().toString(),
                trainingLabel,
                history
            )
        );
    }

    private List<AiResultModelMessage> normalizeResults(
        String status,
        boolean detected,
        boolean limitReached,
        double probability,
        String reason,
        String checkUuid,
        List<AiResultModelMessage> results
    ) {
        if (results != null && !results.isEmpty()) {
            return results;
        }
        AiResultModelMessage fallback = GSON.fromJson(
            GSON.toJson(
                new LegacyAiResult(
                    Boolean.TRUE,
                    status,
                    Boolean.valueOf(detected),
                    Boolean.valueOf(limitReached),
                    Double.valueOf(probability),
                    reason,
                    checkUuid
                )
            ),
            AiResultModelMessage.class
        );
        return Collections.singletonList(fallback);
    }

    private void onAiResults(List<AiResultModelMessage> results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        for (AiResultModelMessage result : results) {
            if (result == null) {
                continue;
            }
            if ("ok".equalsIgnoreCase(result.getStatusOrDefault())) {
                getPlayerManager()
                    .publishMonitorUpdate(
                        getPlayer().getUuid(),
                        getPlayer().getName(),
                        result.getProbabilityOrUnknown(),
                        result.isDetected(),
                        result.isGlobalModel()
                    );
            }
            if (!result.isDetected()) {
                continue;
            }
            double probability = result.getProbabilityOrUnknown();
            String probabilityText = probability < 0D
                ? "?"
                : String.format(Locale.US, "%.3f", probability);
            String source = result.isGlobalModel() ? "global" : "private";
            String reason = result.getReasonOrEmpty();
            String details = reason == null || reason.trim().isEmpty()
                ? "AI rotation detection (" + source + "), p: " + probabilityText
                : "AI rotation detection (" + source + "), p: " + probabilityText + ", " + reason;
            getPlayer().getViolationManager().flag(this, probability, details, result.isGlobalModel());
        }
    }

    private boolean shouldUsePrivateModelForChecks() {
        return getPlugin().getConfigManager().isPrivateModelEnabled();
    }

    private boolean shouldUseGlobalModelForChecks() {
        if (!shouldUsePrivateModelForChecks()) {
            return true;
        }
        return getPlugin().getConfigManager().isPrivateModelAlsoCheckGlobal();
    }

    private void runTrainingNotifier(
        Consumer<String> notifier,
        boolean restart,
        String status,
        String reason
    ) {
        Runnable task = () -> {
            if ("ok".equalsIgnoreCase(status) || "queued".equalsIgnoreCase(status)) {
                int sentCount;
                synchronized (lock) {
                    sentCount = trainingSentBatches;
                }
                notifier.accept(
                    restart
                        ? getPlugin()
                            .getConfigManager()
                            .getTrainMessage(
                                "sent-continuous",
                                "&aSent to training. Collecting next rotation batch... Total batches sent: &f{count}"
                            )
                            .replace("{count}", Integer.toString(sentCount))
                        : getPlugin()
                            .getConfigManager()
                            .getTrainMessage(
                                "sent",
                                "&aSent to training. Total batches sent: &f{count}"
                            )
                            .replace("{count}", Integer.toString(sentCount))
                );
                return;
            }

            String details = reason == null || reason.trim().isEmpty()
                ? status
                : reason;
            synchronized (lock) {
                trainingActive = false;
                trainingInFlight = false;
                trainingLabel = -1;
                trainingSentBatches = 0;
                trainingNotifier = null;
                continuousTraining = false;
            }
            notifier.accept(
                getPlugin()
                    .getConfigManager()
                    .getTrainMessage("error", "&cTraining error: {details}")
                    .replace("{details}", details)
            );
        };

        getPlugin().getTaskScheduler().runPlayer(getPlayer().getUuid(), task);
    }

    private void runOnSender(CommandSender sender, Runnable task) {
        if (sender instanceof Player) {
            getPlugin().getTaskScheduler().runPlayer((Player) sender, task);
            return;
        }
        getPlugin().getTaskScheduler().runGlobal(task);
    }

    private static final class LegacyAiResult {

        private final Boolean global;
        private final String status;
        private final Boolean detected;
        private final Boolean limit_reached;
        private final Double probability;
        private final String reason;
        private final String check_uuid;

        private LegacyAiResult(
            Boolean global,
            String status,
            Boolean detected,
            Boolean limitReached,
            Double probability,
            String reason,
            String checkUuid
        ) {
            this.global = global;
            this.status = status;
            this.detected = detected;
            this.limit_reached = limitReached;
            this.probability = probability;
            this.reason = reason;
            this.check_uuid = checkUuid;
        }
    }
}
