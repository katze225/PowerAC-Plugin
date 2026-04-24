package me.katze.powerac.tracker.impl;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import me.katze.powerac.object.AIRotationData;
import me.katze.powerac.player.PowerPlayer;
import me.katze.powerac.tracker.Tracker;
import me.katze.powerac.utility.MathUtility;

@Getter
public class RotationTracker extends Tracker {

    private static final int HISTORY_BATCH_SIZE = 300;
    private static final int MAX_DELTA_MS = 100;

    private final Consumer<List<AIRotationData>> batchConsumer;

    private final List<AIRotationData> history = new ArrayList<>();
    private AIRotationData lastSample;

    private float yaw;
    private float pitch;
    private float lastYaw;
    private float lastPitch;
    private boolean hasLastRotation;
    private long lastRotationTimeMs;

    public RotationTracker(
        PowerPlayer player,
        Consumer<List<AIRotationData>> batchConsumer
    ) {
        super(player);
        this.batchConsumer = batchConsumer;
    }

    @Override
    public synchronized void registerIncomingPostHandler(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.PLAYER_ROTATION
            && event.getPacketType() != PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION) {
            return;
        }

        WrapperPlayClientPlayerFlying flying = new WrapperPlayClientPlayerFlying(event);
        handleRotation(
            MathUtility.wrapYaw(flying.getLocation().getYaw()),
            flying.getLocation().getPitch(),
            System.currentTimeMillis()
        );
    }

    public synchronized void handleRotation(float newYaw, float newPitch, long now) {
        if (!hasLastRotation) {
            hasLastRotation = true;
            yaw = newYaw;
            pitch = newPitch;
            lastYaw = newYaw;
            lastPitch = newPitch;
            lastRotationTimeMs = now;
            return;
        }

        lastYaw = yaw;
        lastPitch = pitch;
        yaw = newYaw;
        pitch = newPitch;

        long deltaMs = Math.max(1L, now - lastRotationTimeMs);
        if (deltaMs > MAX_DELTA_MS) {
            deltaMs = MAX_DELTA_MS;
        }

        long timeSinceLastAttack = player
            .getActionTracker()
            .getTimeSinceLastAttack(now);
        if (timeSinceLastAttack <= 4000L) {
            AIRotationData currentSample = AIRotationData.from(
                lastSample,
                lastYaw,
                lastPitch,
                yaw,
                pitch,
                timeSinceLastAttack,
                deltaMs
            );

            history.add(currentSample);
            lastSample = currentSample;

            if (history.size() >= HISTORY_BATCH_SIZE) {
                List<AIRotationData> batch = new ArrayList<>(
                    history.subList(0, HISTORY_BATCH_SIZE)
                );
                history.subList(0, HISTORY_BATCH_SIZE).clear();
                lastRotationTimeMs = now;
                if (batchConsumer != null) {
                    batchConsumer.accept(batch);
                }
                return;
            }
        }

        lastRotationTimeMs = now;
    }

    public synchronized void reset() {
        history.clear();
        lastSample = null;
    }
}
