/*******************************************************************************
 * Copyright (c) 2012 cpw.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 *
 * Contributors:
 *     cpw - initial API and implementation
 ******************************************************************************/
package cpw.mods.ironchest;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.Packet;
import net.minecraft.server.gui.IUpdatePlayerListBox;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityLockable;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IChatComponent;

public class TileEntityIronChest extends TileEntityLockable implements IUpdatePlayerListBox, IInventory 
{
    private int ticksSinceSync = -1;
    public float prevLidAngle;
    public float lidAngle;
    private int numUsingPlayers;
    private IronChestType type;
    public ItemStack[] chestContents;
    private ItemStack[] topStacks;
    private int facing;
    private boolean inventoryTouched;
    private boolean hadStuff;
    private String customName;

    public TileEntityIronChest()
    {
        this(IronChestType.IRON);
    }

    protected TileEntityIronChest(IronChestType type)
    {
        super();
        this.type = type;
        this.chestContents = new ItemStack[getSizeInventory()];
        this.topStacks = new ItemStack[8];
    }

    public ItemStack[] getContents()
    {
        return chestContents;
    }

    @Override
    public int getSizeInventory()
    {
        return type.size;
    }

    public int getFacing()
    {
        return this.facing;
    }

    public IronChestType getType()
    {
        return type;
    }

    @Override
    public ItemStack getStackInSlot(int i)
    {
        inventoryTouched = true;
        return chestContents[i];
    }

    @Override
    public void markDirty()
    {
        super.markDirty();
        sortTopStacks();
    }

    protected void sortTopStacks()
    {
        if (!type.isTransparent() || (worldObj != null && worldObj.isRemote))
        {
            return;
        }
        ItemStack[] tempCopy = new ItemStack[getSizeInventory()];
        boolean hasStuff = false;
        int compressedIdx = 0;
        mainLoop: for (int i = 0; i < getSizeInventory(); i++)
        {
            if (chestContents[i] != null)
            {
                for (int j = 0; j < compressedIdx; j++)
                {
                    if (tempCopy[j].isItemEqual(chestContents[i]))
                    {
                        tempCopy[j].stackSize += chestContents[i].stackSize;
                        continue mainLoop;
                    }
                }
                tempCopy[compressedIdx++] = chestContents[i].copy();
                hasStuff = true;
            }
        }
        if (!hasStuff && hadStuff)
        {
            hadStuff = false;
            for (int i = 0; i < topStacks.length; i++)
            {
                topStacks[i] = null;
            }
            if (worldObj != null)
            {
                worldObj.markBlockForUpdate(pos);
            }
            return;
        }
        hadStuff = true;
        Arrays.sort(tempCopy, new Comparator<ItemStack>() {
            @Override
            public int compare(ItemStack o1, ItemStack o2)
            {
                if (o1 == null)
                {
                    return 1;
                }
                else if (o2 == null)
                {
                    return -1;
                }
                else
                {
                    return o2.stackSize - o1.stackSize;
                }
            }
        });
        int p = 0;
        for (int i = 0; i < tempCopy.length; i++)
        {
            if (tempCopy[i] != null && tempCopy[i].stackSize > 0)
            {
                topStacks[p++] = tempCopy[i];
                if (p == topStacks.length)
                {
                    break;
                }
            }
        }
        for (int i = p; i < topStacks.length; i++)
        {
            topStacks[i] = null;
        }
        if (worldObj != null)
        {
            worldObj.markBlockForUpdate(pos);
        }
    }

    @Override
    public ItemStack decrStackSize(int i, int j)
    {
        if (chestContents[i] != null)
        {
            if (chestContents[i].stackSize <= j)
            {
                ItemStack itemstack = chestContents[i];
                chestContents[i] = null;
                markDirty();
                return itemstack;
            }
            ItemStack itemstack1 = chestContents[i].splitStack(j);
            if (chestContents[i].stackSize == 0)
            {
                chestContents[i] = null;
            }
            markDirty();
            return itemstack1;
        }
        else
        {
            return null;
        }
    }

    @Override
    public void setInventorySlotContents(int i, ItemStack itemstack)
    {
        chestContents[i] = itemstack;
        if (itemstack != null && itemstack.stackSize > getInventoryStackLimit())
        {
            itemstack.stackSize = getInventoryStackLimit();
        }
        markDirty();
    }
    
    @Override
    public String getCommandSenderName()
    {
        return this.hasName() ? this.customName : type.name();
    }
    
    @Override
    public boolean hasName()
    {
        return this.customName != null && this.customName.length() > 0;
    }
    
