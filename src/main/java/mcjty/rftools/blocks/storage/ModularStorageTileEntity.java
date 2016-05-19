package mcjty.rftools.blocks.storage;

import mcjty.lib.container.DefaultSidedInventory;
import mcjty.lib.container.InventoryHelper;
import mcjty.lib.entity.GenericTileEntity;
import mcjty.lib.network.Argument;
import mcjty.lib.varia.CustomSidedInvWrapper;
import mcjty.rftools.ClientInfo;
import mcjty.rftools.api.general.IInventoryTracker;
import mcjty.rftools.items.storage.StorageFilterCache;
import mcjty.rftools.items.storage.StorageFilterItem;
import mcjty.rftools.items.storage.StorageModuleItem;
import mcjty.rftools.items.storage.StorageTypeItem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.Map;

public class ModularStorageTileEntity extends GenericTileEntity implements ITickable, DefaultSidedInventory, IInventoryTracker {

    public static final String CMD_SETTINGS = "settings";
    public static final String CMD_COMPACT = "compact";
    public static final String CMD_CYCLE = "cycle";

    private int[] accessible = null;
    private int maxSize = 0;
    private int version = 0;

    private StorageFilterCache filterCache = null;

    private InventoryHelper inventoryHelper = new InventoryHelper(this, ModularStorageContainer.factory, ModularStorageContainer.SLOT_STORAGE + ModularStorageContainer.MAXSIZE_STORAGE);

    private String sortMode = "";
    private String viewMode = "";
    private boolean groupMode = false;
    private String filter = "";

    private int numStacks = -1;       // -1 means no storage cell.
    private int remoteId = 0;

    private int prevLevel = -3;     // -3 means to check, -2 means invalid
    private int timer = 10;

    private RemoteStorageTileEntity cachedRemoteStorage;
    private int cachedRemoteStorageId;

    @Override
    public void update() {
        if (!worldObj.isRemote) {
            checkStateServer();
        }
    }

    private void checkStateServer() {
        timer--;
        if (timer > 0) {
            return;
        }
        timer = 10;
        cachedRemoteStorage = null;
        cachedRemoteStorageId = -1;

        if (isRemote()) {
            // Only if we have a remote storage module do we have to do anything.
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            int si = -1;
            if (storageTileEntity != null) {
                si = storageTileEntity.findRemoteIndex(remoteId);
            }
            if (si == -1) {
                if (prevLevel != -2) {
                    prevLevel = -2;
                    numStacks = -1;
                    setMaxSize(0);
                }
                return;
            }

            numStacks = storageTileEntity.getCount(si);

            int newMaxSize = storageTileEntity.getMaxStacks(si);
            if (newMaxSize != maxSize) {
                setMaxSize(newMaxSize);
            }
            int level = getRenderLevel();
            if (level != prevLevel) {
                prevLevel = level;
                markDirtyClient();
            }
        }
    }

    @Override
    public InventoryHelper getInventoryHelper() {
        return inventoryHelper;
    }

    @Override
    public int[] getSlotsForFace(EnumFacing side) {
        if (accessible == null) {
            accessible = new int[maxSize];
            for (int i = 0 ; i < maxSize ; i++) {
                accessible[i] = ModularStorageContainer.SLOT_STORAGE + i;
            }
        }
        return accessible;
    }

    @Override
    public boolean canInsertItem(int index, ItemStack stack, EnumFacing direction) {
        return index >= ModularStorageContainer.SLOT_STORAGE && isItemValidForSlot(index, stack);
    }

    @Override
    public boolean canExtractItem(int index, ItemStack stack, EnumFacing direction) {
        return index >= ModularStorageContainer.SLOT_STORAGE && isItemValidForSlot(index, stack);
    }

    public boolean isGroupMode() {
        return groupMode;
    }

    public void setGroupMode(boolean groupMode) {
        this.groupMode = groupMode;
        markDirty();
    }

    public String getSortMode() {
        return sortMode;
    }

    public void setSortMode(String sortMode) {
        this.sortMode = sortMode;
        markDirty();
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
        markDirty();
    }

    public String getViewMode() {
        return viewMode;
    }

    public void setViewMode(String viewMode) {
        this.viewMode = viewMode;
        markDirty();
    }

    public int getMaxSize() {
        return maxSize;
    }

    @Override
    public int getSizeInventory() {
        return ModularStorageContainer.SLOT_STORAGE + maxSize;
    }

