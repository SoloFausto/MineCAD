package org.solofausto.minecad.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import org.joml.Matrix4f;
import org.solofausto.minecad.blueprint.BlueprintGeometry;
import org.solofausto.minecad.blueprint.BlueprintItemData;
import org.solofausto.minecad.blueprint.BlueprintManager;
import org.solofausto.minecad.blueprint.BlueprintLine;
import org.solofausto.minecad.blueprint.BlueprintSession;
import org.solofausto.minecad.Minecad;

public abstract class SketchToolBase {
    private static final float TARGET_ALPHA = 0.7f;
    private static final float POINT_ALPHA = 0.75f;
    private static final float LINE_ALPHA = 0.65f;
    private static final double TARGET_OFFSET = 0.003;

    protected SketchToolBase() {
    }

    public static void register() {
        WorldRenderEvents.END_MAIN.register(SketchToolBase::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        var player = client.player;
        if (!isHoldingSketchTool(client)) {
            return;
        }

        BlueprintSession session = BlueprintManager.getInstance()
                .getOrCreate(player.getUuid());
        ItemStack activeBlueprint = getHeldBlueprintTool(player);
        if (activeBlueprint.isEmpty()) {
            activeBlueprint = findBlueprintStack(player, session.getCurrentBlueprintId());
        }

        if (activeBlueprint.isEmpty() || !BlueprintItemData.loadFromItem(activeBlueprint, session)) {
            return;
        }

        if (session.getOrigin() == null) {
            return;
        }

        Vec3d cameraPos = clientCameraPos();
        Vec3d direction = player.getRotationVec(1.0f);
        Vec3d hit = BlueprintGeometry.intersectRayWithPlane(cameraPos, direction, session.getOrigin());
        if (hit == null) {
            return;
        }

        renderPoints(context, session, cameraPos);
        renderLines(context, session, cameraPos);

        BlockPos targetPos = BlockPos.ofFloored(hit);
        renderTargetBlock(context, targetPos);
    }

    private static boolean isHoldingSketchTool(MinecraftClient client) {
        var player = client.player;
        if (player == null) {
            return false;
        }
        return player.getMainHandStack().isOf(Minecad.SKETCH_TOOL)
                || player.getOffHandStack().isOf(Minecad.SKETCH_TOOL)
                || player.getMainHandStack().isOf(Minecad.LINE_TOOL)
                || player.getOffHandStack().isOf(Minecad.LINE_TOOL)
                || player.getMainHandStack().isOf(Minecad.BLUEPRINT_TOOL)
                || player.getOffHandStack().isOf(Minecad.BLUEPRINT_TOOL);
    }

    private static ItemStack getHeldBlueprintTool(net.minecraft.entity.player.PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.isOf(Minecad.BLUEPRINT_TOOL)) {
            return main;
        }
        ItemStack off = player.getOffHandStack();
        if (off.isOf(Minecad.BLUEPRINT_TOOL)) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findBlueprintStack(net.minecraft.entity.player.PlayerEntity player,
            java.util.UUID blueprintId) {
        ItemStack main = player.getMainHandStack();
        if (main.isOf(Minecad.BLUEPRINT_TOOL) && matchesBlueprint(main, blueprintId)) {
            return main;
        }

        ItemStack off = player.getOffHandStack();
        if (off.isOf(Minecad.BLUEPRINT_TOOL) && matchesBlueprint(off, blueprintId)) {
            return off;
        }

        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.getStack(i);
            if (stack.isOf(Minecad.BLUEPRINT_TOOL) && matchesBlueprint(stack, blueprintId)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }

    private static boolean matchesBlueprint(ItemStack stack, java.util.UUID blueprintId) {
        if (blueprintId == null) {
            return true;
        }
        java.util.UUID stackId = BlueprintItemData.getBlueprintId(stack);
        return blueprintId.equals(stackId);
    }

    private static void renderTargetBlock(WorldRenderContext context, BlockPos targetPos) {
        MatrixStack matrices = context.matrices();
        Vec3d cameraPos = clientCameraPos();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        int alpha = Math.round(255 * TARGET_ALPHA);
        emitCube(buffer, matrix, targetPos, cameraPos, alpha, 255, 0, 0);

        BuiltBuffer built = buffer.end();
        RenderLayers.debugQuads().draw(built);
        built.close();
    }

    private static void emitQuad(BufferBuilder buffer, Matrix4f matrix, Vec3d[] corners, Vec3d cameraPos, int alpha,
            int red, int green, int blue) {
        for (Vec3d corner : corners) {
            float x = (float) (corner.x - cameraPos.x);
            float y = (float) (corner.y - cameraPos.y);
            float z = (float) (corner.z - cameraPos.z);
            buffer.vertex(matrix, x, y, z)
                    .color(red, green, blue, alpha);
        }
    }

    private static void renderPoints(WorldRenderContext context, BlueprintSession session, Vec3d cameraPos) {
        if (session.getPoints().isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrices();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        int alpha = Math.round(255 * POINT_ALPHA);

        for (var point : session.getPoints()) {
            Vec3d world = BlueprintGeometry.toWorldCoords(point, session.getOrigin());
            BlockPos pos = BlockPos.ofFloored(world);
            emitCube(buffer, matrix, pos, cameraPos, alpha, 0, 120, 255);
        }

        BuiltBuffer built = buffer.end();
        RenderLayers.debugQuads().draw(built);
        built.close();
    }

    private static void renderLines(WorldRenderContext context, BlueprintSession session, Vec3d cameraPos) {
        if (session.getLines().isEmpty()) {
            return;
        }

        MatrixStack matrices = context.matrices();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        int alpha = Math.round(255 * LINE_ALPHA);

        for (BlueprintLine line : session.getLines()) {
            Vec3d startWorld = BlueprintGeometry.toWorldCoords(line.start(), session.getOrigin());
            Vec3d endWorld = BlueprintGeometry.toWorldCoords(line.end(), session.getOrigin());
            BlockPos start = BlockPos.ofFloored(startWorld);
            BlockPos end = BlockPos.ofFloored(endWorld);
            for (BlockPos pos : getLineBlocks(start, end)) {
                emitCube(buffer, matrix, pos, cameraPos, alpha, 0, 255, 0);
            }
        }

        BuiltBuffer built = buffer.end();
        RenderLayers.debugQuads().draw(built);
        built.close();
    }

    private static List<BlockPos> getLineBlocks(BlockPos start, BlockPos end) {
        List<BlockPos> blocks = new ArrayList<>();
        int x1 = start.getX();
        int y1 = start.getY();
        int z1 = start.getZ();
        int x2 = end.getX();
        int y2 = end.getY();
        int z2 = end.getZ();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int xs = x2 > x1 ? 1 : -1;
        int ys = y2 > y1 ? 1 : -1;
        int zs = z2 > z1 ? 1 : -1;

        blocks.add(new BlockPos(x1, y1, z1));

        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx;
            int p2 = 2 * dz - dx;
            while (x1 != x2) {
                x1 += xs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dx;
                }
                p1 += 2 * dy;
                p2 += 2 * dz;
                blocks.add(new BlockPos(x1, y1, z1));
            }
        } else if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy;
            int p2 = 2 * dz - dy;
            while (y1 != y2) {
                y1 += ys;
                if (p1 >= 0) {
                    x1 += xs;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dy;
                }
                p1 += 2 * dx;
                p2 += 2 * dz;
                blocks.add(new BlockPos(x1, y1, z1));
            }
        } else {
            int p1 = 2 * dy - dz;
            int p2 = 2 * dx - dz;
            while (z1 != z2) {
                z1 += zs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dz;
                }
                if (p2 >= 0) {
                    x1 += xs;
                    p2 -= 2 * dz;
                }
                p1 += 2 * dy;
                p2 += 2 * dx;
                blocks.add(new BlockPos(x1, y1, z1));
            }
        }

