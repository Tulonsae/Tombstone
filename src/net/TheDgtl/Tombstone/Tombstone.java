package net.TheDgtl.Tombstone;

import java.io.File;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.ProtectionTypes;
import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

public class Tombstone extends JavaPlugin {
	public static Logger log;
	private final eListener entityListener = new eListener();
	private PermissionHandler Permissions = null;
	private Plugin lwcPlugin = null;
	
	public Tombstone(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
    	super(pluginLoader, instance, desc, folder, plugin, cLoader);
    	log = Logger.getLogger("Minecraft");
	}
	
	public void onEnable() {
		PluginDescriptionFile pdfFile = getDescription();
        log.info(pdfFile.getName() + " v." + pdfFile.getVersion() + " is enabled.");
        
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvent(Event.Type.ENTITY_DEATH, entityListener, Priority.Normal, this);
        setupPermissions();
        checkLWC();
	}
	
	public void onDisable() {
		
	}
	
	private void checkLWC() {
		if (lwcPlugin != null) return;
		lwcPlugin = getServer().getPluginManager().getPlugin("LWC");
		if (lwcPlugin != null) {
			log.info("[Tombstone] LWC Found, enabling chest protection.");
		}
	}
	
	private Boolean activateLWC(Player player, Block block) {
		checkLWC();
		if (lwcPlugin == null) return false;
		LWC lwc = ((LWCPlugin)lwcPlugin).getLWC();
		// Register the chest as private
		lwc.getPhysicalDatabase().registerProtectedEntity(block.getTypeId(), ProtectionTypes.PRIVATE, player.getName(), "", block.getX(), block.getY(), block.getZ());
		return true;
	}
	
    private void setupPermissions() {
    	Plugin perm = getServer().getPluginManager().getPlugin("Permissions");

    	if(Permissions == null) {
    	    if(perm != null) {
    	    	Permissions = ((Permissions)perm).getHandler();
    	    } else {
    	    	log.info("[" + getDescription().getName() + "] Permission system not enabled. Enabling basic usage.");
    	    }
    	}
    }
    
    public Boolean hasPerm(Player player, String perm, Boolean def) {
    	if (Permissions != null)
    		return Permissions.has(player, perm);
    	return def;
    }
	
	private class eListener extends EntityListener {
        
        @Override
        public void onEntityDeath(EntityDeathEvent event ) {
        	if (!(event.getEntity() instanceof Player)) return;
        	Player p = (Player)event.getEntity();
        	if (!hasPerm(p, "tombstone.use", true)) return;
        	if (event.getDrops().size() == 0) {
        		p.sendMessage("[Tombstone] Inventory Empty.");
        		return;
        	}
        	
        	// Get the current player location.
        	Location loc = p.getLocation();
        	Block block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        	
        	// If we run into something we don't want to destroy, go one up.
        	if (	block.getType() == Material.STEP || 
        			block.getType() == Material.TORCH ||
        			block.getType() == Material.REDSTONE_WIRE || 
        			block.getType() == Material.RAILS || 
        			block.getType() == Material.STONE_PLATE || 
        			block.getType() == Material.WOOD_PLATE ||
        			block.getType() == Material.REDSTONE_TORCH_ON ||
        			block.getType() == Material.REDSTONE_TORCH_OFF ||
        			block.getType() == Material.CAKE_BLOCK) 
        		block = p.getWorld().getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ()); 

			// Check if the player has a chest.
			if (!p.getInventory().contains(Material.CHEST) && !hasPerm(p, "tombstone.freechest", false)) {
				p.sendMessage("[Tombstone] No chest found in inventory. Inventory dropped");
				return;
			}
        	
        	// Check if we can replace the block.
			if ( !canReplace(block.getType()) ) {
				p.sendMessage("[Tombstone] Could not find room for chest. Inventory dropped");
				return;
			}
        	
			int removeChestCount = 1;
			
			// Set the current block to a chest, init some variables for later use.
			block.setType(Material.CHEST);
			Chest sChest = (Chest)block.getState();
			Chest lChest = null;
			int slot = 0;
			int maxSlot = sChest.getInventory().getSize();
			
