package com.idyl.snailman;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

import java.awt.*;

@ConfigGroup("snailmanmode")
public interface SnailManModeConfig extends Config
{
	@ConfigItem(
		keyName = "snailName",
		name = "Snail Name",
		description = "The name you'd like to give your snail"
	)
	default String name()
	{
		return "Snail";
	}

	@ConfigItem(
			keyName = "snailColor",
			name = "Snail Color",
			description = "The color of the snail tile"
	)
	default Color color()
	{
		return Color.RED;
	}
}
