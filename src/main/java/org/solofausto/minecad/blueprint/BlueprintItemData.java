package org.solofausto.minecad.blueprint;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.Direction;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.util.math.BlockPos;

public final class BlueprintItemData {
    public static final String NBT_BLUEPRINT = "Blueprint";
    private static final String NBT_ID = "Id";
    private static final String NBT_ORIGIN = "Origin";
    private static final String NBT_FACE = "Face";
    private static final String NBT_X = "X";
    private static final String NBT_Y = "Y";
    private static final String NBT_Z = "Z";
    private static final String NBT_POINTS = "Points";
    private static final String NBT_POINT_X = "x";
    private static final String NBT_POINT_Y = "y";
    private static final String NBT_LINES = "Lines";
    private static final String NBT_LINE_X1 = "x1";
    private static final String NBT_LINE_Y1 = "y1";
    private static final String NBT_LINE_X2 = "x2";
    private static final String NBT_LINE_Y2 = "y2";
    private static final String NBT_PENDING_LINE = "PendingLine";
    private static final String NBT_PENDING_X = "x";
    private static final String NBT_PENDING_Y = "y";

    private BlueprintItemData() {
    }

    public static void writeToItem(ItemStack stack, BlueprintSession session) {
        if (stack == null || stack.isEmpty() || session == null || session.getOrigin() == null) {
            return;
        }

        java.util.UUID id = session.getCurrentBlueprintId();
        if (id == null) {
            id = getBlueprintId(stack);
        }
        if (id == null) {
            id = java.util.UUID.randomUUID();
        }
        session.setCurrentBlueprintId(id);

        NbtCompound blueprintNbt = new NbtCompound();
        BlueprintOrigin origin = session.getOrigin();
        BlockPos pos = origin.blockPos();

        NbtCompound originNbt = new NbtCompound();
        originNbt.putInt(NBT_X, pos.getX());
        originNbt.putInt(NBT_Y, pos.getY());
        originNbt.putInt(NBT_Z, pos.getZ());
        originNbt.putString(NBT_FACE, origin.face().asString());
        blueprintNbt.put(NBT_ORIGIN, originNbt);
        blueprintNbt.putString(NBT_ID, id.toString());

        NbtList pointsList = new NbtList();
        for (BlueprintPoint point : session.getPoints()) {
            NbtCompound pointNbt = new NbtCompound();
            pointNbt.putDouble(NBT_POINT_X, point.x());
            pointNbt.putDouble(NBT_POINT_Y, point.y());
            pointsList.add(pointNbt);
        }
        blueprintNbt.put(NBT_POINTS, pointsList);

        NbtList linesList = new NbtList();
        for (BlueprintLine line : session.getLines()) {
            NbtCompound lineNbt = new NbtCompound();
            lineNbt.putDouble(NBT_LINE_X1, line.start().x());
            lineNbt.putDouble(NBT_LINE_Y1, line.start().y());
            lineNbt.putDouble(NBT_LINE_X2, line.end().x());
            lineNbt.putDouble(NBT_LINE_Y2, line.end().y());
            linesList.add(lineNbt);
        }
        blueprintNbt.put(NBT_LINES, linesList);

        BlueprintPoint pending = session.getPendingLineStart();
        if (pending != null) {
            NbtCompound pendingNbt = new NbtCompound();
            pendingNbt.putDouble(NBT_PENDING_X, pending.x());
            pendingNbt.putDouble(NBT_PENDING_Y, pending.y());
            blueprintNbt.put(NBT_PENDING_LINE, pendingNbt);
        } else {
            blueprintNbt.remove(NBT_PENDING_LINE);
        }

        NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        root.put(NBT_BLUEPRINT, blueprintNbt);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
    }

