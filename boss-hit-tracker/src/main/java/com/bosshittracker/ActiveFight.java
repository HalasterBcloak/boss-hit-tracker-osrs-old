package com.bosshittracker;

/**
 * A boss you are currently hitting, before it dies.
 */
class ActiveFight
{
	final String boss;
	final KillRecord record;
	/** Set when the NPC dies; the kill is saved when it despawns. */
	boolean died;

	ActiveFight(String boss, long startTime)
	{
		this.boss = boss;
		this.record = new KillRecord(startTime);
	}
}
