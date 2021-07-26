package lain.mods.cos.impl;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lain.mods.cos.api.event.CosArmorDeathDrops;
import lain.mods.cos.impl.inventory.ContainerCosArmor;
import lain.mods.cos.impl.inventory.InventoryCosArmor;
import lain.mods.cos.impl.network.packet.PacketSyncCosArmor;
import lain.mods.cos.impl.network.packet.PacketSyncHiddenFlags;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fmllegacy.LogicalSidedProvider;
import net.minecraftforge.fmlserverevents.FMLServerStoppingEvent;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.Random;
import java.util.UUID;

public class InventoryManager {

    protected static final InventoryCosArmor Dummy = new InventoryCosArmor() {

        @Override
        @Nonnull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        @Nonnull
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        @Nonnull
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public boolean isHidden(String modid, String identifier) {
            return false;
        }

        @Override
        public boolean isSkinArmor(int slot) {
            return false;
        }

        @Override
        protected void onContentsChanged(int slot) {
        }

        @Override
        protected void onLoad() {
        }

        @Override
        public boolean setHidden(String modid, String identifier, boolean set) {
            return false;
        }

        @Override
        public void setSkinArmor(int slot, boolean enabled) {
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        }

        @Override
        public boolean setUpdateListener(ContentsChangeListener listener) {
            return false;
        }

        @Override
        public boolean setUpdateListener(HiddenFlagsChangeListener listener) {
            return false;
        }

    };

    protected static final Random RANDOM = new Random();

    protected final LoadingCache<UUID, InventoryCosArmor> CommonCache = CacheBuilder.newBuilder().build(new CacheLoader<UUID, InventoryCosArmor>() {

        @Override
        public InventoryCosArmor load(UUID key) throws Exception {
            InventoryCosArmor inventory = new InventoryCosArmor();
            inventory.setUpdateListener((inv, slot) -> onInventoryChanged(key, inv, slot));
            inventory.setUpdateListener((inv, modid, identifier) -> onHiddenFlagsChanged(key, inv, modid, identifier));
            loadInventory(key, inventory);
            return inventory;
        }

    });

    public static boolean checkIdentifier(String modid, String identifier) {
        if (modid == null || modid.isEmpty() || identifier == null || identifier.isEmpty() || !ModList.get().isLoaded(modid))
            return false;

        return false;
    }

    public ContainerCosArmor createContainerClient(int windowId, Inventory invPlayer, FriendlyByteBuf extraData) {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    public InventoryCosArmor getCosArmorInventory(UUID uuid) {
        if (uuid == null)
            return Dummy;
        return CommonCache.getUnchecked(uuid);
    }

    @Nonnull
    public InventoryCosArmor getCosArmorInventoryClient(UUID uuid) {
        throw new UnsupportedOperationException();
    }

    protected File getDataFile(UUID uuid) {
        return new File(LogicalSidedProvider.INSTANCE.<MinecraftServer>get(LogicalSide.SERVER).playerDataStorage.getPlayerDataFolder(), uuid + ".cosarmor");
    }

    private void handlePlayerDrops(LivingDropsEvent event) {
        if (event.getEntityLiving() instanceof Player) {
            if (event.getEntityLiving().isEffectiveAi() && !event.getEntityLiving().getCommandSenderWorld().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) && !ModConfigs.CosArmorKeepThroughDeath.get()) {
                InventoryCosArmor inv = getCosArmorInventory(event.getEntityLiving().getUUID());
                if (MinecraftForge.EVENT_BUS.post(new CosArmorDeathDrops((Player) event.getEntityLiving(), inv)))
                    return;
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i).copy();
                    if (stack.isEmpty())
                        continue;

                    float fX = RANDOM.nextFloat() * 0.75F + 0.125F;
                    float fY = RANDOM.nextFloat() * 0.75F;
                    float fZ = RANDOM.nextFloat() * 0.75F + 0.125F;
                    while (!stack.isEmpty()) {
                        ItemEntity entity = new ItemEntity(event.getEntityLiving().getCommandSenderWorld(), event.getEntityLiving().getX() + (double) fX, event.getEntityLiving().getY() + (double) fY, event.getEntityLiving().getZ() + (double) fZ, stack.split(RANDOM.nextInt(21) + 10));
                        entity.setDeltaMovement(RANDOM.nextGaussian() * (double) 0.05F, RANDOM.nextGaussian() * (double) 0.05F + (double) 0.2F, RANDOM.nextGaussian() * (double) 0.05F);
                        event.getDrops().add(entity);
                    }

                    inv.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
        }
    }

    private void handlePlayerLoggedIn(PlayerLoggedInEvent event) {
        CommonCache.invalidate(event.getPlayer().getUUID());
        getCosArmorInventory(event.getPlayer().getUUID());

        if (event.getPlayer() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getPlayer();
            for (ServerPlayer other : LogicalSidedProvider.INSTANCE.<MinecraftServer>get(LogicalSide.SERVER).getPlayerList().getPlayers()) {
                if (other == player)
                    continue;
                UUID uuid = other.getUUID();
                InventoryCosArmor inv = getCosArmorInventory(uuid);
                for (int i = 0; i < inv.getSlots(); i++)
                    ModObjects.network.sendTo(new PacketSyncCosArmor(uuid, inv, i), player);
                inv.forEachHidden((modid, identifier) -> ModObjects.network.sendTo(new PacketSyncHiddenFlags(uuid, inv, modid, identifier), player));
            }
        }
    }

