package mcjty.rftools.blocks.powercell;

import mcjty.lib.container.GenericGuiContainer;
import mcjty.lib.network.clientinfo.PacketGetInfoFromServer;
import mcjty.rftools.RFTools;
import mcjty.rftools.blocks.GenericRFToolsBlock;
import mcjty.rftools.network.RFToolsMessages;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import java.text.DecimalFormat;
import java.util.List;

public class PowerCellBlock extends GenericRFToolsBlock<PowerCellTileEntity, PowerCellContainer> {

    public static final PropertyEnum<PowerCellTileEntity.Mode> NORTH = PropertyEnum.create("north", PowerCellTileEntity.Mode.class);
    public static final PropertyEnum<PowerCellTileEntity.Mode> SOUTH = PropertyEnum.create("south", PowerCellTileEntity.Mode.class);
    public static final PropertyEnum<PowerCellTileEntity.Mode> WEST = PropertyEnum.create("west", PowerCellTileEntity.Mode.class);
    public static final PropertyEnum<PowerCellTileEntity.Mode> EAST = PropertyEnum.create("east", PowerCellTileEntity.Mode.class);
    public static final PropertyEnum<PowerCellTileEntity.Mode> UP = PropertyEnum.create("up", PowerCellTileEntity.Mode.class);
    public static final PropertyEnum<PowerCellTileEntity.Mode> DOWN = PropertyEnum.create("down", PowerCellTileEntity.Mode.class);

    private static long lastTime = 0;

    public PowerCellBlock() {
        super(Material.iron, PowerCellTileEntity.class, PowerCellContainer.class, "powercell", true);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public Class<? extends GenericGuiContainer> getGuiClass() {
        return GuiPowerCell.class;
    }

    @Override
    public boolean hasNoRotation() {
        return true;
    }

    @Override
    public int getGuiID() {
        return RFTools.GUI_POWERCELL;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack itemStack, EntityPlayer player, List<String> list, boolean whatIsThis) {
        super.addInformation(itemStack, player, list, whatIsThis);

        NBTTagCompound tagCompound = itemStack.getTagCompound();
        if (tagCompound != null) {
            list.add(EnumChatFormatting.YELLOW + "Energy: " + tagCompound.getInteger("energy"));
        }

        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            list.add("This block can store power");
            list.add("Optionally in a big multi");
            list.add("dimensional structure");
        } else {
            list.add(EnumChatFormatting.WHITE + RFTools.SHIFT_MESSAGE);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        TileEntity tileEntity = accessor.getTileEntity();
        if (tileEntity instanceof PowerCellTileEntity) {
            PowerCellTileEntity powerCellTileEntity = (PowerCellTileEntity) tileEntity;
            int id = powerCellTileEntity.getNetworkId();
            if (id != -1) {
                currenttip.add(EnumChatFormatting.GREEN + "ID: " + new DecimalFormat("#.##").format(id));
                if (System.currentTimeMillis() - lastTime > 250) {
                    lastTime = System.currentTimeMillis();
                    RFToolsMessages.INSTANCE.sendToServer(new PacketGetInfoFromServer(RFTools.MODID, new PowerCellInfoPacketServer(id)));
                }
            } else {
                currenttip.add(EnumChatFormatting.GREEN + "Local storage!");
                PowerCellInfoPacketClient.tooltipEnergy = powerCellTileEntity.getEnergy();
                PowerCellInfoPacketClient.tooltipBlocks = 1;
            }
            currenttip.add(EnumChatFormatting.GREEN + "Energy: " + PowerCellInfoPacketClient.tooltipEnergy + "/" + (PowerCellInfoPacketClient.tooltipBlocks * PowerCellConfiguration.rfPerCell) + " RF");
        }
        return currenttip;
    }

    @Override
    protected boolean wrenchUse(World world, BlockPos pos, EnumFacing side, EntityPlayer player) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof PowerCellTileEntity) {
                PowerCellTileEntity powerCellTileEntity = (PowerCellTileEntity) te;
                powerCellTileEntity.toggleMode(side);
            }
        }
        return true;
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (stack.hasTagCompound() && !world.isRemote) {
            PowerCellTileEntity powerCellTileEntity = (PowerCellTileEntity) world.getTileEntity(pos);
            if (powerCellTileEntity != null) {
                int networkId = powerCellTileEntity.getNetworkId();
                if (networkId == -1) {
                    // No network, energy is already restored to the local block
                } else {
                    int energy = stack.getTagCompound().getInteger("energy");
                    PowerCellNetwork powerCellNetwork = PowerCellNetwork.getChannels(world);
                    PowerCellNetwork.Network network = powerCellNetwork.getChannel(networkId);
                    network.setEnergy(energy + network.getEnergy());
                    network.getBlocks().add(powerCellTileEntity.getGlobalPos());
                    powerCellNetwork.save(world);
                }
            }
        }
    }

    @Override
    public List<ItemStack> getDrops(IBlockAccess blockAccess, BlockPos pos, IBlockState state, int fortune) {
        World world = (World) blockAccess;
        List<ItemStack> drops = super.getDrops(world, pos, state, fortune);
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof PowerCellTileEntity) {
                PowerCellNetwork.Network network = ((PowerCellTileEntity) te).getNetwork();
                if (network != null) {
                    int energy = network.getEnergySingleBlock();
                    if (!drops.isEmpty()) {
                        NBTTagCompound tagCompound = drops.get(0).getTagCompound();
                        if (tagCompound == null) {
                            tagCompound = new NBTTagCompound();
                            drops.get(0).setTagCompound(tagCompound);
                        }
                        tagCompound.setInteger("energy", energy);
                    }
                }
            }
        }
        return drops;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof PowerCellTileEntity) {
                PowerCellTileEntity cellTileEntity = (PowerCellTileEntity) te;
                PowerCellNetwork.Network network = cellTileEntity.getNetwork();
                if (network != null) {
                    network.extractEnergySingleBlock();
                    network.getBlocks().remove(cellTileEntity.getGlobalPos());
                    PowerCellNetwork.getChannels(world).save(world);
                }
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        PowerCellTileEntity te = (PowerCellTileEntity) world.getTileEntity(pos);
        PowerCellTileEntity.Mode north = te.getMode(EnumFacing.NORTH);
        PowerCellTileEntity.Mode south = te.getMode(EnumFacing.SOUTH);
        PowerCellTileEntity.Mode west = te.getMode(EnumFacing.WEST);
        PowerCellTileEntity.Mode east = te.getMode(EnumFacing.EAST);
        PowerCellTileEntity.Mode up = te.getMode(EnumFacing.UP);
        PowerCellTileEntity.Mode down = te.getMode(EnumFacing.DOWN);
        return state.withProperty(NORTH, north).withProperty(SOUTH, south).withProperty(WEST, west).withProperty(EAST, east).withProperty(UP, up).withProperty(DOWN, down);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    protected BlockState createBlockState() {
        return new BlockState(this, NORTH, SOUTH, WEST, EAST, UP, DOWN);
    }
}
