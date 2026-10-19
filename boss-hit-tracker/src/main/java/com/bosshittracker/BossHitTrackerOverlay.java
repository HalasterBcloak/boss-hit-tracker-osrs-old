package com.bosshittracker;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Shows your hits on the boss you're currently fighting.
 */
class BossHitTrackerOverlay extends OverlayPanel
{
	private final BossHitTrackerPlugin plugin;
	private final BossHitTrackerConfig config;

	@Inject
	BossHitTrackerOverlay(BossHitTrackerPlugin plugin, BossHitTrackerConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showOverlay())
		{
			return null;
		}
		ActiveFight fight = plugin.getCurrentFightSnapshot();
		if (fight == null)
		{
			return null;
		}

		long seconds = (System.currentTimeMillis() - fight.record.getTimestamp()) / 1000;
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(fight.boss)
			.color(Color.ORANGE)
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Time")
			.right(String.format("%d:%02d", seconds / 60, seconds % 60))
			.build());

		for (Map.Entry<String, WeaponStats> e : fight.record.getWeapons().entrySet())
		{
			WeaponStats w = e.getValue();
			panelComponent.getChildren().add(LineComponent.builder()
				.left(e.getKey())
				.leftColor(Color.LIGHT_GRAY)
				.right(w.getHits() + " hits / " + w.getDamage())
				.build());
		}

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Total")
			.right(fight.record.getTotalHits() + " hits / " + fight.record.getTotalDamage())
			.rightColor(Color.GREEN)
			.build());

		panelComponent.setPreferredSize(new Dimension(190, 0));
		return super.render(graphics);
	}
}