    public static boolean loadFromItem(ItemStack stack, BlueprintSession session) {
        if (stack == null || stack.isEmpty() || session == null) {
            return false;
        }

        NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        Optional<NbtCompound> blueprintOpt = root.getCompound(NBT_BLUEPRINT);
        if (blueprintOpt.isEmpty()) {
            return false;
        }

        NbtCompound blueprintNbt = blueprintOpt.get();
        Optional<NbtCompound> originOpt = blueprintNbt.getCompound(NBT_ORIGIN);
        if (originOpt.isEmpty()) {
            return false;
        }

        Optional<String> idOpt = blueprintNbt.getString(NBT_ID);
        if (idOpt.isPresent()) {
            try {
                session.setCurrentBlueprintId(java.util.UUID.fromString(idOpt.get()));
            } catch (IllegalArgumentException ex) {
                session.setCurrentBlueprintId(null);
            }
        }

        NbtCompound originNbt = originOpt.get();
        Optional<String> faceName = originNbt.getString(NBT_FACE);
        if (faceName.isEmpty()) {
            return false;
        }

        Direction face;
        try {
            face = Direction.valueOf(faceName.get().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return false;
        }

        Optional<Integer> xOpt = originNbt.getInt(NBT_X);
        Optional<Integer> yOpt = originNbt.getInt(NBT_Y);
        Optional<Integer> zOpt = originNbt.getInt(NBT_Z);
        if (xOpt.isEmpty() || yOpt.isEmpty() || zOpt.isEmpty()) {
            return false;
        }

        BlockPos pos = new BlockPos(xOpt.get(), yOpt.get(), zOpt.get());
        session.start(new BlueprintOrigin(pos, face));

        NbtList pointsList = blueprintNbt.getListOrEmpty(NBT_POINTS);
        for (int i = 0; i < pointsList.size(); i++) {
            Optional<NbtCompound> pointOpt = pointsList.getCompound(i);
            if (pointOpt.isEmpty()) {
                continue;
            }

            NbtCompound pointNbt = pointOpt.get();
            Optional<Double> x = pointNbt.getDouble(NBT_POINT_X);
            Optional<Double> y = pointNbt.getDouble(NBT_POINT_Y);
            if (x.isEmpty() || y.isEmpty()) {
                continue;
            }
            session.addPoint(new BlueprintPoint(x.get(), y.get()));
        }

        NbtList linesList = blueprintNbt.getListOrEmpty(NBT_LINES);
        for (int i = 0; i < linesList.size(); i++) {
            Optional<NbtCompound> lineOpt = linesList.getCompound(i);
            if (lineOpt.isEmpty()) {
                continue;
            }

            NbtCompound lineNbt = lineOpt.get();
            Optional<Double> x1 = lineNbt.getDouble(NBT_LINE_X1);
            Optional<Double> y1 = lineNbt.getDouble(NBT_LINE_Y1);
            Optional<Double> x2 = lineNbt.getDouble(NBT_LINE_X2);
            Optional<Double> y2 = lineNbt.getDouble(NBT_LINE_Y2);
            if (x1.isEmpty() || y1.isEmpty() || x2.isEmpty() || y2.isEmpty()) {
                continue;
            }

            BlueprintPoint start = new BlueprintPoint(x1.get(), y1.get());
            BlueprintPoint end = new BlueprintPoint(x2.get(), y2.get());
            session.addLine(new BlueprintLine(start, end));
        }

        Optional<NbtCompound> pendingOpt = blueprintNbt.getCompound(NBT_PENDING_LINE);
        if (pendingOpt.isPresent()) {
            NbtCompound pendingNbt = pendingOpt.get();
            Optional<Double> px = pendingNbt.getDouble(NBT_PENDING_X);
            Optional<Double> py = pendingNbt.getDouble(NBT_PENDING_Y);
            if (px.isPresent() && py.isPresent()) {
                session.setPendingLineStart(new BlueprintPoint(px.get(), py.get()));
            }
        }

        return true;
    }

    public static java.util.UUID ensureBlueprintId(ItemStack stack, BlueprintSession session) {
        if (stack == null || stack.isEmpty() || session == null) {
            return null;
        }

        java.util.UUID id = getBlueprintId(stack);
        if (id == null) {
            id = java.util.UUID.randomUUID();
            NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
            NbtCompound blueprintNbt = root.getCompound(NBT_BLUEPRINT).orElseGet(NbtCompound::new);
            blueprintNbt.putString(NBT_ID, id.toString());
            root.put(NBT_BLUEPRINT, blueprintNbt);
            stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(root));
        }

        session.setCurrentBlueprintId(id);
        return id;
    }

    public static java.util.UUID getBlueprintId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        NbtCompound root = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        Optional<NbtCompound> blueprintOpt = root.getCompound(NBT_BLUEPRINT);
        if (blueprintOpt.isEmpty()) {
            return null;
        }

        Optional<String> idOpt = blueprintOpt.get().getString(NBT_ID);
        if (idOpt.isEmpty()) {
            return null;
        }

        try {
            return java.util.UUID.fromString(idOpt.get());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}