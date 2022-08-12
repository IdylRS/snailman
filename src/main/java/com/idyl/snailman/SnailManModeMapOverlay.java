package com.idyl.snailman;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Area;

public class SnailManModeMapOverlay extends Overlay {
    private final Client client;
    private final SnailManModePlugin plugin;
    private final SnailManModeConfig config;

    private Area mapClipArea;

    private BufferedImage mapIcon;

    @Inject
    private SnailManModeMapOverlay(Client client, SnailManModePlugin plugin, SnailManModeConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.MANUAL);
        drawAfterLayer(WidgetInfo.WORLD_MAP_VIEW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if(!config.showOnMap()) return null;

        if (client.getWidget(WidgetInfo.WORLD_MAP_VIEW) == null) {
            return null;
        }

        mapClipArea = getWorldMapClipArea(client.getWidget(WidgetInfo.WORLD_MAP_VIEW).getBounds());
        graphics.setClip(mapClipArea);

        BufferedImage marker = getMapIconImage();
        Point point = plugin.mapWorldPointToGraphicsPoint(plugin.getSnailWorldPoint());
        graphics.drawImage(marker, point.getX() - marker.getWidth() / 2, point.getY() - marker.getHeight() / 2, null);

        return null;
    }

    private BufferedImage getMapIconImage() {
        if(mapIcon == null) {
            mapIcon = ImageUtil.loadImageResource(getClass(), "/marker.png");
        }

        return mapIcon;
    }

    private Area getWorldMapClipArea(Rectangle baseRectangle) {
        final Widget overview = client.getWidget(WidgetInfo.WORLD_MAP_OVERVIEW_MAP);
        final Widget surfaceSelector = client.getWidget(WidgetInfo.WORLD_MAP_SURFACE_SELECTOR);

        Area clipArea = new Area(baseRectangle);

        if (overview != null && !overview.isHidden()) {
            clipArea.subtract(new Area(overview.getBounds()));
        }

        if (surfaceSelector != null && !surfaceSelector.isHidden()) {
            clipArea.subtract(new Area(surfaceSelector.getBounds()));
        }

        return clipArea;
    }
}