			// If they are allowed, spawn a large chest to catch their entire inventory.
			if (hasPerm(p, "tombstone.large", false) && !hasPerm(p, "tombstone.freechest", false)) {
				removeChestCount = 2;
				// Check if the player has two chests. No easy way to do it.
				int chestCount = 0;
				for (ItemStack i : p.getInventory().getContents()) {
					if (i.getType() == Material.CHEST) chestCount += i.getAmount();
					if (chestCount >= removeChestCount) break;
				}
				if (chestCount >= removeChestCount) {
					Block lBlock = findLarge(block);
					if (lBlock != null) {
						lBlock.setType(Material.CHEST);
						lChest = (Chest)lBlock.getState();
						maxSlot = maxSlot * 2;
					} else {
						removeChestCount = 1;
					}
				} else {
					removeChestCount = 1;
				}
			}
			
			// tombstone.freechest + tombstone.large then just place a large chest.
			if (hasPerm(p, "tombstone.freechest", false)) {
				removeChestCount = 0;
				if (hasPerm(p, "tombstone.large", false)) {
					Block lBlock = findLarge(block);
					if (lBlock != null) {
						lBlock.setType(Material.CHEST);
						lChest = (Chest)lBlock.getState();
						maxSlot = maxSlot * 2;
					}
				}
			}
			
			// Protect the chest if LWC is installed.
			Boolean prot = false;
			if (hasPerm(p, "tombstone.lwc", true))
				prot = activateLWC(p, sChest.getBlock());
			
			// First get players armor, tends to be more important.
			if (p.getInventory().getArmorContents().length > 0) {
				for (ItemStack i : p.getInventory().getArmorContents()) {
					for (ListIterator<ItemStack> iter = event.getDrops().listIterator(event.getDrops().size()); iter.hasPrevious();) {
						ItemStack j = iter.previous();
						if (j.equals(i)) {
							sChest.getInventory().setItem(slot, j);
							iter.remove();
							slot++;
							break;
						}
					}
				}
			}
			
			// Next get the players inventory using the getDrops() method.
			for (Iterator<ItemStack> iter = event.getDrops().listIterator(); iter.hasNext();) {
				ItemStack i = iter.next();
				// Take the chest
				if (removeChestCount > 0 && i.getType() == Material.CHEST) {
					if (i.getAmount() >= removeChestCount) {
						i.setAmount(i.getAmount() - removeChestCount);
						removeChestCount = 0;
					} else {
						removeChestCount -= i.getAmount();
						i.setAmount(0);
					}
					if (i.getAmount() == 0) {
						iter.remove();
						continue;
					}
				}
				
				// Add items to chest if not full.
				if (slot < maxSlot) {
					if (slot >= sChest.getInventory().getSize()) {
						if (lChest == null) break;
						lChest.getInventory().setItem(slot % lChest.getInventory().getSize(), i);
					} else {
						sChest.getInventory().setItem(slot, i);
					}
					iter.remove();
					slot++;
				} else if (removeChestCount == 0) break;
			}
			
			// Tell the player how many items went into chest.
			String msg = "Inventory stored in chest.";
			if (event.getDrops().size() > 0)
				msg +=  event.getDrops().size() + " items wouldn't fit in chest.";
			p.sendMessage("[Tombstone] " + msg);
			if (prot)
				p.sendMessage("[Tombstone] Chest protected with LWC");
        }
        
        Block findLarge(Block base) {
        	// Check all 4 sides for air.
        	Block exp;
        	exp = base.getWorld().getBlockAt(base.getX() - 1, base.getY(), base.getZ());
        	if (canReplace(exp.getType())) return exp;
        	exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() - 1);
        	if (canReplace(exp.getType())) return exp;
        	exp = base.getWorld().getBlockAt(base.getX() + 1, base.getY(), base.getZ());
        	if (canReplace(exp.getType())) return exp;
        	exp = base.getWorld().getBlockAt(base.getX(), base.getY(), base.getZ() + 1);
        	if (canReplace(exp.getType())) return exp;
        	return null;
        }
        
        Boolean canReplace(Material mat) {
        	return (mat == Material.AIR || 
        			mat == Material.SAPLING || 
        			mat == Material.WATER || 
        			mat == Material.STATIONARY_WATER || 
        			mat == Material.LAVA || 
        			mat == Material.STATIONARY_LAVA || 
        			mat == Material.YELLOW_FLOWER || 
        			mat == Material.RED_ROSE || 
        			mat == Material.BROWN_MUSHROOM || 
        			mat == Material.RED_MUSHROOM || 
        			mat == Material.FIRE || 
        			mat == Material.CROPS || 
        			mat == Material.SNOW || 
        			mat == Material.SUGAR_CANE);
        }
	}
}
