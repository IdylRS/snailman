package com.idyl.snailman;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SnailmanModePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SnailmanModePlugin.class);
		RuneLite.main(args);
	}
}