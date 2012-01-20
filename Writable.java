
package me.exphc.Writable;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.lang.Byte;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
            if (Writable.getWritingState(player) != WritingState.NOT_WRITING) {
                player.sendMessage("You are already writing");
                // TODO: stop other writing, restore (like timeout), cancel task? 
                return;
            }

            // TODO: prevent writing on >1 stacks? or try to shuffle around?

            // TODO: check block to ensure is realistically hard surface to write on (stone, not gravel or sand, etc.)

            // TODO: check if have writing implement (ink sac), if so use up

            // If blank, assign new ID
            short id = item.getDurability();
            if (id == 0) {
                id = Writable.nextPaperID(); // TODO: choose next!
                item.setDurability(id);
            }
            log.info("This is book #"+id);


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
    // Returns items restored
    static public ItemStack restoreSavedItem(Player player) {
        ItemStack items = savedItemStack.get(player);
        int slot = savedItemSlot.get(player);

        player.getInventory().setItem(slot, items);

        savedItemStack.remove(player);
        savedItemSlot.remove(player);

        return items;
    }

    static public void readPaperToPlayer(Player player, int id) {
        ArrayList<String> lines = Writable.readPaper(id);

        player.sendMessage(lines + "");
        // TODO: page through, view
    }


    // When change to in inventory slot, read back
    public void onItemHeldChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItem(event.getNewSlot());

        if (item != null && item.getType() == Material.PAPER) {
            int id = item.getDurability();

            // TODO: only if not zero
            readPaperToPlayer(player, id);
        }
    }

    // If pickup a paper with writing on it, let know
    public void onPlayerPickupItem(PlayerPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();

        if (item != null && item.getType() == Material.PAPER) {
            if (item.getDurability() != 0) {
                event.getPlayer().sendMessage("You picked up paper, mysteriously scribbled");
            } 
        }
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

        // Destroy sign
        block.setType(Material.AIR);

        // Restore previous item 
        ItemStack paperItem = WritablePlayerListener.restoreSavedItem(player);

        // Write
        int id = paperItem.getDurability();
        Writable.writePaper(id, lines);

        // Finish up
        Writable.setWritingState(player, WritingState.NOT_WRITING);
        WritablePlayerListener.readPaperToPlayer(player, id);
    }
}


public class Writable extends JavaPlugin {
    static Logger log = Logger.getLogger("Minecraft");
    WritablePlayerListener playerListener;
    WritableBlockListener blockListener;

    static private ConcurrentHashMap<Player, WritingState> writingState;
    static private ConcurrentHashMap<Integer, ArrayList<String>> paperTexts;    // TODO: paper class?

    public void onEnable() {
        writingState = new ConcurrentHashMap<Player, WritingState>();

        loadConfig();

        // TODO: load from disk
        paperTexts = new ConcurrentHashMap<Integer, ArrayList<String>>();

        playerListener = new WritablePlayerListener(this);
        blockListener = new WritableBlockListener(this);

        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_INTERACT, playerListener, org.bukkit.event.Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_ITEM_HELD, playerListener, org.bukkit.event.Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.PLAYER_PICKUP_ITEM, playerListener, org.bukkit.event.Event.Priority.Normal, this);

        Bukkit.getPluginManager().registerEvent(Event.Type.SIGN_CHANGE, blockListener, org.bukkit.event.Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(Event.Type.BLOCK_PLACE, blockListener, org.bukkit.event.Event.Priority.Normal, this);

        configurePaperStacking();

        log.info("Writable enabled");
    }

    static List<Material> writingImplementMaterials;
    static List<Material> writingSurfaceMaterials;

    private void loadConfig() {
        List<String> implementsStrings = getConfig().getStringList("writingImplements");

        writingImplementMaterials = new ArrayList<Material>(); 

        for (String implementString: implementsStrings) {
            Material implementMaterial = Material.matchMaterial(implementString);

            if (implementMaterial == null) {
                log.info("Invalid implement material: " + implementString);
                // TODO: error
                continue;
            }

            writingImplementMaterials.add(implementMaterial);
        }

        List<String> surfacesStrings = getConfig().getStringList("writingSurfaces");

        writingSurfaceMaterials = new ArrayList<Material>();

        for (String surfaceString: surfacesStrings) {
            Material surfaceMaterial = Material.matchMaterial(surfaceString);

            if (surfaceMaterial == null) {
                log.info("Invalid surface material: " + surfaceString);
                // TODO: error;
                continue;
            }

            writingSurfaceMaterials.add(surfaceMaterial);
        }

        log.info("writingImplementMaterials="+writingImplementMaterials);
        log.info("writingSurfaceMaterials="+writingSurfaceMaterials);

        //TODOList<Map<String,Object>> inksStrings = getConfig().getMapList("inks");

    }


    // Try to make paper stack by damage ID, or otherwise stack by one
    // Based on http://code.google.com/p/nisovin-minecraft-bukkit-plugins/source/browse/trunk/BookWorm/src/com/nisovin/bookworm/BookWorm.java
    // http://dev.bukkit.org/server-mods/bookworm/
    private void configurePaperStacking() {
        try {
        boolean ok = false;
            // attempt to make papers with different data values stack separately
            try {
                // obfuscated method name, check BookWorm for updates
                String methodName = getConfig().getString("stack-by-data-fn", "a");//"bQ");
                Method method = net.minecraft.server.Item.class.getDeclaredMethod(methodName, boolean.class);
                if (method.getReturnType() == net.minecraft.server.Item.class) {
                    method.setAccessible(true);
                    method.invoke(net.minecraft.server.Item.PAPER, true);
                    ok = true;
                }
            } catch (Exception e) {
                log.info("Not stacking papers together");
            }
            if (!ok) {
                // otherwise limit stack size to 1
                Field field = net.minecraft.server.Item.class.getDeclaredField("maxStackSize");
                field.setAccessible(true);
                field.setInt(net.minecraft.server.Item.PAPER, 1);
            } else {
                log.info("Successfully changed paper stacking");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void onDisable() {
        // TODO: save paperTexts to disk

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

    // Manipulate paper text
    static public void writePaper(int id, String[] newLines) {
        ArrayList<String> lines = readPaper(id);

        // Add non-empty lines
        for (int i = 0; i < newLines.length; i += 1) {
            String line = newLines[i];

            if (line == null || line.equals("")) {
                // TODO: don't skip unless is last lines!
                // sometimes want to retain spacing, add whitespace..
                // foo\n\n\n -> foo
                // foo\nbar -> foo\nbar
                // \n\nfoo -> \n\nfoo
                continue;
            }

            lines.add(line);
        }

        paperTexts.put(new Integer(id), lines);

        // TODO: save to disk now?
    }

    static public ArrayList<String> readPaper(int id) {
        ArrayList<String> lines = paperTexts.get(id);

        if (lines == null) {
            return new ArrayList<String>();   // empty array
        } else {
            return lines;
        }
    }

    static short nextPaperID() {
        // TODO: get next!
        return 1;
    }
}
