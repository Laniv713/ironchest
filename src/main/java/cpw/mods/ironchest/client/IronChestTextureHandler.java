package cpw.mods.ironchest.client;

import java.util.Random;

import cpw.mods.ironchest.BlockIronChest;
import cpw.mods.ironchest.IronChestType;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityDiggingFX;
import net.minecraft.client.renderer.block.model.ModelManager;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class IronChestTextureHandler
{
    public static void addHitEffects(World world, BlockPos pos, EnumFacing side)
    {
        IBlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        state = block.getActualState(state, world, pos);
        Random rand = new Random();
        IronChestType type = state.getValue(BlockIronChest.VARIANT_PROP);
        ModelManager modelmanager = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelManager();

        if (block.getRenderType(state) != EnumBlockRenderType.INVISIBLE)
        {
            int i = pos.getX();
            int j = pos.getY();
            int k = pos.getZ();
            float f = 0.1F;
            AxisAlignedBB bb = block.getBoundingBox(state, world, pos);

            double d0 = i + rand.nextDouble() * (bb.maxX - bb.minX - f * 2.0F) + f + bb.minX;
            double d1 = j + rand.nextDouble() * (bb.maxY - bb.minY - f * 2.0F) + f + bb.minY;
            double d2 = k + rand.nextDouble() * (bb.maxZ - bb.minZ - f * 2.0F) + f + bb.minZ;

            if (side == EnumFacing.DOWN)
            {
                d1 = j + bb.minY - f;
            }

            if (side == EnumFacing.UP)
            {
                d1 = j + bb.maxY + f;
            }

            if (side == EnumFacing.NORTH)
            {
                d2 = k + bb.minZ - f;
            }

            if (side == EnumFacing.SOUTH)
            {
                d2 = k + bb.maxZ + f;
            }

            if (side == EnumFacing.WEST)
            {
                d0 = i + bb.minX - f;
            }

            if (side == EnumFacing.EAST)
            {
                d0 = i + bb.maxX + f;
            }

            EntityDiggingFX fx = ((EntityDiggingFX) Minecraft.getMinecraft().effectRenderer.spawnEffectParticle(EnumParticleTypes.BLOCK_CRACK.getParticleID(),
                    d0, d1, d2, 0.0D, 0.0D, 0.0D, Block.getIdFromBlock(state.getBlock())));

            fx.setBlockPos(pos);
            fx.multiplyVelocity(0.2F);
            fx.multipleParticleScaleBy(0.6F);
            fx.setParticleTexture(modelmanager.getTextureMap().getAtlasSprite(type.getBreakTexture()));
        }
    }

    public static void addDestroyEffects(World world, BlockPos pos, IBlockState state)
    {
        state = state.getBlock().getActualState(state, world, pos);
        int i = 4;
        IronChestType type = state.getValue(BlockIronChest.VARIANT_PROP);
        ModelManager modelmanager = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelManager();

        for (int j = 0; j < i; ++j)
        {
            for (int k = 0; k < i; ++k)
            {
                for (int l = 0; l < i; ++l)
                {
                    double d0 = pos.getX() + (j + 0.5D) / i;
                    double d1 = pos.getY() + (k + 0.5D) / i;
                    double d2 = pos.getZ() + (l + 0.5D) / i;
                    EntityDiggingFX fx = ((EntityDiggingFX) Minecraft.getMinecraft().effectRenderer.spawnEffectParticle(
                            EnumParticleTypes.BLOCK_CRACK.getParticleID(), d0, d1, d2, d0 - pos.getX() - 0.5D, d1 - pos.getY() - 0.5D, d2 - pos.getZ() - 0.5D,
                            Block.getIdFromBlock(state.getBlock())));
                    fx.setBlockPos(pos);
                    fx.setParticleTexture(modelmanager.getTextureMap().getAtlasSprite(type.getBreakTexture()));
                }
            }
        }
    }
}
