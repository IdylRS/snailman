package com.idyl.snailman;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.*;

@ConfigGroup("snailmanmode")
public interface SnailManModeConfig extends Config
{
	@ConfigItem(
			keyName = "snailColor",
			name = "Snail Color",
			description = "The color of the snail tile"
	)
	default Color color()
	{
		return Color.RED;
	}

	@ConfigItem(
			keyName = "showOnMap",
			name = "Show Snail on World Map",
			description = "Show where the snail is on the world map (kind of defeats the purpose but its neat)"
	)
	default boolean showOnMap() { return false; }

	@ConfigItem(
			keyName = "pauseSnail",
			name = "Pause Snail",
			description = "Pause the snail so that it stops following you"
	)
	default boolean pauseSnail() { return false; }

	@ConfigItem(
			keyName = "moveSpeed",
			name = "Move Speed",
			description = "How many ticks it takes the snail to move 1 tile"

	)
	default int moveSpeed() { return 1; }
}
