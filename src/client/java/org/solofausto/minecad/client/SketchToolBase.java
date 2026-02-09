package org.solofausto.minecad.client;

import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.ItemStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.joml.Matrix4f;
import org.solofausto.minecad.blueprint.BlueprintGeometry;
import org.solofausto.minecad.blueprint.BlueprintItemData;
import org.solofausto.minecad.blueprint.BlueprintManager;
import org.solofausto.minecad.blueprint.BlueprintSession;
import org.solofausto.minecad.Minecad;

public abstract class SketchToolBase {
    private static final float TARGET_ALPHA = 0.7f;
    private static final float POINT_ALPHA = 0.75f;
    private static final float LINE_ALPHA = 0.65f;
    private static final float REGION_ALPHA = 0.85f;
    private static final double TARGET_OFFSET = 0.003;
    private static final long BLINK_PERIOD_MS = 360L;

    private static int lastHoveredRegionIndex = -1;

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

        double maxDistanceSq = getRenderDistanceSq();
        Frustum frustum = context.worldRenderer().getCapturedFrustum();
        renderPoints(context, session, cameraPos, maxDistanceSq, frustum);
        renderLines(context, session, cameraPos, maxDistanceSq, frustum);

        BlockPos targetPos = BlockPos.ofFloored(hit);
        renderRegions(context, session, cameraPos, targetPos, maxDistanceSq, frustum);
        renderTargetBlock(context, targetPos, maxDistanceSq, frustum);
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

    private static void renderTargetBlock(WorldRenderContext context, BlockPos targetPos, double maxDistanceSq,
            Frustum frustum) {
        MatrixStack matrices = context.matrices();
        Vec3d cameraPos = clientCameraPos();
        if (cameraPos.squaredDistanceTo(Vec3d.ofCenter(targetPos)) > maxDistanceSq) {
            return;
        }
        if (frustum != null && !frustum.isVisible(new Box(targetPos))) {
            return;
        }
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

    private static void renderPoints(WorldRenderContext context, BlueprintSession session, Vec3d cameraPos,
            double maxDistanceSq, Frustum frustum) {
        if (session.getPoints().isEmpty()) {
            return;
        }

        if (isBackfaceCulled(session.getOrigin(), cameraPos)) {
            return;
        }

        MatrixStack matrices = context.matrices();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        int alpha = Math.round(255 * POINT_ALPHA);
        boolean emitted = false;

        List<BlockPos> points = session.getCachedPointBlocks();
        for (BlockPos pos : points) {
            if (cameraPos.squaredDistanceTo(Vec3d.ofCenter(pos)) > maxDistanceSq) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(new Box(pos))) {
                continue;
            }
            emitCube(buffer, matrix, pos, cameraPos, alpha, 0, 120, 255);
            emitted = true;
        }

        if (!emitted) {
            return;
        }

        BuiltBuffer built = buffer.end();
        RenderLayers.debugQuads().draw(built);
        built.close();
    }

    private static void renderLines(WorldRenderContext context, BlueprintSession session, Vec3d cameraPos,
            double maxDistanceSq, Frustum frustum) {
        if (session.getLines().isEmpty()) {
            return;
        }

        if (isBackfaceCulled(session.getOrigin(), cameraPos)) {
            return;
        }

        MatrixStack matrices = context.matrices();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);
        int alpha = Math.round(255 * LINE_ALPHA);
        boolean emitted = false;

        List<BlueprintSession.LineBlockCache> lines = session.getCachedLineBlocks();
        for (BlueprintSession.LineBlockCache line : lines) {
            if (frustum != null && !frustum.isVisible(line.bounds())) {
                continue;
            }
            for (BlockPos pos : line.blocks()) {
                if (cameraPos.squaredDistanceTo(Vec3d.ofCenter(pos)) > maxDistanceSq) {
                    continue;
                }
                if (frustum != null && !frustum.isVisible(new Box(pos))) {
                    continue;
                }
                emitCube(buffer, matrix, pos, cameraPos, alpha, 0, 255, 0);
                emitted = true;
            }
        }

        if (!emitted) {
            return;
        }

