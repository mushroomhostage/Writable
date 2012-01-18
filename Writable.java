
package me.exphc.Writable;

import java.lang.Byte;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.player.*;
import org.bukkit.Material.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.command.*;
import org.bukkit.inventory.*;
import org.bukkit.configuration.*;
import org.bukkit.configuration.file.*;
import org.bukkit.*;

// Location of a block, integral, comparable and hashable (unlike Bukkit's Location)
// TODO: merge with other plugins
class BlockLocation implements Comparable {
    World world;
    int x, y, z;

    public BlockLocation(Location loc) {
        world = loc.getWorld();
        x = loc.getBlockX();
        y = loc.getBlockY();
        z = loc.getBlockZ();
    }

    public BlockLocation(World w, int x0, int y0, int z0) {
        world = w;
        x = x0;
        y = y0;
        z = z0;
    }

    public Location getLocation() {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    public String toString() {
        return x + "," + y + "," + z;
    }

    public int compareTo(Object obj) {
        if (!(obj instanceof BlockLocation)) {
            return -1;
        }
        BlockLocation rhs = (BlockLocation)obj;

        // TODO: also compare world
        if (x - rhs.x != 0) {
            return x - rhs.x;
        } else if (y - rhs.y != 0) {
            return y - rhs.y;
        } else if (z - rhs.z != 0) {
            return z - rhs.z;
        }

        return 0;
    }

    public boolean equals(Object obj) {
        return compareTo(obj) == 0;      // why do I have to do this myself?
    }

    public int hashCode() {
        // lame hashing TODO: improve?
        return x * y * z;
    }
}

class WritablePlayerListener extends PlayerListener {
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;

    public WritablePlayerListener(Plugin pl) {
        plugin = pl;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (item != null && item.getType() == Material.PAPER && action == Action.RIGHT_CLICK_BLOCK) {
            // TODO: check block to ensure is realistically hard surface to write on (stone, not gravel or sand, etc.)
            // TODO: check if have writing implement (ink sac), if so use up

            // Quickly change to sign, so double right-click paper = place sign to write on
            player.setItemInHand(new ItemStack(Material.SIGN, 1));
            // TODO: if have >1, save off old paper?

            // XXX: This is wrong, we need to save where the sign is placed, not where they clicked the paper
            // But, we do need to link the two together
            /*
            BlockLocation blockLoc = new BlockLocation(block.getLocation());
            log.info("Writing at "+blockLoc);
            Writable.writingAt.put(blockLoc, 42);
            log.info("BLOCK1="+block);
            */
        }
    }
}

class WritableBlockListener extends BlockListener {
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;

    public WritableBlockListener(Plugin pl) {
        plugin = pl;
    }

    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        String[] lines = event.getLines();

        log.info("BLOCK2="+block);

        // Find out if this same came from writing on paper
        BlockLocation blockLoc = new BlockLocation(block.getLocation());
        Integer paperInteger = Writable.writingAt.get(blockLoc);
        if (paperInteger == null) {
            log.info("Just placing a sign, at "+blockLoc+", not in "+Writable.writingAt);
            return;
        }
        // TODO: check nearby

        int paperInt = paperInteger.intValue();
        log.info("Writing on paper #"+paperInt);

        // TODO: append lines to paper
    }

    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST) {
            BlockLocation blockLoc = new BlockLocation(block.getLocation());

            // TODO: determine if came from paper
            log.info("BLOCK place"+block);
            Writable.writingAt.put(blockLoc, new Integer(42));
        }
    }
}


public class Writable extends JavaPlugin {
    Logger log = Logger.getLogger("Minecraft");
    WritablePlayerListener playerListener;
    WritableBlockListener blockListener;

    static public ConcurrentHashMap<BlockLocation, Integer> writingAt;

    public void onEnable() {
        writingAt = new ConcurrentHashMap<BlockLocation, Integer>();

        playerListener = new WritablePlayerListener(this);
        blockListener = new WritableBlockListener(this);

        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.PLAYER_INTERACT, playerListener, org.bukkit.event.Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.SIGN_CHANGE, blockListener, org.bukkit.event.Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.BLOCK_PLACE, blockListener, org.bukkit.event.Event.Priority.Normal, this);

        log.info("Writable enabled");
    }

    public void onDisable() {
        log.info("Writable disabled");
    }
}