    private void handlePlayerLoggedOut(PlayerLoggedOutEvent event) {
        UUID uuid;
        InventoryCosArmor inv;
        if ((inv = CommonCache.getIfPresent(uuid = event.getPlayer().getUUID())) != null) {
            saveInventory(uuid, inv);
            CommonCache.invalidate(uuid);
        }
    }

    private void handleRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("clearcosarmor").requires(s -> {
            return s.hasPermission(2);
        }).executes(s -> {
            int count = 0;
            ServerPlayer player = s.getSource().getPlayerOrException();
            InventoryCosArmor inv = getCosArmorInventory(player.getUUID());
            for (int i = 0; i < inv.getSlots(); i++)
                count += inv.extractItem(i, Integer.MAX_VALUE, false).getCount();
            s.getSource().sendSuccess(new TranslatableComponent("cos.command.clearcosarmor.success.single", count, player.getDisplayName()), true);
            return count;
        }).then(Commands.argument("targets", EntityArgument.players()).executes(s -> {
            int count = 0;
            Collection<ServerPlayer> players = EntityArgument.getPlayers(s, "targets");
            for (ServerPlayer player : players) {
                InventoryCosArmor inv = getCosArmorInventory(player.getUUID());
                for (int i = 0; i < inv.getSlots(); i++)
                    count += inv.extractItem(i, Integer.MAX_VALUE, false).getCount();
            }
            if (players.size() == 1)
                s.getSource().sendSuccess(new TranslatableComponent("cos.command.clearcosarmor.success.single", count, players.iterator().next().getDisplayName()), true);
            else
                s.getSource().sendSuccess(new TranslatableComponent("cos.command.clearcosarmor.success.multiple", count, players.size()), true);
            return count;
        })));

        if (!ModConfigs.CosArmorDisableCosHatCommand.get()) {
            event.getDispatcher().register(Commands.literal("coshat").requires(s -> {
                return s.hasPermission(0);
            }).executes(s -> {
                ServerPlayer player = s.getSource().getPlayerOrException();
                InventoryCosArmor inv = getCosArmorInventory(player.getUUID());
                ItemStack stack1 = player.getItemBySlot(EquipmentSlot.MAINHAND);
                ItemStack stack2 = inv.getStackInSlot(3);
                player.setItemSlot(EquipmentSlot.MAINHAND, stack2);
                inv.setStackInSlot(3, stack1);
                return 0;
            }));
        }
    }

    private void handleSaveToFile(PlayerEvent.SaveToFile event) {
        UUID uuid;
        InventoryCosArmor inv;
        if ((inv = CommonCache.getIfPresent(uuid = UUID.fromString(event.getPlayerUUID()))) != null)
            saveInventory(uuid, inv);
    }

    private void handleServerStopping(FMLServerStoppingEvent event) {
        ModObjects.logger.debug("Server is stopping... try to save all still loaded CosmeticArmor data");
        CommonCache.asMap().entrySet().forEach(e -> {
            ModObjects.logger.debug(e.getKey());
            saveInventory(e.getKey(), e.getValue());
        });
        CommonCache.invalidateAll();
    }

    protected void loadInventory(UUID uuid, InventoryCosArmor inventory) {
        if (inventory == Dummy)
            return;
        try {
            File file;
            if ((file = getDataFile(uuid)).exists())
                inventory.deserializeNBT(NbtIo.read(file));
        } catch (Throwable t) {
            ModObjects.logger.fatal("Failed to load CosmeticArmor data", t);
        }
    }

    protected void onHiddenFlagsChanged(UUID uuid, InventoryCosArmor inventory, String modid, String identifier) {
        ModObjects.network.sendToAll(new PacketSyncHiddenFlags(uuid, inventory, modid, identifier));
    }

    protected void onInventoryChanged(UUID uuid, InventoryCosArmor inventory, int slot) {
        ModObjects.network.sendToAll(new PacketSyncCosArmor(uuid, inventory, slot));
    }

    public void registerEvents() {
        MinecraftForge.EVENT_BUS.addListener(this::handlePlayerDrops);
        MinecraftForge.EVENT_BUS.addListener(this::handlePlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(this::handlePlayerLoggedOut);
        MinecraftForge.EVENT_BUS.addListener(this::handleSaveToFile);
        MinecraftForge.EVENT_BUS.addListener(this::handleRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::handleServerStopping);
    }

    public void registerEventsClient() {
        throw new UnsupportedOperationException();
    }

    protected void saveInventory(UUID uuid, InventoryCosArmor inventory) {
        if (inventory == Dummy)
            return;
        try {
            NbtIo.write(inventory.serializeNBT(), getDataFile(uuid));
        } catch (Throwable t) {
            ModObjects.logger.fatal("Failed to save CosmeticArmor data", t);
        }
    }

}
