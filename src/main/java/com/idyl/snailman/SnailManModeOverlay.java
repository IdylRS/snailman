package com.idyl.snailman;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.*;
import net.runelite.api.Point;

import javax.inject.Inject;
import java.awt.*;

@Slf4j
public class SnailManModeOverlay extends Overlay {
    private static final int MAX_DRAW_DISTANCE = 32;

    private final Client client;
    private final SnailManModeConfig config;
    private final SnailManModePlugin plugin;

    @Inject
    private SnailManModeOverlay(Client client, SnailManModeConfig config, SnailManModePlugin plugin) {
        this.client = client;
        this.config = config;
        this.plugin = plugin;

        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    private void renderTransports(Graphics2D graphics) {
        for (WorldPoint a : plugin.pathfinder.transports.keySet()) {
            drawTransport(graphics, a, Color.GREEN, -1);

            java.awt.Point ca = tileCenter(a);

            if (ca == null) {
                continue;
            }

            for (WorldPoint b : plugin.pathfinder.transports.get(a)) {
                java.awt.Point cb = tileCenter(b);

                if (cb != null) {
                    graphics.drawLine(ca.x, ca.y, cb.x, cb.y);
                }
            }

            StringBuilder s = new StringBuilder();
            for (WorldPoint b : plugin.pathfinder.transports.get(a)) {
                if (b.getPlane() > a.getPlane()) {
                    s.append("+");
                } else if (b.getPlane() < a.getPlane()) {
                    s.append("-");
                } else {
                    s.append("=");
                }
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString(s.toString(), ca.x, ca.y);
        }
    }

    private java.awt.Point tileCenter(WorldPoint b) {
        if (b.getPlane() != client.getPlane()) {
            return null;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, b);
        if (lp == null) {
            return null;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return null;
        }

        int cx = poly.getBounds().x + poly.getBounds().width / 2;
        int cy = poly.getBounds().y + poly.getBounds().height / 2;
        return new java.awt.Point(cx, cy);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        renderTransports(graphics);

        WorldPoint snailPoint = plugin.getSnailWorldPoint();

        drawTile(graphics, snailPoint, config.color(), config.name(), new BasicStroke((float) 2));
        return null;
    }

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color, String name, Stroke borderStroke)
    {
        WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

        if (point.distanceTo(playerLocation) >= MAX_DRAW_DISTANCE)
        {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, point);
        if (lp == null)
        {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly != null)
        {
            OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, 1), borderStroke);
        }

        if(name != "") {
            Point canvasTextLocation = Perspective.getCanvasTextLocation(client, graphics, lp, name, 0);
            if (canvasTextLocation != null)
            {
                OverlayUtil.renderTextLocation(graphics, canvasTextLocation, name, color);
            }
        }
    }

    private void drawTransport(Graphics2D graphics, WorldPoint point, Color color, int counter) {
        if (point.getPlane() != client.getPlane()) {
            return;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, point);
        if (lp == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return;
        }

        graphics.setColor(color);
        graphics.fill(poly);
    }
}
