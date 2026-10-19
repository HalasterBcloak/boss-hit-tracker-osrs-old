package com.bosshittracker;

/**
 * Totals for the hits you landed with one weapon.
 */
public class WeaponStats
{
	int hits;
	int zeros;
	long damage;
	int maxHit;

	void addHit(int amount)
	{
		hits++;
		damage += amount;
		if (amount == 0)
		{
			zeros++;
		}
		if (amount > maxHit)
		{
			maxHit = amount;
		}
	}

	void merge(WeaponStats other)
	{
		hits += other.hits;
		zeros += other.zeros;
		damage += other.damage;
		maxHit = Math.max(maxHit, other.maxHit);
	}

	WeaponStats copy()
	{
		WeaponStats c = new WeaponStats();
		c.merge(this);
		return c;
	}

	public int getHits()
	{
		return hits;
	}

	public int getZeros()
	{
		return zeros;
	}

	public long getDamage()
	{
		return damage;
	}

	public int getMaxHit()
	{
		return maxHit;
	}

	public double getAverageHit()
	{
		return hits == 0 ? 0 : (double) damage / hits;
	}

	/** Share of hits that did damage, 0-100. */
	public double getNonZeroPercent()
	{
		return hits == 0 ? 0 : 100.0 * (hits - zeros) / hits;
	}
}
