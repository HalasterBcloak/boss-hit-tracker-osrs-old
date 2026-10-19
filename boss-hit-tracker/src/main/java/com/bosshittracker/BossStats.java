package com.bosshittracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything tracked for one boss: kill count, lifetime per-weapon totals and recent kills (newest first).
 */
public class BossStats
{
	int kills;
	Map<String, WeaponStats> weapons = new LinkedHashMap<>();
	List<KillRecord> recentKills = new ArrayList<>();

	void addKill(KillRecord kill, int historySize)
	{
		kills++;
		if (weapons == null)
		{
			weapons = new LinkedHashMap<>();
		}
		if (recentKills == null)
		{
			recentKills = new ArrayList<>();
		}
		for (Map.Entry<String, WeaponStats> e : kill.getWeapons().entrySet())
		{
			weapons.computeIfAbsent(e.getKey(), w -> new WeaponStats()).merge(e.getValue());
		}
		recentKills.add(0, kill);
		while (recentKills.size() > historySize)
		{
			recentKills.remove(recentKills.size() - 1);
		}
	}

	public int getKills()
	{
		return kills;
	}

	public Map<String, WeaponStats> getWeapons()
	{
		return weapons == null ? new LinkedHashMap<>() : weapons;
	}

	public List<KillRecord> getRecentKills()
	{
		return recentKills == null ? new ArrayList<>() : recentKills;
	}

	BossStats copy()
	{
		BossStats c = new BossStats();
		c.kills = kills;
		for (Map.Entry<String, WeaponStats> e : getWeapons().entrySet())
		{
			c.weapons.put(e.getKey(), e.getValue().copy());
		}
		for (KillRecord k : getRecentKills())
		{
			c.recentKills.add(k.copy());
		}
		return c;
	}
}
