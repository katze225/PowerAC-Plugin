package me.katze.powerac.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.enchantment.Enchantment;
import com.github.retrooper.packetevents.protocol.item.enchantment.type.EnchantmentTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import java.util.Collections;
import java.util.List;
import me.katze.powerac.PowerAC;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public final class EspPacket extends PacketListenerAbstract {

    private static final int HEALTH_METADATA_INDEX = 8;
    private static final float SPOOFED_HEALTH = 0.5F;
    private static final double SPOOFED_MAX_HEALTH = 1.0D;

    private final PowerAC plugin;

    public EspPacket(PowerAC plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        Player viewer = event.getPlayer();
        if (viewer == null) {
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.ENTITY_EQUIPMENT) {
            rewriteEquipment(event, viewer);
            return;
        }
        if (!plugin.getConfigManager().isEspHideHealth()) {
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            rewriteHealthMetadata(event, viewer);
            return;
        }
        if (event.getPacketType() == PacketType.Play.Server.UPDATE_ATTRIBUTES) {
            rewriteHealthAttributes(event, viewer);
        }
    }

    private void rewriteEquipment(PacketSendEvent event, Player viewer) {
        Player target = resolveTargetPlayer(viewer, new WrapperPlayServerEntityEquipment(event).getEntityId());
        if (target == null) {
            return;
        }

        WrapperPlayServerEntityEquipment wrapper = new WrapperPlayServerEntityEquipment(event);
        List<Equipment> equipment = wrapper.getEquipment();
        if (equipment == null || equipment.isEmpty()) {
            return;
        }

        boolean changed = false;
        for (Equipment piece : equipment) {
            if (piece == null) {
                continue;
            }

            ItemStack item = piece.getItem();
            if (item == null || item.isEmpty()) {
                continue;
            }

            if (plugin.getConfigManager().isEspHideCount() && item.getAmount() > 1) {
                item.setAmount(1);
                changed = true;
            }

            if (plugin.getConfigManager().isEspHideDurability() && item.getDamageValue() > 0) {
                item.setDamageValue(item.getMaxDamage());
                changed = true;
            }

            if (plugin.getConfigManager().isEspHideEnchants() && isArmorSlot(piece.getSlot())) {
                List<Enchantment> enchantments = item.getEnchantments();
                if (enchantments.isEmpty()) {
                    if (item.isEnchanted()) {
                        item.setEnchantments(Collections.emptyList());
                        changed = true;
                    }
                    continue;
                }

                item.setEnchantments(Collections.singletonList(
                    new Enchantment(EnchantmentTypes.UNBREAKING, 2)
                ));
                changed = true;
            }
        }

        if (changed) {
            wrapper.setEquipment(equipment);
        }
    }

    private void rewriteHealthMetadata(PacketSendEvent event, Player viewer) {
        WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
        if (isViewerEntity(viewer, wrapper.getEntityId())) {
            return;
        }

        Player target = resolveTargetPlayer(viewer, wrapper.getEntityId());
        if (target == null || target.isDead()) {
            return;
        }

        List<EntityData<?>> metadata = wrapper.getEntityMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        for (EntityData<?> entry : metadata) {
            EntityData data = entry;
            if (data.getIndex() != HEALTH_METADATA_INDEX || data.getType() != EntityDataTypes.FLOAT) {
                continue;
            }

            Object value = data.getValue();
            if (!(value instanceof Float) || ((Float) value) <= 0.0F) {
                return;
            }

            data.setValue(SPOOFED_HEALTH);
            wrapper.setEntityMetadata(metadata);
            return;
        }
    }

    private void rewriteHealthAttributes(PacketSendEvent event, Player viewer) {
        WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(event);
        if (isViewerEntity(viewer, wrapper.getEntityId())) {
            return;
        }

        Player target = resolveTargetPlayer(viewer, wrapper.getEntityId());
        if (target == null || target.isDead()) {
            return;
        }

        List<WrapperPlayServerUpdateAttributes.Property> properties = wrapper.getProperties();
        if (properties == null || properties.isEmpty()) {
            return;
        }

        for (WrapperPlayServerUpdateAttributes.Property property : properties) {
            if (property.getAttribute() != Attributes.MAX_HEALTH) {
                continue;
            }

            property.setValue(SPOOFED_MAX_HEALTH);
            property.setModifiers(Collections.emptyList());
            wrapper.setProperties(properties);
            return;
        }
    }

    private boolean isViewerEntity(Player viewer, int entityId) {
        return viewer.getEntityId() == entityId;
    }

    private Player resolveTargetPlayer(Player viewer, int entityId) {
        Entity entity = SpigotConversionUtil.getEntityById(viewer.getWorld(), entityId);
        if (!(entity instanceof Player)) {
            return null;
        }
        return (Player) entity;
    }

    private boolean isArmorSlot(EquipmentSlot slot) {
        return slot == EquipmentSlot.HELMET
            || slot == EquipmentSlot.CHEST_PLATE
            || slot == EquipmentSlot.LEGGINGS
            || slot == EquipmentSlot.BOOTS;
    }
}
