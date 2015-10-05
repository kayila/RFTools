package mcjty.rftools.blocks.screens;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mcjty.rftools.RFTools;
import mcjty.rftools.blocks.screens.modulesclient.ClientScreenModule;
import mcjty.rftools.blocks.screens.network.PacketGetScreenData;
import mcjty.rftools.network.RFToolsMessages;
import mcjty.varia.Coordinate;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SideOnly(Side.CLIENT)
public class ScreenRenderer extends TileEntitySpecialRenderer {

    private static final ResourceLocation texture = new ResourceLocation(RFTools.MODID, "textures/blocks/screenFrame.png");
    private final ModelScreen screenModel = new ModelScreen(ScreenTileEntity.SIZE_NORMAL);
    private final ModelScreen screenModelLarge = new ModelScreen(ScreenTileEntity.SIZE_LARGE);
    private final ModelScreen screenModelHuge = new ModelScreen(ScreenTileEntity.SIZE_HUGE);

    @Override
    public void renderTileEntityAt(TileEntity tileEntity, double x, double y, double z, float f) {
        GL11.glPushMatrix();
        float f3;

        int meta = tileEntity.getBlockMetadata();
        f3 = 0.0F;

        if (meta == 2) {
            f3 = 180.0F;
        }

        if (meta == 4) {
            f3 = 90.0F;
        }

        if (meta == 5) {
            f3 = -90.0F;
        }

        GL11.glTranslatef((float) x + 0.5F, (float) y + 0.75F, (float) z + 0.5F);
        GL11.glRotatef(-f3, 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(0.0F, -0.2500F, -0.4375F);

        ScreenTileEntity screenTileEntity = (ScreenTileEntity) tileEntity;

        if (!screenTileEntity.isTransparent()) {
            GL11.glDisable(GL11.GL_LIGHTING);
            renderScreenBoard(screenTileEntity.getSize(), screenTileEntity.getColor());
        }

        if (screenTileEntity.isPowerOn()) {
            FontRenderer fontrenderer = this.func_147498_b();

            ClientScreenModule.TransformMode mode = ClientScreenModule.TransformMode.NONE;
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_LIGHTING);

            Map<Integer, Object[]> screenData = updateScreenData(screenTileEntity);

            List<ClientScreenModule> modules = screenTileEntity.getClientScreenModules();
            renderModules(fontrenderer, mode, modules, screenData, screenTileEntity.getSize());

//            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }

        GL11.glDepthMask(true);
        RenderHelper.disableStandardItemLighting();

        GL11.glPopMatrix();
    }

    private Map<Integer, Object[]> updateScreenData(ScreenTileEntity screenTileEntity) {
        long millis = System.currentTimeMillis();
        if ((millis - screenTileEntity.lastTime > 500) && screenTileEntity.isNeedsServerData()) {
            screenTileEntity.lastTime = millis;
            RFToolsMessages.INSTANCE.sendToServer(new PacketGetScreenData(screenTileEntity.xCoord, screenTileEntity.yCoord, screenTileEntity.zCoord, millis));
        }

        Map<Integer,Object[]> screenData = ScreenTileEntity.screenData.get(new Coordinate(screenTileEntity.xCoord, screenTileEntity.yCoord, screenTileEntity.zCoord));
        if (screenData == null) {
            screenData = Collections.EMPTY_MAP;
        }
        return screenData;
    }

    private void renderModules(FontRenderer fontrenderer, ClientScreenModule.TransformMode mode, List<ClientScreenModule> modules, Map<Integer, Object[]> screenData, int size) {
        float f3;
        float factor = size + 1.0f;
        int currenty = 7;
        int moduleIndex = 0;
        for (ClientScreenModule module : modules) {
            if (module != null) {
                int height = module.getHeight();
                // Check if this module has enough room
                if (currenty + height <= 124) {
                    if (module.getTransformMode() != mode) {
                        if (mode != ClientScreenModule.TransformMode.NONE) {
                            GL11.glPopMatrix();
                        }
                        GL11.glPushMatrix();
                        mode = module.getTransformMode();

                        switch (mode) {
                            case TEXT:
                                GL11.glTranslatef(-0.5F, 0.5F, 0.07F);
                                f3 = 0.0075F;
                                GL11.glScalef(f3 * factor, -f3 * factor, f3);
                                GL11.glNormal3f(0.0F, 0.0F, -1.0F);
                                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                                break;
                            case TEXTLARGE:
                                GL11.glTranslatef(-0.5F, 0.5F, 0.07F);
                                f3 = 0.0075F * 2;
                                GL11.glScalef(f3 * factor, -f3 * factor, f3);
                                GL11.glNormal3f(0.0F, 0.0F, -1.0F);
                                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                                break;
                            case ITEM:
                                break;
                            default:
                                break;
                        }
                    }

                    module.render(fontrenderer, currenty, screenData.get(moduleIndex), factor);
                    currenty += height;
                }
            }
            moduleIndex++;
        }

        if (mode != ClientScreenModule.TransformMode.NONE) {
            GL11.glPopMatrix();
        }
    }

    private void renderScreenBoard(int size, int color) {
        this.bindTexture(texture);
        GL11.glPushMatrix();
        GL11.glScalef(1, -1, -1);
        if (size == ScreenTileEntity.SIZE_HUGE) {
            this.screenModelHuge.render();
        } else if (size == ScreenTileEntity.SIZE_LARGE) {
            this.screenModelLarge.render();
        } else {
            this.screenModel.render();
        }

        GL11.glDepthMask(false);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setBrightness(240);
        tessellator.setColorRGBA_I(color, 255);
//        tessellator.setColorOpaque(0, 0, 0);
        float r;
        if (size == ScreenTileEntity.SIZE_HUGE) {
            r = 2.46f;
        } else if (size == ScreenTileEntity.SIZE_LARGE) {
            r = 1.46f;
        } else {
            r = .46f;
        }
        tessellator.addVertex(-.46f, r, -0.08f);
        tessellator.addVertex(r, r, -0.08f);
        tessellator.addVertex(r, -.46f, -0.08f);
        tessellator.addVertex(-.46f, -.46f, -0.08f);
        tessellator.draw();

        GL11.glPopMatrix();
    }
}
