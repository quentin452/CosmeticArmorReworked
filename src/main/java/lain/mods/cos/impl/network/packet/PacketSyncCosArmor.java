package lain.mods.cos.impl.network.packet;

import java.util.UUID;
import lain.mods.cos.impl.ModObjects;
import lain.mods.cos.impl.inventory.InventoryCosArmor;
import lain.mods.cos.impl.network.NetworkManager.NetworkPacket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent.Context;

public class PacketSyncCosArmor implements NetworkPacket
{

    UUID uuid;
    int slot;
    boolean isSkinArmor;
    ItemStack itemCosArmor;

    public PacketSyncCosArmor()
    {
    }

    public PacketSyncCosArmor(UUID uuid, InventoryCosArmor inventory, int slot)
    {
        if (uuid == null)
            throw new IllegalArgumentException();
        this.uuid = uuid;
        this.slot = slot;
        this.isSkinArmor = inventory.isSkinArmor(slot);
        this.itemCosArmor = inventory.getStackInSlot(slot);
    }

    @Override
    public void handlePacketClient(Context context)
    {
        context.enqueueWork(() -> {
            ModObjects.invMan.getCosArmorInventoryClient(uuid).setStackInSlot(slot, itemCosArmor);
            ModObjects.invMan.getCosArmorInventoryClient(uuid).setSkinArmor(slot, isSkinArmor);
        });
    }

    @Override
    public void handlePacketServer(Context context)
    {
    }

    @Override
    public void readFromBuffer(PacketBuffer buffer)
    {
        uuid = new UUID(buffer.readLong(), buffer.readLong());
        slot = buffer.readByte();
        isSkinArmor = buffer.readBoolean();
        itemCosArmor = buffer.readItemStack();
    }

    @Override
    public void writeToBuffer(PacketBuffer buffer)
    {
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
        buffer.writeByte(slot);
        buffer.writeBoolean(isSkinArmor);
        buffer.writeItemStack(itemCosArmor);
    }

}
