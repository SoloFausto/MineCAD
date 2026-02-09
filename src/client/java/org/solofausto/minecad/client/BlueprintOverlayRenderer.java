package org.solofausto.minecad.client;

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
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.joml.Matrix4f;
import org.solofausto.minecad.Minecad;
import org.solofausto.minecad.blueprint.BlueprintItemData;
import org.solofausto.minecad.blueprint.BlueprintManager;
import org.solofausto.minecad.blueprint.BlueprintOrigin;
import org.solofausto.minecad.blueprint.BlueprintSession;

public final class BlueprintOverlayRenderer {
    private static final float OVERLAY_ALPHA = 0.35f;
    private static final double OVERLAY_OFFSET = 0.008;
    private static final double OVERLAY_EXTENT = 1024.0;

    private BlueprintOverlayRenderer() {
    }

    public static void register() {
        WorldRenderEvents.END_MAIN.register(BlueprintOverlayRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        var player = client.player;
        if (!isHoldingSketchTools(player)) {
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

        renderFaceOverlay(context, session.getOrigin());
    }

    private static void renderFaceOverlay(WorldRenderContext context, BlueprintOrigin origin) {
        BlockPos pos = origin.blockPos();
        Direction planeFace = origin.face();
        Direction renderFace = (planeFace == Direction.UP || planeFace == Direction.DOWN)
                ? Direction.DOWN
                : planeFace;
        Vec3d[] corners = getFaceCorners(pos, renderFace, OVERLAY_OFFSET);

        MatrixStack matrices = context.matrices();
        Vec3d cameraPos = clientCameraPos();
        if (isBackfaceCulled(pos, renderFace, cameraPos)) {
            return;
        }
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        int alpha = Math.round(255 * OVERLAY_ALPHA);
        emitQuad(buffer, matrix, corners, cameraPos, alpha);

        BuiltBuffer built = buffer.end();
        RenderLayers.debugQuads().draw(built);
        built.close();
    }

    private static Vec3d clientCameraPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return Vec3d.ZERO;
        }
        return client.gameRenderer.getCamera().getCameraPos();
    }

    private static boolean isHoldingSketchTools(net.minecraft.entity.player.PlayerEntity player) {
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

    private static void emitQuad(BufferBuilder buffer, Matrix4f matrix, Vec3d[] corners, Vec3d cameraPos, int alpha) {
        for (Vec3d corner : corners) {
            float x = (float) (corner.x - cameraPos.x);
            float y = (float) (corner.y - cameraPos.y);
            float z = (float) (corner.z - cameraPos.z);
            buffer.vertex(matrix, x, y, z)
                    .color(255, 255, 255, alpha);
        }
    }

    private static Vec3d[] getFaceCorners(BlockPos pos, Direction face, double offset) {
        double x0 = pos.getX();
        double y0 = pos.getY();
        double z0 = pos.getZ();
        double x1 = x0 + 1.0;
        double y1 = y0 + 1.0;
        double z1 = z0 + 1.0;

        double min = -OVERLAY_EXTENT;
        double max = OVERLAY_EXTENT;

        return switch (face) {
            case NORTH -> new Vec3d[] {
                    new Vec3d(min, min, z0 - offset),
                    new Vec3d(max, min, z0 - offset),
                    new Vec3d(max, max, z0 - offset),
                    new Vec3d(min, max, z0 - offset)
            };
            case SOUTH -> new Vec3d[] {
                    new Vec3d(max, min, z1 + offset),
                    new Vec3d(min, min, z1 + offset),
                    new Vec3d(min, max, z1 + offset),
                    new Vec3d(max, max, z1 + offset)
            };
            case EAST -> new Vec3d[] {
                    new Vec3d(x1 + offset, min, max),
                    new Vec3d(x1 + offset, min, min),
                    new Vec3d(x1 + offset, max, min),
                    new Vec3d(x1 + offset, max, max)
            };
            case WEST -> new Vec3d[] {
                    new Vec3d(x0 - offset, min, min),
                    new Vec3d(x0 - offset, min, max),
                    new Vec3d(x0 - offset, max, max),
                    new Vec3d(x0 - offset, max, min)
            };
            case UP -> new Vec3d[] {
                    new Vec3d(min, y1 + offset, min),
                    new Vec3d(max, y1 + offset, min),
                    new Vec3d(max, y1 + offset, max),
                    new Vec3d(min, y1 + offset, max)
            };
            case DOWN -> new Vec3d[] {
                    new Vec3d(min, y0 - offset, max),
                    new Vec3d(max, y0 - offset, max),
                    new Vec3d(max, y0 - offset, min),
                    new Vec3d(min, y0 - offset, min)
            };
        };
    }

    private static boolean isBackfaceCulled(BlockPos pos, Direction face, Vec3d cameraPos) {
        Vec3d planePoint = switch (face) {
            case NORTH -> new Vec3d(pos.getX(), pos.getY(), pos.getZ());
            case SOUTH -> new Vec3d(pos.getX(), pos.getY(), pos.getZ() + 1);
            case WEST -> new Vec3d(pos.getX(), pos.getY(), pos.getZ());
            case EAST -> new Vec3d(pos.getX() + 1, pos.getY(), pos.getZ());
            case DOWN -> new Vec3d(pos.getX(), pos.getY(), pos.getZ());
            case UP -> new Vec3d(pos.getX(), pos.getY() + 1, pos.getZ());
        };

        Vec3d normal = switch (face) {
            case NORTH -> new Vec3d(0, 0, -1);
            case SOUTH -> new Vec3d(0, 0, 1);
            case WEST -> new Vec3d(-1, 0, 0);
            case EAST -> new Vec3d(1, 0, 0);
            case DOWN -> new Vec3d(0, -1, 0);
            case UP -> new Vec3d(0, 1, 0);
        };

        return cameraPos.subtract(planePoint).dotProduct(normal) <= 0.0;
    }
}
