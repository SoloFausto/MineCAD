package org.solofausto.minecad.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BlueprintSession {
    private BlueprintOrigin origin;
    private final List<BlueprintPoint> points = new ArrayList<>();
    private final List<BlueprintLine> lines = new ArrayList<>();
    private BlueprintPoint pendingLineStart;
    private java.util.UUID currentBlueprintId;

    public void start(BlueprintOrigin origin) {
        this.origin = origin;
        this.points.clear();
        this.lines.clear();
        this.pendingLineStart = null;
    }

    public BlueprintOrigin getOrigin() {
        return origin;
    }

    public java.util.UUID getCurrentBlueprintId() {
        return currentBlueprintId;
    }

    public void setCurrentBlueprintId(java.util.UUID currentBlueprintId) {
        this.currentBlueprintId = currentBlueprintId;
    }

    public void addPoint(BlueprintPoint point) {
        points.add(point);
    }

    public void addLine(BlueprintLine line) {
        lines.add(line);
    }

    public List<BlueprintPoint> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public List<BlueprintLine> getLines() {
        return Collections.unmodifiableList(lines);
    }

    public BlueprintPoint getPendingLineStart() {
        return pendingLineStart;
    }

    public void setPendingLineStart(BlueprintPoint pendingLineStart) {
        this.pendingLineStart = pendingLineStart;
    }

    public void clear() {
        origin = null;
        points.clear();
        lines.clear();
        pendingLineStart = null;
        currentBlueprintId = null;
    }
}