    public void setCustomName(String name)
    {
        this.customName = name;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbttagcompound)
    {
        super.readFromNBT(nbttagcompound);
        
        //10 - TAG_COMPOUND
        NBTTagList nbttaglist = nbttagcompound.getTagList("Items", 10);
        this.chestContents = new ItemStack[getSizeInventory()];
        
        //8 - TAG_STRING
        if (nbttagcompound.hasKey("CustomName", 8))
        {
            this.customName = nbttagcompound.getString("CustomName");
        }
        
        for (int i = 0; i < nbttaglist.tagCount(); i++)
        {
            NBTTagCompound nbttagcompound1 = nbttaglist.getCompoundTagAt(i);
            int j = nbttagcompound1.getByte("Slot") & 0xff;
            if (j >= 0 && j < chestContents.length)
            {
                chestContents[j] = ItemStack.loadItemStackFromNBT(nbttagcompound1);
            }
        }
        facing = nbttagcompound.getByte("facing");
        sortTopStacks();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbttagcompound)
    {
        super.writeToNBT(nbttagcompound);
        NBTTagList nbttaglist = new NBTTagList();
        for (int i = 0; i < chestContents.length; i++)
        {
            if (chestContents[i] != null)
            {
                NBTTagCompound nbttagcompound1 = new NBTTagCompound();
                nbttagcompound1.setByte("Slot", (byte) i);
                chestContents[i].writeToNBT(nbttagcompound1);
                nbttaglist.appendTag(nbttagcompound1);
            }
        }

        nbttagcompound.setTag("Items", nbttaglist);
        nbttagcompound.setByte("facing", (byte)facing);
        
        if (this.hasName())
        {
            nbttagcompound.setString("CustomName", this.customName);
        }
    }