        return blocks;
    }

    private static void emitCube(BufferBuilder buffer, Matrix4f matrix, BlockPos pos, Vec3d cameraPos, int alpha,
            int red, int green, int blue) {
        double x0 = pos.getX() - TARGET_OFFSET;
        double y0 = pos.getY() - TARGET_OFFSET;
        double z0 = pos.getZ() - TARGET_OFFSET;
        double x1 = pos.getX() + 1.0 + TARGET_OFFSET;
        double y1 = pos.getY() + 1.0 + TARGET_OFFSET;
        double z1 = pos.getZ() + 1.0 + TARGET_OFFSET;

        Vec3d v000 = new Vec3d(x0, y0, z0);
        Vec3d v001 = new Vec3d(x0, y0, z1);
        Vec3d v010 = new Vec3d(x0, y1, z0);
        Vec3d v011 = new Vec3d(x0, y1, z1);
        Vec3d v100 = new Vec3d(x1, y0, z0);
        Vec3d v101 = new Vec3d(x1, y0, z1);
        Vec3d v110 = new Vec3d(x1, y1, z0);
        Vec3d v111 = new Vec3d(x1, y1, z1);

        emitQuad(buffer, matrix, new Vec3d[] { v001, v101, v111, v011 }, cameraPos, alpha, red, green, blue); // south
        emitQuad(buffer, matrix, new Vec3d[] { v100, v000, v010, v110 }, cameraPos, alpha, red, green, blue); // north
        emitQuad(buffer, matrix, new Vec3d[] { v101, v100, v110, v111 }, cameraPos, alpha, red, green, blue); // east
        emitQuad(buffer, matrix, new Vec3d[] { v000, v001, v011, v010 }, cameraPos, alpha, red, green, blue); // west
        emitQuad(buffer, matrix, new Vec3d[] { v010, v011, v111, v110 }, cameraPos, alpha, red, green, blue); // up
        emitQuad(buffer, matrix, new Vec3d[] { v000, v100, v101, v001 }, cameraPos, alpha, red, green, blue); // down
    }

    private static Vec3d clientCameraPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return Vec3d.ZERO;
        }
        return client.gameRenderer.getCamera().getCameraPos();
    }
}