    private boolean containsItem(int index) {
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return false;
            }
            ItemStack[] slots = storageTileEntity.findStacksForId(remoteId);
            if (slots == null || index >= slots.length) {
                return false;
            }
            return slots[index] != null;
        } else {
            return inventoryHelper.containsItem(index);
        }
    }

    // On server, and if we have a remote storage module and if we're accessing a remote slot we check the remote storage.
    private boolean isStorageAvailableRemotely(int index) {
        return isServer() && isRemote() && index >= ModularStorageContainer.SLOT_STORAGE;
    }

    private boolean isRemote() {
        return remoteId != 0;
    }

    public int getRemoteId() {
        return remoteId;
    }

    @Override
    public ItemStack getStackInSlot(int index) {
        if (index >= getSizeInventory()) {
            return null;
        }
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return null;
            }
            ItemStack[] slots = storageTileEntity.findStacksForId(remoteId);
            if (slots == null || index >= slots.length) {
                return null;
            }
            return slots[index];
        }
        return inventoryHelper.getStackInSlot(index);
    }

    private void handleNewAmount(boolean s1, int index) {
        if (index < ModularStorageContainer.SLOT_STORAGE) {
            return;
        }
        boolean s2 = containsItem(index);
        if (s1 == s2) {
            return;
        }

        int rlold = getRenderLevel();

        if (s1) {
            numStacks--;
        } else {
            numStacks++;
        }
        StorageModuleItem.updateStackSize(getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE), numStacks);

        int rlnew = getRenderLevel();
        if (rlold != rlnew) {
            markDirtyClient();
        }
    }

    public int getRenderLevel() {
        if (numStacks == -1 || maxSize == 0) {
            return -1;
        }
        return (numStacks+6) * 7 / maxSize;
    }

    public int getNumStacks() {
        return numStacks;
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        version++;
        return inventoryHelper.removeStackFromSlot(index);
    }

    private ItemStack decrStackSizeHelper(int index, int amount) {
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return null;
            }

            int si = storageTileEntity.findRemoteIndex(remoteId);
            if (si == -1) {
                return null;
            }
            return storageTileEntity.decrStackSizeRemote(si, index, amount);
        } else {
            return inventoryHelper.decrStackSize(index, amount);
        }
    }

    @Override
    public ItemStack decrStackSize(int index, int amount) {
        version++;
        if (index == ModularStorageContainer.SLOT_STORAGE_MODULE) {
            if (!worldObj.isRemote) {
                copyToModule();
            }
        }

        boolean s1 = containsItem(index);
        ItemStack itemStack = decrStackSizeHelper(index, amount);
        handleNewAmount(s1, index);

        if (index == ModularStorageContainer.SLOT_STORAGE_MODULE) {
            ItemStack stackInSlot = inventoryHelper.getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE);
            // Will probably be null here. Just to be safe
            copyFromModule(stackInSlot);
        }
        return itemStack;
    }

    private void setInventorySlotContentsHelper(int limit, int index, ItemStack stack) {
        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return;
            }

            int si = storageTileEntity.findRemoteIndex(remoteId);
            if (si == -1) {
                return;
            }
            storageTileEntity.updateRemoteSlot(si, limit, index, stack);
        } else {
            inventoryHelper.setInventorySlotContents(getInventoryStackLimit(), index, stack);
        }
    }

    @Override
    public void setInventorySlotContents(int index, ItemStack stack) {
        version++;
        if (index == ModularStorageContainer.SLOT_STORAGE_MODULE) {
            if (isServer()) {
                copyToModule();
            }
        } else if (index == ModularStorageContainer.SLOT_TYPE_MODULE) {
            // Make sure front side is updated.
            IBlockState state = worldObj.getBlockState(getPos());
            worldObj.notifyBlockUpdate(getPos(), state, state, 3);
        } else if (index == ModularStorageContainer.SLOT_FILTER_MODULE) {
            filterCache = null;
        }
        boolean s1 = containsItem(index);

        setInventorySlotContentsHelper(getInventoryStackLimit(), index, stack);

        if (index == ModularStorageContainer.SLOT_STORAGE_MODULE) {
            if (isServer()) {
                copyFromModule(stack);
            }
        }

        handleNewAmount(s1, index);
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return canPlayerAccess(player);
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        if (index >= getSizeInventory()) {
            return false;
        }

        switch (index) {
            case ModularStorageContainer.SLOT_STORAGE_MODULE:
                return stack != null && ModularStorageSetup.storageModuleItem == stack.getItem();
            case ModularStorageContainer.SLOT_FILTER_MODULE:
                return stack != null && stack.getItem() instanceof StorageFilterItem;
            case ModularStorageContainer.SLOT_TYPE_MODULE:
                return stack != null && stack.getItem() instanceof StorageTypeItem;
        }

        if (index < ModularStorageContainer.SLOT_STORAGE) {
            return true;
        }

        if (isStorageAvailableRemotely(index)) {
            index -= ModularStorageContainer.SLOT_STORAGE;
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return false;
            }

            ItemStack[] stacks = storageTileEntity.findStacksForId(remoteId);
            if (stacks == null || index >= stacks.length) {
                return false;
            }
        }

        if (inventoryHelper.containsItem(ModularStorageContainer.SLOT_FILTER_MODULE)) {
            getFilterCache();
            if (filterCache != null) {
                return filterCache.match(stack);
            }
        }

        return true;
    }

    private void getFilterCache() {
        if (filterCache == null) {
            filterCache = StorageFilterItem.getCache(inventoryHelper.getStackInSlot(ModularStorageContainer.SLOT_FILTER_MODULE));
        }
    }

    public void copyToModule() {
        ItemStack stack = inventoryHelper.getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE);
        if (stack == null) {
            // Should be impossible.
            return;
        }

        if (stack.getItemDamage() == StorageModuleItem.STORAGE_REMOTE) {
            remoteId = 0;
            return;
        }
        NBTTagCompound tagCompound = stack.getTagCompound();
        if (tagCompound == null) {
            tagCompound = new NBTTagCompound();
            stack.setTagCompound(tagCompound);
        }
        int cnt = writeBufferToItemNBT(tagCompound);
        tagCompound.setInteger("count", cnt);
    }

    public void copyFromModule(ItemStack stack) {
        for (int i = ModularStorageContainer.SLOT_STORAGE ; i < inventoryHelper.getCount() ; i++) {
            inventoryHelper.setInventorySlotContents(0, i, null);
        }

        if (stack == null) {
            setMaxSize(0);
            numStacks = -1;
            return;
        }

        remoteId = 0;
        if (stack.getItemDamage() == StorageModuleItem.STORAGE_REMOTE) {
            NBTTagCompound tagCompound = stack.getTagCompound();
            if (tagCompound == null || !tagCompound.hasKey("id")) {
                setMaxSize(0);
                numStacks = -1;
                return;
            }
            remoteId = tagCompound.getInteger("id");
            RemoteStorageTileEntity remoteStorageTileEntity = getRemoteStorage(remoteId);
            if (remoteStorageTileEntity == null) {
                setMaxSize(0);
                numStacks = -1;
                return;
            }
            ItemStack storageStack = remoteStorageTileEntity.findStorageWithId(remoteId);
            if (storageStack == null) {
                setMaxSize(0);
                numStacks = -1;
                return;
            }
            setMaxSize(StorageModuleItem.MAXSIZE[storageStack.getItemDamage()]);
        } else {
            setMaxSize(StorageModuleItem.MAXSIZE[stack.getItemDamage()]);
            NBTTagCompound tagCompound = stack.getTagCompound();
            if (tagCompound != null) {
                readBufferFromItemNBT(tagCompound);
            }
        }

        updateStackCount();
    }

    private RemoteStorageTileEntity getRemoteStorage(int id) {
        if (id != cachedRemoteStorageId) {
            cachedRemoteStorage = null;
        }
        if (cachedRemoteStorage != null) {
            return cachedRemoteStorage;
        }

        World world = getWorldSafe();
        cachedRemoteStorage = RemoteStorageIdRegistry.getRemoteStorage(world, id);
        if (cachedRemoteStorage != null) {
            cachedRemoteStorageId = id;
        } else {
            cachedRemoteStorageId = -1;
        }

        return cachedRemoteStorage;
    }

    private void updateStackCount() {
        numStacks = 0;
        if (isServer() && isRemote()) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return;
            }
            int si = storageTileEntity.findRemoteIndex(remoteId);
            if (si == -1) {
                return;
            }
            ItemStack[] stacks = storageTileEntity.getRemoteStacks(si);
            for (int i = 0 ; i < Math.min(maxSize, stacks.length) ; i++) {
                if (stacks[i] != null) {
                    numStacks++;
                }
            }
            storageTileEntity.updateCount(si, numStacks);
        } else {
            for (int i = ModularStorageContainer.SLOT_STORAGE; i < ModularStorageContainer.SLOT_STORAGE + maxSize; i++) {
                if (inventoryHelper.containsItem(i)) {
                    numStacks++;
                }
            }
        }
        StorageModuleItem.updateStackSize(getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE), numStacks);
    }

    private boolean isServer() {
        if (worldObj != null) {
            return !worldObj.isRemote;
        } else {
            return FMLCommonHandler.instance().getEffectiveSide() == Side.SERVER;
        }
    }

    private World getWorldSafe() {
        World world = worldObj;
        if (world == null) {
            if (FMLCommonHandler.instance().getEffectiveSide() == Side.CLIENT) {
                world = ClientInfo.getWorld();
            } else {
                world = DimensionManager.getWorld(0);
            }
        }
        return world;
    }

    private void setMaxSize(int ms) {
        maxSize = ms;
        inventoryHelper.setNewCount(ModularStorageContainer.SLOT_STORAGE + maxSize);
        accessible = null;

        markDirtyClient();
    }

    @Override
    public void readFromNBT(NBTTagCompound tagCompound) {
        super.readFromNBT(tagCompound);
    }

    @Override
    public void readRestorableFromNBT(NBTTagCompound tagCompound) {
        super.readRestorableFromNBT(tagCompound);
        numStacks = tagCompound.getInteger("numStacks");
        maxSize = tagCompound.getInteger("maxSize");
//        System.out.println((isServer() ? "SERVER" : "CLIENT") + ": loc=" + xCoord + "," + yCoord + "," + zCoord + ", #stacks=" + numStacks + ", max=" + maxSize);
        remoteId = tagCompound.getInteger("remoteId");
        sortMode = tagCompound.getString("sortMode");
        viewMode = tagCompound.getString("viewMode");
        groupMode = tagCompound.getBoolean("groupMode");
        version = tagCompound.getInteger("version");
        filter = tagCompound.getString("filter");
        inventoryHelper.setNewCount(ModularStorageContainer.SLOT_STORAGE + maxSize);
        accessible = null;
        readBufferFromNBT(tagCompound);

        if (isServer()) {
            updateStackCount();
        }
    }

    private void readBufferFromItemNBT(NBTTagCompound tagCompound) {
        NBTTagList bufferTagList = tagCompound.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0 ; i < bufferTagList.tagCount() ; i++) {
            NBTTagCompound nbtTagCompound = bufferTagList.getCompoundTagAt(i);
            inventoryHelper.setStackInSlot(i+ModularStorageContainer.SLOT_STORAGE, ItemStack.loadItemStackFromNBT(nbtTagCompound));
        }
    }

    private void readBufferFromNBT(NBTTagCompound tagCompound) {
        NBTTagList bufferTagList = tagCompound.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        if (tagCompound.hasKey("SlotStorage")) {
            // This is a new TE with separate NBT tags for the three special slots.
            for (int i = 0 ; i < bufferTagList.tagCount() ; i++) {
                NBTTagCompound nbtTagCompound = bufferTagList.getCompoundTagAt(i);
                inventoryHelper.setStackInSlot(i+ModularStorageContainer.SLOT_STORAGE, ItemStack.loadItemStackFromNBT(nbtTagCompound));
            }
            inventoryHelper.setStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE, ItemStack.loadItemStackFromNBT(tagCompound.getCompoundTag("SlotStorage")));
            inventoryHelper.setStackInSlot(ModularStorageContainer.SLOT_TYPE_MODULE, ItemStack.loadItemStackFromNBT(tagCompound.getCompoundTag("SlotType")));
            inventoryHelper.setStackInSlot(ModularStorageContainer.SLOT_FILTER_MODULE, ItemStack.loadItemStackFromNBT(tagCompound.getCompoundTag("SlotFilter")));
        } else {
            // This is an old TE so we have to convert this to the new format.
            int index = 0;
            for (int i = 0 ; i < bufferTagList.tagCount() ; i++) {
                NBTTagCompound nbtTagCompound = bufferTagList.getCompoundTagAt(i);
                inventoryHelper.setStackInSlot(index, ItemStack.loadItemStackFromNBT(nbtTagCompound));
                index++;
                if (index == ModularStorageContainer.SLOT_FILTER_MODULE) {
                    index++;    // Skip this slot since this TE will not have that.
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tagCompound) {
        super.writeToNBT(tagCompound);
        return tagCompound;
    }

    @Override
    public void writeRestorableToNBT(NBTTagCompound tagCompound) {
        super.writeRestorableToNBT(tagCompound);
        writeBufferToNBT(tagCompound);
        writeSlot(tagCompound, ModularStorageContainer.SLOT_STORAGE_MODULE, "SlotStorage");
        writeSlot(tagCompound, ModularStorageContainer.SLOT_TYPE_MODULE, "SlotType");
        writeSlot(tagCompound, ModularStorageContainer.SLOT_FILTER_MODULE, "SlotFilter");
        tagCompound.setInteger("numStacks", numStacks);
        tagCompound.setInteger("maxSize", maxSize);
        tagCompound.setInteger("remoteId", remoteId);
        tagCompound.setString("sortMode", sortMode);
        tagCompound.setString("viewMode", viewMode);
        tagCompound.setBoolean("groupMode", groupMode);
        tagCompound.setString("filter", filter);
        tagCompound.setInteger("version", version);
    }

    private void writeSlot(NBTTagCompound tagCompound, int index, String name) {
        NBTTagCompound nbtTagCompound = new NBTTagCompound();
        ItemStack stack = inventoryHelper.getStackInSlot(index);
        if (stack != null) {
            stack.writeToNBT(nbtTagCompound);
        }
        tagCompound.setTag(name, nbtTagCompound);
    }

    private void writeBufferToNBT(NBTTagCompound tagCompound) {
        // If sendToClient is true we have to send dummy information to the client
        // so that it can remotely open gui's.
        boolean sendToClient = isServer() && isRemote();

        NBTTagList bufferTagList = new NBTTagList();
        if (sendToClient) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity != null) {
                ItemStack[] slots = storageTileEntity.findStacksForId(remoteId);
                if (slots != null) {
                    for (ItemStack stack : slots) {
                        NBTTagCompound nbtTagCompound = new NBTTagCompound();
                        if (stack != null) {
                            stack.writeToNBT(nbtTagCompound);
                        }
                        bufferTagList.appendTag(nbtTagCompound);
                    }
                }
            }
        } else {
            for (int i = ModularStorageContainer.SLOT_STORAGE; i < inventoryHelper.getCount(); i++) {
                ItemStack stack = inventoryHelper.getStackInSlot(i);
                NBTTagCompound nbtTagCompound = new NBTTagCompound();
                if (stack != null) {
                    stack.writeToNBT(nbtTagCompound);
                }
                bufferTagList.appendTag(nbtTagCompound);
            }
        }
        tagCompound.setTag("Items", bufferTagList);
    }

    private int writeBufferToItemNBT(NBTTagCompound tagCompound) {
        int cnt = 0;
        NBTTagList bufferTagList = new NBTTagList();
        for (int i = ModularStorageContainer.SLOT_STORAGE; i < inventoryHelper.getCount(); i++) {
            ItemStack stack = inventoryHelper.getStackInSlot(i);
            NBTTagCompound nbtTagCompound = new NBTTagCompound();
            if (stack != null) {
                stack.writeToNBT(nbtTagCompound);
                if (stack.stackSize > 0) {
                    cnt++;
                }
            }
            bufferTagList.appendTag(nbtTagCompound);
        }
        tagCompound.setTag("Items", bufferTagList);
        return cnt;
    }

    @Override
    public boolean execute(EntityPlayerMP playerMP, String command, Map<String, Argument> args) {
        boolean rc = super.execute(playerMP, command, args);
        if (rc) {
            return true;
        }
        if (CMD_SETTINGS.equals(command)) {
            setFilter(args.get("filter").getString());
            setViewMode(args.get("viewMode").getString());
            setSortMode(args.get("sortMode").getString());
            setGroupMode(args.get("groupMode").getBoolean());
            IBlockState state = worldObj.getBlockState(getPos());
            worldObj.notifyBlockUpdate(getPos(), state, state, 3);
            return true;
        } else if (CMD_COMPACT.equals(command)) {
            compact();
            return true;
        } else if (CMD_CYCLE.equals(command)) {
            cycle();
            return true;
        }
        return false;
    }

    private void cycle() {
        if (isRemote()) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return;
            }
            remoteId = storageTileEntity.cycle(remoteId);
            getStackInSlot(ModularStorageContainer.SLOT_STORAGE_MODULE).getTagCompound().setInteger("id", remoteId);
            markDirtyClient();
        }
    }

    private void compact() {
        if (isRemote()) {
            RemoteStorageTileEntity storageTileEntity = getRemoteStorage(remoteId);
            if (storageTileEntity == null) {
                return;
            }
            storageTileEntity.compact(remoteId);
        } else {
            InventoryHelper.compactStacks(inventoryHelper, ModularStorageContainer.SLOT_STORAGE, maxSize);
        }

        updateStackCount();
        markDirtyClient();
    }

    @Override
    public int getVersion() {
        return version;
    }

    IItemHandler invHandler = new CustomSidedInvWrapper(this);

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.util.EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) invHandler;
        }
        return super.getCapability(capability, facing);
    }
}
