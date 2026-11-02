package dev.example.bedrockplace;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BedrockPlace extends JavaPlugin implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.DOWN, BlockFace.UP, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private double reach;
    private int cooldownTicks;
    private final Map<UUID, Integer> lastPlace = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reach = getConfig().getDouble("reach", 4.5);
        cooldownTicks = getConfig().getInt("cooldown-ticks", 3);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR) return;
        if (event.useItemInHand() == Event.Result.DENY) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("bedrockplace.use")) return;
        if (player.getGameMode() == GameMode.SPECTATOR) return;

        EquipmentSlot hand = event.getHand();
        if (hand == null) return;
        ItemStack item = player.getInventory().getItem(hand);
        if (item == null || item.getType().isAir() || !item.getType().isBlock()) return;

        int now = getServer().getCurrentTick();
        Integer last = lastPlace.get(player.getUniqueId());
        if (last != null && now - last < cooldownTicks) return;

        Block target = findPlacementBlock(player);
        if (target == null) return;

        Block against = findSupport(target);
        if (against == null) return;

        BoundingBox box = BoundingBox.of(target);
        for (Entity e : target.getWorld().getNearbyEntities(box)) {
            if (e instanceof Item || e.isDead()) continue;
            if (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
            return;
        }

        BlockData data = item.getType().createBlockData();
        BlockState replaced = target.getState();
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                target, replaced, against, item, player, true, hand);

        target.setBlockData(data, false);
        getServer().getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            replaced.update(true, false);
            return;
        }

        lastPlace.put(player.getUniqueId(), now);
        event.setCancelled(true);
        player.swingHand(hand);
        target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5),
                data.getSoundGroup().getPlaceSound(), 1f, 0.8f);

        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItem(hand, item.getAmount() <= 0 ? null : item);
        }
    }

    private Block findPlacementBlock(Player player) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Block playerFeet = player.getLocation().getBlock();
        Block playerHead = eye.getBlock();

        for (double d = 0.5; d <= reach; d += 0.1) {
            Block b = eye.clone().add(dir.clone().multiply(d)).getBlock();
            if (!b.isReplaceable()) return null;
            if (b.equals(playerFeet) || b.equals(playerHead)) continue;
            if (findSupport(b) != null) return b;
        }
        return null;
    }

    private Block findSupport(Block block) {
        for (BlockFace face : FACES) {
            Block n = block.getRelative(face);
            if (!n.isReplaceable() && !n.getType().isAir() && n.getType() != Material.BARRIER) {
                return n;
            }
        }
        return null;
    }

    @Override
    public void onDisable() {
        lastPlace.clear();
    }
            }
