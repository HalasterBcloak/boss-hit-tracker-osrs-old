package com.bosshittracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Your hits on a single boss kill, grouped by weapon.
 */
public class KillRecord
{
	long timestamp;
	long durationMs;
	Map<String, WeaponStats> weapons = new LinkedHashMap<>();

	KillRecord()
	{
	}

	KillRecord(long timestamp)
	{
		this.timestamp = timestamp;
	}

	void addHit(String weapon, int amount)
	{
		weapons.computeIfAbsent(weapon, w -> new WeaponStats()).addHit(amount);
	}

	public long getTimestamp()
	{
		return timestamp;
	}

	public long getDurationMs()
	{
		return durationMs;
	}

	public Map<String, WeaponStats> getWeapons()
	{
		return weapons == null ? new LinkedHashMap<>() : weapons;
	}

	public int getTotalHits()
	{
		int total = 0;
		for (WeaponStats w : getWeapons().values())
		{
			total += w.hits;
		}
		return total;
	}

	public long getTotalDamage()
	{
		long total = 0;
		for (WeaponStats w : getWeapons().values())
		{
			total += w.damage;
		}
		return total;
	}

	KillRecord copy()
	{
		KillRecord c = new KillRecord(timestamp);
		c.durationMs = durationMs;
		for (Map.Entry<String, WeaponStats> e : getWeapons().entrySet())
		{
			c.weapons.put(e.getKey(), e.getValue().copy());
		}
		return c;
	}
}