        BuiltBuffer built = buffer.end();
        RenderLayers.debugQuads().draw(built);
        built.close();
    }

    private static void renderRegions(WorldRenderContext context, BlueprintSession session, Vec3d cameraPos,
            BlockPos targetPos, double maxDistanceSq, Frustum frustum) {
        if (session.getLines().isEmpty() || session.getOrigin() == null) {
            return;
        }

        if (isBackfaceCulled(session.getOrigin(), cameraPos)) {
            return;
        }

        List<Region> regions = detectRegions(session);
        if (regions.isEmpty()) {
            return;
        }

        PlanePos targetPlane = worldToPlaneCoords(targetPos, session.getOrigin());
        int hoveredIndex = -1;
        for (int i = 0; i < regions.size(); i++) {
            if (regions.get(i).planeCells.contains(targetPlane)) {
                hoveredIndex = i;
                break;
            }
        }

        if (hoveredIndex < 0) {
            lastHoveredRegionIndex = -1;
            return;
        }

        lastHoveredRegionIndex = hoveredIndex;
        Region region = regions.get(hoveredIndex);
        if (frustum != null && !frustum.isVisible(region.bounds)) {
            return;
        }
        float blink = blinkFactor();
        int alpha = Math.round(255 * REGION_ALPHA * blink);
        if (alpha <= 0) {
            return;
        }

        MatrixStack matrices = context.matrices();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,
                VertexFormats.POSITION_COLOR);

        boolean emitted = false;
        for (BlockPos pos : region.worldBlocks) {
            if (cameraPos.squaredDistanceTo(Vec3d.ofCenter(pos)) > maxDistanceSq) {
                continue;
            }
            if (frustum != null && !frustum.isVisible(new Box(pos))) {
                continue;
            }
            emitCube(buffer, matrix, pos, cameraPos, alpha, 255, 215, 0);
            emitted = true;
        }

        if (!emitted) {
            return;
        }

        BuiltBuffer built = buffer.end();
        RenderLayers.debugQuads().draw(built);
        built.close();
    }

    private static List<Region> detectRegions(BlueprintSession session) {
        if (session.getOrigin() == null) {
            return List.of();
        }

        Set<PlanePos> wallCells = new HashSet<>();
        int minU = Integer.MAX_VALUE;
        int maxU = Integer.MIN_VALUE;
        int minV = Integer.MAX_VALUE;
        int maxV = Integer.MIN_VALUE;

        List<BlueprintSession.LineBlockCache> lines = session.getCachedLineBlocks();
        for (BlueprintSession.LineBlockCache line : lines) {
            for (BlockPos pos : line.blocks()) {
                PlanePos planePos = worldToPlaneCoords(pos, session.getOrigin());
                wallCells.add(planePos);
                minU = Math.min(minU, planePos.u());
                maxU = Math.max(maxU, planePos.u());
                minV = Math.min(minV, planePos.v());
                maxV = Math.max(maxV, planePos.v());
            }
        }

        if (wallCells.isEmpty()) {
            return List.of();
        }

        minU -= 1;
        minV -= 1;
        maxU += 1;
        maxV += 1;

        Set<PlanePos> visited = new HashSet<>();
        ArrayDeque<PlanePos> queue = new ArrayDeque<>();

        for (int u = minU; u <= maxU; u++) {
            enqueueIfOpen(queue, visited, wallCells, new PlanePos(u, minV));
            enqueueIfOpen(queue, visited, wallCells, new PlanePos(u, maxV));
        }
        for (int v = minV; v <= maxV; v++) {
            enqueueIfOpen(queue, visited, wallCells, new PlanePos(minU, v));
            enqueueIfOpen(queue, visited, wallCells, new PlanePos(maxU, v));
        }

        flood(queue, visited, wallCells, minU, maxU, minV, maxV);

        List<Region> regions = new ArrayList<>();
        for (int u = minU + 1; u <= maxU - 1; u++) {
            for (int v = minV + 1; v <= maxV - 1; v++) {
                PlanePos pos = new PlanePos(u, v);
                if (wallCells.contains(pos) || visited.contains(pos)) {
                    continue;
                }
                Region region = floodRegion(pos, wallCells, visited, session.getOrigin(), minU, maxU, minV, maxV);
                if (!region.worldBlocks.isEmpty()) {
                    regions.add(region);
                }
            }
        }

        return regions;
    }

    private static void enqueueIfOpen(ArrayDeque<PlanePos> queue, Set<PlanePos> visited, Set<PlanePos> walls,
            PlanePos pos) {
        if (!walls.contains(pos) && visited.add(pos)) {
            queue.add(pos);
        }
    }

    private static void flood(ArrayDeque<PlanePos> queue, Set<PlanePos> visited, Set<PlanePos> walls,
            int minU, int maxU, int minV, int maxV) {
        while (!queue.isEmpty()) {
            PlanePos current = queue.poll();
            for (PlanePos next : neighbors(current)) {
                if (next.u() < minU || next.u() > maxU || next.v() < minV || next.v() > maxV) {
                    continue;
                }
                if (walls.contains(next) || visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }
    }

    private static Region floodRegion(PlanePos start, Set<PlanePos> walls, Set<PlanePos> visited,
            org.solofausto.minecad.blueprint.BlueprintOrigin origin,
            int minU, int maxU, int minV, int maxV) {
        ArrayDeque<PlanePos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);

        Set<PlanePos> planeCells = new HashSet<>();
        List<BlockPos> worldBlocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        while (!queue.isEmpty()) {
            PlanePos current = queue.poll();
            planeCells.add(current);
            BlockPos worldBlock = planeToWorldBlock(current, origin);
            worldBlocks.add(worldBlock);
            minX = Math.min(minX, worldBlock.getX());
            minY = Math.min(minY, worldBlock.getY());
            minZ = Math.min(minZ, worldBlock.getZ());
            maxX = Math.max(maxX, worldBlock.getX());
            maxY = Math.max(maxY, worldBlock.getY());
            maxZ = Math.max(maxZ, worldBlock.getZ());

            for (PlanePos next : neighbors(current)) {
                if (next.u() < minU || next.u() > maxU || next.v() < minV || next.v() > maxV) {
                    continue;
                }
                if (walls.contains(next) || visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }

        Box bounds = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        return new Region(planeCells, worldBlocks, bounds);
    }

    private static List<PlanePos> neighbors(PlanePos pos) {
        return List.of(
                new PlanePos(pos.u() + 1, pos.v()),
                new PlanePos(pos.u() - 1, pos.v()),
                new PlanePos(pos.u(), pos.v() + 1),
                new PlanePos(pos.u(), pos.v() - 1));
    }

    private static PlanePos worldToPlaneCoords(BlockPos pos, org.solofausto.minecad.blueprint.BlueprintOrigin origin) {
        BlockPos originPos = origin.blockPos();
        return switch (origin.face()) {
            case NORTH, SOUTH -> new PlanePos(pos.getX() - originPos.getX(), pos.getY() - originPos.getY());
            case EAST, WEST -> new PlanePos(pos.getZ() - originPos.getZ(), pos.getY() - originPos.getY());
            case UP, DOWN -> new PlanePos(pos.getX() - originPos.getX(), pos.getZ() - originPos.getZ());
        };
    }

    private static BlockPos planeToWorldBlock(PlanePos pos, org.solofausto.minecad.blueprint.BlueprintOrigin origin) {
        BlockPos originPos = origin.blockPos();
        return switch (origin.face()) {
            case NORTH -> new BlockPos(originPos.getX() + pos.u(), originPos.getY() + pos.v(), originPos.getZ());
            case SOUTH -> new BlockPos(originPos.getX() + pos.u(), originPos.getY() + pos.v(), originPos.getZ() + 1);
            case EAST -> new BlockPos(originPos.getX() + 1, originPos.getY() + pos.v(), originPos.getZ() + pos.u());
            case WEST -> new BlockPos(originPos.getX(), originPos.getY() + pos.v(), originPos.getZ() + pos.u());
            case UP -> new BlockPos(originPos.getX() + pos.u(), originPos.getY() + 1, originPos.getZ() + pos.v());
            case DOWN -> new BlockPos(originPos.getX() + pos.u(), originPos.getY(), originPos.getZ() + pos.v());
        };
    }

    private static float blinkFactor() {
        long time = System.currentTimeMillis();
        double phase = (time % BLINK_PERIOD_MS) / (double) BLINK_PERIOD_MS;
        return (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2.0));
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

    private static boolean isBackfaceCulled(org.solofausto.minecad.blueprint.BlueprintOrigin origin,
            Vec3d cameraPos) {
        if (origin == null) {
            return false;
        }

        BlockPos pos = origin.blockPos();
        Direction face = origin.face();
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

    private static Vec3d clientCameraPos() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.gameRenderer == null || client.gameRenderer.getCamera() == null) {
            return Vec3d.ZERO;
        }
        return client.gameRenderer.getCamera().getCameraPos();
    }

    private static double getRenderDistanceSq() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) {
            return Double.POSITIVE_INFINITY;
        }
        int viewDistanceChunks = client.options.getViewDistance().getValue();
        double maxDistance = viewDistanceChunks * 16.0;
        return maxDistance * maxDistance;
    }

    private record PlanePos(int u, int v) {
    }

    private static final class Region {
        private final Set<PlanePos> planeCells;
        private final List<BlockPos> worldBlocks;
        private final Box bounds;

        private Region(Set<PlanePos> planeCells, List<BlockPos> worldBlocks, Box bounds) {
            this.planeCells = planeCells;
            this.worldBlocks = worldBlocks;
            this.bounds = bounds;
        }
    }
}