    @Override
    public int getInventoryStackLimit()
    {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer entityplayer)
    {
        if (worldObj == null)
        {
            return true;
        }
        if (worldObj.getTileEntity(pos) != this)
        {
            return false;
        }
        return entityplayer.getDistanceSq((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D) <= 64D;
    }

    @Override
    public void update()
    {
        // Resynchronize clients with the server state
        if (worldObj != null && !this.worldObj.isRemote && this.numUsingPlayers != 0 && (this.ticksSinceSync + pos.getX() + pos.getY() + pos.getZ()) % 200 == 0)
        {
            this.numUsingPlayers = 0;
            float var1 = 5.0F;
            @SuppressWarnings("unchecked")
            List<EntityPlayer> var2 = this.worldObj.getEntitiesWithinAABB(EntityPlayer.class, new AxisAlignedBB((double)((float)pos.getX() - var1), (double)((float)pos.getY() - var1), (double)((float)pos.getZ() - var1), (double)((float)(pos.getX() + 1) + var1), (double)((float)(pos.getY() + 1) + var1), (double)((float)(pos.getZ() + 1) + var1)));

            for (EntityPlayer var4 : var2) {
                if (var4.openContainer instanceof ContainerIronChest) {
                    ++this.numUsingPlayers;
                }
            }
        }

        if (worldObj != null && !worldObj.isRemote && ticksSinceSync < 0)
        {
            worldObj.addBlockEvent(pos, IronChest.ironChestBlock, 3, ((numUsingPlayers << 3) & 0xF8) | (facing & 0x7));
        }
        if (!worldObj.isRemote && inventoryTouched)
        {
            inventoryTouched = false;
            sortTopStacks();
        }

        this.ticksSinceSync++;
        prevLidAngle = lidAngle;
        float f = 0.1F;
        if (numUsingPlayers > 0 && lidAngle == 0.0F)
        {
            double d = (double) pos.getX() + 0.5D;
            double d1 = (double) pos.getZ() + 0.5D;
            worldObj.playSoundEffect(d, (double) pos.getY() + 0.5D, d1, "random.chestopen", 0.5F, worldObj.rand.nextFloat() * 0.1F + 0.9F);
        }
        if (numUsingPlayers == 0 && lidAngle > 0.0F || numUsingPlayers > 0 && lidAngle < 1.0F)
        {
            float f1 = lidAngle;
            if (numUsingPlayers > 0)
            {
                lidAngle += f;
            }
            else
            {
                lidAngle -= f;
            }
            if (lidAngle > 1.0F)
            {
                lidAngle = 1.0F;
            }
            float f2 = 0.5F;
            if (lidAngle < f2 && f1 >= f2)
            {
                double d2 = (double) pos.getX() + 0.5D;
                double d3 = (double) pos.getZ() + 0.5D;
                worldObj.playSoundEffect(d2, (double) pos.getY() + 0.5D, d3, "random.chestclosed", 0.5F, worldObj.rand.nextFloat() * 0.1F + 0.9F);
            }
            if (lidAngle < 0.0F)
            {
                lidAngle = 0.0F;
            }
        }
    }

    @Override
    public boolean receiveClientEvent(int i, int j)
    {
        if (i == 1)
        {
            numUsingPlayers = j;
        }
        else if (i == 2)
        {
            facing = (byte) j;
        }
        else if (i == 3)
        {
            facing = (byte) (j & 0x7);
            numUsingPlayers = (j & 0xF8) >> 3;
        }
        return true;
    }

    @Override
    public void openInventory(EntityPlayer player)
    {
        if (worldObj == null) return;
        numUsingPlayers++;
        worldObj.addBlockEvent(pos, IronChest.ironChestBlock, 1, numUsingPlayers);
    }

    @Override
    public void closeInventory(EntityPlayer player)
    {
        if (worldObj == null) return;
        numUsingPlayers--;
        worldObj.addBlockEvent(pos, IronChest.ironChestBlock, 1, numUsingPlayers);
    }

    public void setFacing(int facing2)
    {
        this.facing = facing2;
    }

    public TileEntityIronChest applyUpgradeItem(ItemChestChanger itemChestChanger)
    {
        if (numUsingPlayers > 0)
        {
            return null;
        }
        if (!itemChestChanger.getType().canUpgrade(this.getType()))
        {
            return null;
        }
        TileEntityIronChest newEntity = IronChestType.makeEntity(itemChestChanger.getTargetChestOrdinal(getType().ordinal()));
        int newSize = newEntity.chestContents.length;
        System.arraycopy(chestContents, 0, newEntity.chestContents, 0, Math.min(newSize, chestContents.length));
        BlockIronChest block = IronChest.ironChestBlock;
        block.dropContent(newSize, this, this.worldObj, pos);
        newEntity.setFacing(facing);
        newEntity.sortTopStacks();
        newEntity.ticksSinceSync = -1;
        return newEntity;
    }

    public ItemStack[] getTopItemStacks()
    {
        return topStacks;
    }

    public TileEntityIronChest updateFromMetadata(int l)
    {
        if (worldObj != null && worldObj.isRemote)
        {
            if (l != type.ordinal())
            {
                worldObj.setTileEntity(pos, IronChestType.makeEntity(l));
                return (TileEntityIronChest) worldObj.getTileEntity(pos);
            }
        }
        return this;
    }

    @Override
    public Packet getDescriptionPacket()
    {
        return PacketHandler.getPacket(this);
    }

    public void handlePacketData(int typeData, ItemStack[] intData)
    {
        TileEntityIronChest chest = this;
        if (this.type.ordinal() != typeData)
        {
            chest = updateFromMetadata(typeData);
        }
        if (IronChestType.values()[typeData].isTransparent() && intData != null)
        {
            int pos = 0;
            for (int i = 0; i < chest.topStacks.length; i++)
            {
                if (intData[pos] != null)
                {
                    chest.topStacks[i] = intData[pos];
                }
                else
                {
                    chest.topStacks[i] = null;
                }
                pos ++;
            }
        }
    }

    public ItemStack[] buildItemStackDataList()
    {
        if (type.isTransparent())
        {
            ItemStack[] sortList = new ItemStack[topStacks.length];
            int pos = 0;
            for (ItemStack is : topStacks)
            {
                if (is != null)
                {
                    sortList[pos++] = is;
                }
                else
                {
                    sortList[pos++] = null;
                }
            }
            return sortList;
        }
        return null;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int par1)
    {
        if (this.chestContents[par1] != null)
        {
            ItemStack var2 = this.chestContents[par1];
            this.chestContents[par1] = null;
            return var2;
        }
        else
        {
            return null;
        }
    }

    public void setMaxStackSize(int size)
    {

    }

    @Override
    public boolean isItemValidForSlot(int i, ItemStack itemstack)
    {
        return type.acceptsStack(itemstack);
    }



    /*void rotateAround(ForgeDirection axis)
    {
        setFacing((byte)ForgeDirection.getOrientation(facing).getRotation(axis).ordinal());
        worldObj.addBlockEvent(this.xCoord, this.yCoord, this.zCoord, IronChest.ironChestBlock, 2, getFacing());
    }*/

    public void wasPlaced(EntityLivingBase entityliving, ItemStack itemStack)
    {
    }

    public void removeAdornments() {}

    @Override
    public int getField(int id)
    {
        return 0;
    }

    @Override
    public void setField(int id, int value) {}

    @Override
    public int getFieldCount()
    {
        return 0;
    }

    @Override
    public void clearInventory()
    {
        for (int i = 0; i < this.chestContents.length; ++i)
        {
            this.chestContents[i] = null;
        }
    }

    @Override
    public Container createContainer(InventoryPlayer playerInventory, EntityPlayer player)
    {
        return null;
    }

    @Override
    public String getGuiID()
    {
        return "IronChest:" + type.name();
    }
}
