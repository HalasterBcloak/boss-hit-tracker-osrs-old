package com.bosshittracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(BossHitTrackerConfig.GROUP)
public interface BossHitTrackerConfig extends Config
{
	String GROUP = "bosshittracker";

	@ConfigItem(
		keyName = "bossesOnly",
		name = "Bosses only",
		description = "Only track hits on bosses. Turn off to track hits on every NPC you kill.",
		position = 0
	)
	default boolean bossesOnly()
	{
		return true;
	}

	@ConfigItem(
		keyName = "extraNpcs",
		name = "Extra NPCs to track",
		description = "Comma-separated NPC names to track in addition to the built-in boss list (e.g. Tormented Demon, Wyrm)",
		position = 1
	)
	default String extraNpcs()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show fight overlay",
		description = "Show your hits for the current fight in an on-screen overlay",
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackSpells",
		name = "Track spells",
		description = "Track magic hits per spell, e.g. \"Kodai wand (Ice Barrage)\", for both manual casts and autocast",
		position = 3
	)
	default boolean trackSpells()
	{
		return true;
	}

	@Range(min = 1, max = 200)
	@ConfigItem(
		keyName = "historySize",
		name = "Kills kept per boss",
		description = "How many individual kills to keep in each boss's kill history",
		position = 4
	)
	default int historySize()
	{
		return 50;
	}
}
