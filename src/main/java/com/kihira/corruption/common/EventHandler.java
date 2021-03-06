package com.kihira.corruption.common;

import com.kihira.corruption.Corruption;
import com.kihira.corruption.common.corruption.CorruptionRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import kihira.foxlib.client.RenderHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityDragonPart;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.WorldEvent;

import java.io.File;
import java.util.Iterator;

public class EventHandler {

    @SubscribeEvent
    //Main corruption event
    public void onLivingDeath(LivingDeathEvent e) {
        if (!e.entityLiving.worldObj.isRemote) {
            if (Corruption.disableCorrOnDragonDeath && (e.entity instanceof EntityDragon || e.entity instanceof EntityDragonPart)) {
                Corruption.setDiableCorruption();
                for (Object obj : FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().playerEntityList) {
                    EntityPlayer player = (EntityPlayer) obj;
                    CorruptionDataHelper.removeAllCorruptionEffectsForPlayer(player);
                }
                FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().sendChatMsg(new ChatComponentText("chat.corruption.end.dragon").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_RED).setItalic(true)));
            }
            else if (Corruption.disableCorrOnWitherDeath && e.entityLiving instanceof EntityWither && e.source.getEntity() instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) e.source.getEntity();
                //Check if they can be corrupted (false if they've already killed it before)
                if (CorruptionDataHelper.canBeCorrupted(player)) {
                    CorruptionDataHelper.removeAllCorruptionEffectsForPlayer(player);
                    CorruptionDataHelper.setCanBeCorrupted(player, false);
                    CorruptionDataHelper.setCorruptionForPlayer(player, 0);
                    player.addChatComponentMessage(new ChatComponentText("chat.corruption.end.wither").setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_RED).setItalic(true)));
                }
            }
            else if (e.entityLiving instanceof EntityPlayer) {
                EntityPlayer player = (EntityPlayer) e.entityLiving;
                CorruptionDataHelper.decreaseCorruptionForPlayer(player, Corruption.corrRemovedOnDeath);
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent e) {
        //BlockTeleportCorruption
        if (!e.world.isRemote) {
            if (CorruptionDataHelper.hasCorruptionEffectsForPlayer(e.getPlayer(), "blockTeleport")) {
                if (this.teleportBlockRandomly(e.x, e.y, e.z, e.world, e.getPlayer())) {
                    //e.setCanceled(true);
                    Corruption.blockTeleportCorruption.blocksBroken.add(e.getPlayer().getCommandSenderName());
                }
            }
            else if (CorruptionDataHelper.getCorruptionForPlayer(e.getPlayer()) > 6000 && e.getPlayer().worldObj.rand.nextInt(FMLEventHandler.CORRUPTION_MAX + 6000) < CorruptionDataHelper.getCorruptionForPlayer(e.getPlayer())) {
                CorruptionDataHelper.addCorruptionEffectForPlayer(e.getPlayer(), "blockTeleport");
            }
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (CorruptionDataHelper.hasCorruptionEffectsForPlayer(e.entityPlayer, "blockTeleport") && e.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && e.entityPlayer.worldObj.getBlock(e.x, e.y, e.z) instanceof BlockContainer) {
            if (this.teleportBlockRandomly(e.x, e.y, e.z, e.entityPlayer.worldObj, e.entityPlayer)) {
                e.setResult(Event.Result.DENY);
                Corruption.blockTeleportCorruption.blocksBroken.add(e.entityPlayer.getCommandSenderName());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamage(LivingHurtEvent e) {
        if (e.entityLiving instanceof EntityPlayer && (e.entityLiving.getHealth() - e.ammount <= 6)) {
            EntityPlayer player = (EntityPlayer) e.entityLiving;
            if (CorruptionDataHelper.canBeCorrupted(player) && CorruptionDataHelper.getCorruptionForPlayer(player) > 2000 && !CorruptionDataHelper.hasCorruptionEffectsForPlayer(player, "bloodLoss")) {
                CorruptionDataHelper.addCorruptionEffectForPlayer(player, "bloodLoss");
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload e) {
        if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
            //Restores original players skins
            File skinBackupFolder = new File("skinbackup");
            if (skinBackupFolder.exists()) {
                File[] skinFiles = skinBackupFolder.listFiles();
                if (skinFiles != null) {
                    for (File skinFile : skinFiles) {
                        String playerName = skinFile.getName().substring(0, skinFile.getName().length() - 4);
                        //TODO Corruption.proxy.uncorruptPlayerSkin(playerName);
                        skinFile.delete();
                    }
                }
                skinBackupFolder.delete();
            }
            Corruption.proxy.disableGrayscaleShader();
            //Purge corruption list
            CorruptionRegistry.currentCorruptionClient.clear();
        }
        //Server
        else {
            //Reset certain corruption
            Corruption.blockTeleportCorruption.blocksBroken.clear();
            Corruption.waterAllergyCorruption.playerCount.clear();
            Corruption.gluttonyCorruption.playerCount.clear();
            Corruption.colourBlindCorruption.playerCount.clear();
            Corruption.stoneSkinCorruption.playerCount.clear();
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onRenderLiving(RenderLivingEvent.Specials.Pre e) {
        if (e.entity instanceof EntityPlayer) {
            if (Corruption.isDebugMode) {
                boolean drawPlate = Minecraft.isGuiEnabled() && e.entity != RenderManager.instance.livingPlayer && !e.entity.isInvisibleToPlayer(Minecraft.getMinecraft().thePlayer) && e.entity.riddenByEntity == null;
                if (drawPlate) {
                    String drawString = "Corruption: " + CorruptionDataHelper.getCorruptionForPlayer((EntityPlayer) e.entity);
                    RenderHelper.drawMultiLineMessageFacingPlayer(e.x, e.y + ((EntityPlayer) e.entity).height + 0.75, e.z, 0.02F, new String[]{drawString}, 0xFFFFFF, true, true);
                }
            }
        }
    }

    private boolean teleportBlockRandomly(int sourceX, int sourceY, int sourceZ, World world, EntityPlayer entityPlayer) {
        int x, y, z;
        for (int i = 0; i < 5; i++) {
            x = (int) (entityPlayer.posX + world.rand.nextInt(2 * 8) - 8);
            y = (int) (entityPlayer.posY + world.rand.nextInt(2 * 3) - 3);
            z = (int) (entityPlayer.posZ + world.rand.nextInt(2 * 8) - 8);
            if (y > 0 && world.isAirBlock(x, y, z)) {
                Block block = world.getBlock(sourceX, sourceY, sourceZ);
                int meta = world.getBlockMetadata(sourceX, sourceY, sourceZ);
                NBTTagCompound tagCompound = new NBTTagCompound();

                if (world.setBlock(x, y, z, block, meta, 3)) {
                    TileEntity tileEntity = world.getTileEntity(sourceX, sourceY, sourceZ);
                    if (tileEntity != null) {
                        tileEntity.writeToNBT(tagCompound);
                        tileEntity.invalidate();

                        world.setTileEntity(x, y, z, ((ITileEntityProvider) block).createNewTileEntity(world, meta));
                        tileEntity = world.getTileEntity(x, y, z);

                        if (tileEntity != null) {
                            NBTTagCompound nbttagcompound = new NBTTagCompound();
                            tileEntity.writeToNBT(nbttagcompound);
                            Iterator iterator = tagCompound.func_150296_c().iterator();

                            while (iterator.hasNext()) {
                                String s = (String) iterator.next();
                                NBTBase nbtbase = tagCompound.getTag(s);

                                if (!s.equals("x") && !s.equals("y") && !s.equals("z")) {
                                    nbttagcompound.setTag(s, nbtbase.copy());
                                }
                            }

                            tileEntity.readFromNBT(nbttagcompound);
                            tileEntity.markDirty();
                        }
                    }
                    world.func_147480_a(sourceX, sourceY, sourceZ, false);
                    return true;
                }
            }
        }
        return false;
    }
}
