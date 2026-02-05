package org.solofausto.minecad.blueprint;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class BlueprintGeometry {
    private static final double PLANE_OFFSET = 0.008;
    private static final double EPSILON = 1.0E-6;

    private BlueprintGeometry() {
    }

    public static Vec3d intersectRayWithPlane(Vec3d rayOrigin, Vec3d rayDirection, BlueprintOrigin origin) {
        Vec3d normal = Vec3d.of(origin.face().getVector());
        double denom = rayDirection.dotProduct(normal);
        if (Math.abs(denom) < EPSILON) {
            return null;
        }

        Vec3d planePoint = planePoint(origin);
        double t = planePoint.subtract(rayOrigin).dotProduct(normal) / denom;
        if (t <= 0.0) {
            return null;
        }

        return rayOrigin.add(rayDirection.multiply(t));
    }

    public static BlueprintPoint projectToBlueprintCoords(Vec3d hit, BlueprintOrigin origin) {
        BlockPos pos = origin.blockPos();
        Direction face = origin.face();
        double localX;
        double localY;

        switch (face) {
            case NORTH, SOUTH -> {
                localX = hit.x - pos.getX();
                localY = hit.y - pos.getY();
            }
            case EAST, WEST -> {
                localX = hit.z - pos.getZ();
                localY = hit.y - pos.getY();
            }
            case UP, DOWN -> {
                localX = hit.x - pos.getX();
                localY = hit.z - pos.getZ();
            }
            default -> {
                localX = hit.x - pos.getX();
                localY = hit.y - pos.getY();
            }
        }

        return new BlueprintPoint(localX, localY);
    }

    public static Vec3d toWorldCoords(BlueprintPoint point, BlueprintOrigin origin) {
        BlockPos pos = origin.blockPos();
        Direction face = origin.face();
        double worldX;
        double worldY;
        double worldZ;

        switch (face) {
            case NORTH -> {
                worldX = pos.getX() + point.x();
                worldY = pos.getY() + point.y();
                worldZ = pos.getZ();
            }
            case SOUTH -> {
                worldX = pos.getX() + point.x();
                worldY = pos.getY() + point.y();
                worldZ = pos.getZ() + 1.0;
            }
            case EAST -> {
                worldX = pos.getX() + 1.0;
                worldY = pos.getY() + point.y();
                worldZ = pos.getZ() + point.x();
            }
            case WEST -> {
                worldX = pos.getX();
                worldY = pos.getY() + point.y();
                worldZ = pos.getZ() + point.x();
            }
            case UP -> {
                worldX = pos.getX() + point.x();
                worldY = pos.getY() + 1.0;
                worldZ = pos.getZ() + point.y();
            }
            case DOWN -> {
                worldX = pos.getX() + point.x();
                worldY = pos.getY();
                worldZ = pos.getZ() + point.y();
            }
            default -> {
                worldX = pos.getX() + point.x();
                worldY = pos.getY() + point.y();
                worldZ = pos.getZ();
            }
        }

        return new Vec3d(worldX, worldY, worldZ);
    }

    private static Vec3d planePoint(BlueprintOrigin origin) {
        BlockPos pos = origin.blockPos();
        Direction face = origin.face();
        double x0 = pos.getX();
        double y0 = pos.getY();
        double z0 = pos.getZ();
        double x1 = x0 + 1.0;
        double y1 = y0 + 1.0;
        double z1 = z0 + 1.0;

        return switch (face) {
            case NORTH -> new Vec3d(x0, y0, z0 - PLANE_OFFSET);
            case SOUTH -> new Vec3d(x0, y0, z1 + PLANE_OFFSET);
            case EAST -> new Vec3d(x1 + PLANE_OFFSET, y0, z0);
            case WEST -> new Vec3d(x0 - PLANE_OFFSET, y0, z0);
            case UP -> new Vec3d(x0, y1 + PLANE_OFFSET, z0);
            case DOWN -> new Vec3d(x0, y0 - PLANE_OFFSET, z0);
        };
    }
}