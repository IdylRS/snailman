package com.idyl.snailman;

import net.runelite.client.config.*;

import java.awt.*;

@ConfigGroup("snailmanmode")
public interface SnailManModeConfig extends Config
{
	@ConfigSection(
			name = "Horror Options",
			description = "Config settings for horror mode",
			position = 99
	)
	String horrorSection = "section";

	@ConfigItem(
			keyName = "snailColor",
			name = "Snail Color",
			description = "The color of the snail tile",
			position = 1
	)
	default Color color()
	{
		return Color.RED;
	}

	@ConfigItem(
			keyName = "speedBoost",
			name = "Speed Boost",
			description = "Snail moves 1 tile per tick when it gets close to you",
			position = 4
	)
	default boolean speedBoost() { return false; }

	@ConfigItem(
			keyName = "showOnMap",
			name = "Show Snail on World Map",
			description = "Show where the snail is on the world map (kind of defeats the purpose but its neat)",
			position = 2
	)
	default boolean showOnMap() { return false; }

	@ConfigItem(
			keyName = "pauseSnail",
			name = "Pause Snail",
			description = "Pause the snail so that it stops following you",
			position = 5
	)
	default boolean pauseSnail() { return false; }

	@ConfigItem(
			keyName = "moveSpeed",
			name = "Move Speed",
			description = "How many ticks it takes the snail to move 1 tile",
			position = 3
	)
	@Range(
			min=1,
			max=30
	)
	default int moveSpeed() { return 1; }

	@ConfigItem(
			keyName = "horrorMode",
			name = "Horror Mode",
			description = "Activate Horror Mode (best experienced with minimum render distance & maximum fog in the GPU plugin)",
			section = horrorSection
	)
	default boolean horrorMode()
	{
		return false;
	}

	@ConfigItem(
			keyName = "drawDistance",
			name = "Draw Distance",
			description = "Distance at which the snail is rendered",
			section = horrorSection
	)
	@Range(
			min = 1,
			max = 32
	)
	default int drawDistance() { return 5; }
}
