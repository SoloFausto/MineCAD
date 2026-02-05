package org.solofausto.minecad.item;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.solofausto.minecad.Minecad;
import org.solofausto.minecad.blueprint.BlueprintGeometry;
import org.solofausto.minecad.blueprint.BlueprintItemData;
import org.solofausto.minecad.blueprint.BlueprintLine;
import org.solofausto.minecad.blueprint.BlueprintManager;
import org.solofausto.minecad.blueprint.BlueprintPoint;
import org.solofausto.minecad.blueprint.BlueprintSession;

public class LineToolItem extends Item {
    public LineToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity player, Hand hand) {
        if (player == null) {
            return ActionResult.PASS;
        }

        BlueprintSession session = BlueprintManager.getInstance().getOrCreate(player.getUuid());
        ItemStack blueprintStack = findBlueprintStack(player, session.getCurrentBlueprintId());
        if (blueprintStack.isEmpty()) {
            return ActionResult.PASS;
        }

        BlueprintItemData.ensureBlueprintId(blueprintStack, session);
        if (!BlueprintItemData.loadFromItem(blueprintStack, session)) {
            return ActionResult.PASS;
        }

        Vec3d cameraPos = player.getCameraPosVec(1.0f);
        Vec3d direction = player.getRotationVec(1.0f);
        Vec3d hit = BlueprintGeometry.intersectRayWithPlane(cameraPos, direction, session.getOrigin());
        if (hit == null) {
            return ActionResult.PASS;
        }

        BlueprintPoint clickPoint = BlueprintGeometry.projectToBlueprintCoords(hit, session.getOrigin());
        BlockPos targetBlock = BlockPos.ofFloored(BlueprintGeometry.toWorldCoords(clickPoint, session.getOrigin()));
        BlueprintPoint selectedPoint = findPointAtBlock(session, targetBlock);
        if (selectedPoint == null) {
            if (hasPointInBlock(session, targetBlock)) {
                return world.isClient() ? ActionResult.SUCCESS : ActionResult.CONSUME;
            }
            selectedPoint = clickPoint;
            session.addPoint(selectedPoint);
        }

        BlueprintPoint pending = session.getPendingLineStart();
        if (pending == null) {
            session.setPendingLineStart(selectedPoint);
            BlueprintItemData.writeToItem(blueprintStack, session);
            return world.isClient() ? ActionResult.SUCCESS : ActionResult.CONSUME;
        }

        if (isSameBlock(pending, selectedPoint, session)) {
            return world.isClient() ? ActionResult.SUCCESS : ActionResult.CONSUME;
        }

        if (!hasLine(session, pending, selectedPoint)) {
            session.addLine(new BlueprintLine(pending, selectedPoint));
        }
        session.setPendingLineStart(null);
        BlueprintItemData.writeToItem(blueprintStack, session);

        return world.isClient() ? ActionResult.SUCCESS : ActionResult.CONSUME;
    }

    private static ItemStack findBlueprintStack(PlayerEntity player, java.util.UUID blueprintId) {
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

    private static boolean hasPointInBlock(BlueprintSession session, BlockPos targetBlock) {
        for (BlueprintPoint existing : session.getPoints()) {
            BlockPos existingBlock = BlockPos.ofFloored(
                    BlueprintGeometry.toWorldCoords(existing, session.getOrigin()));
            if (existingBlock.equals(targetBlock)) {
                return true;
            }
        }
        return false;
    }

    private static BlueprintPoint findPointAtBlock(BlueprintSession session, BlockPos targetBlock) {
        for (BlueprintPoint existing : session.getPoints()) {
            BlockPos existingBlock = BlockPos.ofFloored(
                    BlueprintGeometry.toWorldCoords(existing, session.getOrigin()));
            if (existingBlock.equals(targetBlock)) {
                return existing;
            }
        }
        return null;
    }

    private static boolean isSameBlock(BlueprintPoint a, BlueprintPoint b, BlueprintSession session) {
        BlockPos blockA = BlockPos.ofFloored(BlueprintGeometry.toWorldCoords(a, session.getOrigin()));
        BlockPos blockB = BlockPos.ofFloored(BlueprintGeometry.toWorldCoords(b, session.getOrigin()));
        return blockA.equals(blockB);
    }

    private static boolean hasLine(BlueprintSession session, BlueprintPoint a, BlueprintPoint b) {
        for (BlueprintLine line : session.getLines()) {
            boolean sameDirection = line.start().equals(a) && line.end().equals(b);
            boolean reversed = line.start().equals(b) && line.end().equals(a);
            if (sameDirection || reversed) {
                return true;
            }
        }
        return false;
    }
}