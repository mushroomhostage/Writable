
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

class WritablePlayerListener extends PlayerListener {
    Logger log = Logger.getLogger("Minecraft");
    Plugin plugin;

    public WritablePlayerListener(Plugin pl) {
        plugin = pl;
    }

    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (item != null && item.getType() == Material.PAPER && action == Action.RIGHT_CLICK_BLOCK) {
            // TODO: check block to ensure is realistically hard surface to write on (stone, not gravel or sand, etc.)
            // TODO: check if have writing implement (ink sac), if so use up

            // Quickly change to sign, so double right-click paper = place sign to write on
            //player.setItemInHand(new ItemStack(Material.SIGN, 1, item.getDurability()));
            player.setItemInHand(new ItemStack(Material.SIGN, 1, (short)42, new Byte((byte)69)));

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

        // TODO: find out if this sign came from paper, if so, which one.. this doesn't tell us
        log.info("sign data = " + block.getData()); 
        // TODO: append lines to paper
    }
}


public class Writable extends JavaPlugin {
    Logger log = Logger.getLogger("Minecraft");
    WritablePlayerListener playerListener;
    WritableBlockListener blockListener;

    public void onEnable() {

        playerListener = new WritablePlayerListener(this);
        blockListener = new WritableBlockListener(this);

        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.PLAYER_INTERACT, playerListener, org.bukkit.event.Event.Priority.Normal, this);
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.Event.Type.SIGN_CHANGE, blockListener, org.bukkit.event.Event.Priority.Normal, this);

        log.info("Writable enabled");
    }

    public void onDisable() {
        log.info("Writable disabled");
    }
}
