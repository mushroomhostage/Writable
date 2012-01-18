
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
import org.bukkit.scheduler.*;
import org.bukkit.*;

enum WritingState {
    NOT_WRITING,        // initial state, timed out, or finished writing
    CLICKED_PAPER,      // onPlayerInteract(), when right-click paper
    PLACED_SIGN,        // onBlockPlace(), when placed temporary sign
                        // onSignChange(), after wrote sign
}

class WritableSignPlaceTimeoutTask implements Runnable {
    static public ConcurrentHashMap<Player, Integer> taskIDs = new ConcurrentHashMap<Player, Integer>();

    static Logger log = Logger.getLogger("Minecraft");
    Player player;

    public WritableSignPlaceTimeoutTask(Player p) {
        player = p;
    }

    public void run() {
        if (Writable.getWritingState(player) != WritingState.PLACED_SIGN) {
            log.info("did not place sign in time");
            
            WritablePlayerListener.restoreSavedItem(player);

            Writable.setWritingState(player, WritingState.NOT_WRITING);
        }

        WritableSignPlaceTimeoutTask.taskIDs.remove(player);
    }
}

class WritablePlayerListener extends PlayerListener {
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;

    static private ConcurrentHashMap<Player, ItemStack> savedItemStack = new ConcurrentHashMap<Player, ItemStack>();
    static private ConcurrentHashMap<Player, Integer> savedItemSlot = new ConcurrentHashMap<Player, Integer>();

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

            // Save off old item in hand to restore
            savedItemStack.put(player, item);
            savedItemSlot.put(player, player.getInventory().getHeldItemSlot());

            // Quickly change to sign, so double right-click paper = place sign to write on
            player.setItemInHand(new ItemStack(Material.SIGN, 1));
            // TODO: if have >1, save off old paper?

            Writable.setWritingState(player, WritingState.CLICKED_PAPER);
            
            // Timeout to NOT_WRITING if our sign isn't used in a sufficient time
            WritableSignPlaceTimeoutTask task = new WritableSignPlaceTimeoutTask(player);
            int taskID = Bukkit.getScheduler().scheduleAsyncDelayedTask(plugin, task, plugin.getConfig().getLong("signTimeout", 2*20));

            // Save task to cancel if did in fact make it to PLACED_SIGN in time
            WritableSignPlaceTimeoutTask.taskIDs.put(player, taskID);
        }
    }

    // Restore previous item held by player, before started writing (do not use setItemInHand())
    static public void restoreSavedItem(Player player) {
        ItemStack items = savedItemStack.get(player);
        int slot = savedItemSlot.get(player);

        player.getInventory().setItem(slot, items);

        savedItemStack.remove(player);
        savedItemSlot.remove(player);
    }
}

class WritableBlockListener extends BlockListener {
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;

    public WritableBlockListener(Plugin pl) {
        plugin = pl;
    }

    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        if (block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST) {
            WritingState state = Writable.getWritingState(player);

            // Did they get this sign from right-clicking paper?
            if (state == WritingState.CLICKED_PAPER) {
                log.info("Place paper sign"+block);

                // We made it, stop timeout task (so won't revert to NOT_WRITING and take back sign)
                int taskID = WritableSignPlaceTimeoutTask.taskIDs.get(player);
                WritableSignPlaceTimeoutTask.taskIDs.remove(player);
                Bukkit.getScheduler().cancelTask(taskID);

                Writable.setWritingState(player, WritingState.PLACED_SIGN);
                // TODO: store paper ID
            } else {
                log.info("Place non-paper sign");
            }
        }
    }

    public void onSignChange(SignChangeEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();
        String[] lines = event.getLines();

        WritingState state = Writable.getWritingState(player);
        if (state != WritingState.PLACED_SIGN) {    
            log.info("Changing sign not from paper");
            return;
        }

        // This sign text came from a sign from clicking paper
        log.info("Changing paper sign");

        // TODO: get paper ID
        log.info("Writing on paper");

        // TODO: append lines to paper

        // Destroy sign
        block.setType(Material.AIR);

        
        // Restore previous item 
        WritablePlayerListener.restoreSavedItem(player);

        // Wrote sign, done
        Writable.setWritingState(player, WritingState.NOT_WRITING);
        // TODO: just delete from state?
    }
}


public class Writable extends JavaPlugin {
    static Logger log = Logger.getLogger("Minecraft");
    WritablePlayerListener playerListener;
    WritableBlockListener blockListener;

    static private ConcurrentHashMap<Player, WritingState> writingState;

    public void onEnable() {
        writingState = new ConcurrentHashMap<Player, WritingState>();

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

    // Manipulate state machine
    static public void setWritingState(Player player, WritingState newState) {
        WritingState oldState = getWritingState(player);

        log.info("State change "+player.getName()+": "+oldState+" -> "+newState);

        if (newState == WritingState.NOT_WRITING) {
            writingState.remove(player);
        } else {
            writingState.put(player, newState);
        }
    }

    static public WritingState getWritingState(Player player) {
        WritingState state = writingState.get(player);

        return state == null ? WritingState.NOT_WRITING : state;
    }
}
