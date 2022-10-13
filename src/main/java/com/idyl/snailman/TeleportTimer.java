package com.idyl.snailman;

import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxPriority;
import net.runelite.client.ui.overlay.infobox.Timer;

import javax.inject.Inject;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

class TeleportTimer extends Timer
{
    TeleportTimer(Duration duration, BufferedImage image, SnailManModePlugin plugin)
    {
        super(duration.toMillis(), ChronoUnit.MILLIS, image, plugin);
        this.setTooltip("You are too afraid to cast a teleport spell.");
        setPriority(InfoBoxPriority.MED);
    }
}
