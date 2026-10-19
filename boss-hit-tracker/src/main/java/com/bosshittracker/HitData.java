package com.bosshittracker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * All saved data, keyed by boss name. Stored as JSON per RuneScape account.
 */
public class HitData
{
	Map<String, BossStats> bosses = new LinkedHashMap<>();
}
