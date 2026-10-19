package com.bosshittracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel: pick a boss, then pick one kill (or all kills) to see your hits per weapon.
 */
class BossHitTrackerPanel extends PluginPanel
{
	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance();
	private static final String ALL_KILLS = "All kills (lifetime)";
	/** Kill selection meaning "lifetime totals" rather than one kill. */
	private static final int LIFETIME = -1;

	private final BossHitTrackerPlugin plugin;
	private final JComboBox<String> bossSelect = new JComboBox<>();
	private final JComboBox<String> killSelect = new JComboBox<>();
	private final JPanel content = new JPanel();
	private boolean updatingCombos;

	/** Index into the boss's recent kills (0 = newest), or LIFETIME. */
	private int selectedKill = 0;
	/** Kill count when the panel was last drawn, to notice new kills. */
	private int lastKillCount = -1;
	private String lastBoss;

	BossHitTrackerPanel(BossHitTrackerPlugin plugin)
	{
		this.plugin = plugin;

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Boss Hit Tracker");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(LEFT_ALIGNMENT);
		header.add(title);
		header.add(Box.createVerticalStrut(6));

		header.add(fieldLabel("Boss"));
		bossSelect.setAlignmentX(LEFT_ALIGNMENT);
		header.add(bossSelect);
		header.add(Box.createVerticalStrut(6));

		header.add(fieldLabel("Kill"));
		killSelect.setAlignmentX(LEFT_ALIGNMENT);
		killSelect.setToolTipText("Show one kill, or all kills combined");
		header.add(killSelect);
		header.add(Box.createVerticalStrut(6));

		JPanel buttons = new JPanel(new GridLayout(1, 2, 6, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		JButton copy = new JButton("Copy CSV");
		copy.setToolTipText("Copy all bosses and weapons to the clipboard as CSV (paste into a spreadsheet)");
		copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
			.setContents(new StringSelection(plugin.exportCsv()), null));
		JButton reset = new JButton("Reset boss");
		reset.setToolTipText("Delete all tracked hits for the selected boss");
		reset.addActionListener(e -> confirmReset());
		buttons.add(copy);
		buttons.add(reset);
		header.add(buttons);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Stop BoxLayout from stretching these vertically
		for (javax.swing.JComponent c : new javax.swing.JComponent[]{bossSelect, killSelect, buttons})
		{
			c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
		}

		add(header, BorderLayout.NORTH);
		add(content, BorderLayout.CENTER);

		bossSelect.addActionListener(e ->
		{
			if (!updatingCombos)
			{
				selectedKill = 0;
				showSelectedBoss();
			}
		});
		killSelect.addActionListener(e ->
		{
			if (!updatingCombos)
			{
				int index = killSelect.getSelectedIndex();
				selectedKill = index <= 0 ? LIFETIME : index - 1;
				showSelectedBoss();
			}
		});

		rebuild();
	}

	/** Reload the boss list and redraw. Call on the Swing thread. */
	void rebuild()
	{
		String selected = (String) bossSelect.getSelectedItem();
		List<String> names = plugin.getBossNames();

		updatingCombos = true;
		bossSelect.removeAllItems();
		for (String name : names)
		{
			bossSelect.addItem(name);
		}
		if (selected != null && names.contains(selected))
		{
			bossSelect.setSelectedItem(selected);
		}
		updatingCombos = false;

		showSelectedBoss();
	}

	private void showSelectedBoss()
	{
		content.removeAll();
		String boss = (String) bossSelect.getSelectedItem();
		BossStats stats = boss == null ? null : plugin.getBossStats(boss);

		if (stats == null)
		{
			updatingCombos = true;
			killSelect.removeAllItems();
			updatingCombos = false;
			content.add(textLine("No kills tracked yet. Kill a boss and your hits will show up here.", ColorScheme.LIGHT_GRAY_COLOR));
			finishContent();
			return;
		}

		List<KillRecord> kills = stats.getRecentKills();

		// When a new kill comes in, keep showing the newest kill if that's what you were looking at;
		// otherwise shift the selection so it stays on the same kill.
		if (boss.equals(lastBoss) && lastKillCount >= 0 && stats.getKills() > lastKillCount && selectedKill > 0)
		{
			selectedKill += stats.getKills() - lastKillCount;
		}
		if (selectedKill >= kills.size())
		{
			selectedKill = kills.isEmpty() ? LIFETIME : kills.size() - 1;
		}
		lastBoss = boss;
		lastKillCount = stats.getKills();

		fillKillSelect(stats, kills);

		if (selectedKill == LIFETIME)
		{
			showLifetime(stats);
		}
		else
		{
			showKill(stats, kills.get(selectedKill), stats.getKills() - selectedKill);
		}

		content.add(Box.createVerticalStrut(6));
		content.add(sectionTitle("Recent kills"));
		for (int i = 0; i < kills.size(); i++)
		{
			content.add(killRow(stats, kills.get(i), i));
		}
		if (!kills.isEmpty())
		{
			content.add(textLine("Click a kill to see its weapons.", ColorScheme.MEDIUM_GRAY_COLOR));
		}

		finishContent();
	}

	private void fillKillSelect(BossStats stats, List<KillRecord> kills)
	{
		updatingCombos = true;
		killSelect.removeAllItems();
		killSelect.addItem(ALL_KILLS);
		for (int i = 0; i < kills.size(); i++)
		{
			KillRecord k = kills.get(i);
			String label = "Kill #" + (stats.getKills() - i) + "  (" + duration(k) + ")";
			if (i == 0)
			{
				label += "  (latest)";
			}
			killSelect.addItem(label);
		}
		killSelect.setSelectedIndex(selectedKill == LIFETIME ? 0 : selectedKill + 1);
		updatingCombos = false;
	}

	private void showLifetime(BossStats stats)
	{
		long totalDamage = 0;
		int totalHits = 0;
		for (WeaponStats w : stats.getWeapons().values())
		{
			totalDamage += w.getDamage();
			totalHits += w.getHits();
		}

		content.add(sectionTitle("All kills"));
		content.add(textLine("Kills: " + NUMBER.format(stats.getKills()), Color.WHITE));
		content.add(textLine("Hits: " + NUMBER.format(totalHits) + "   Damage: " + NUMBER.format(totalDamage), Color.WHITE));
		if (stats.getKills() > 0)
		{
			content.add(textLine("Avg damage per kill: " + NUMBER.format(totalDamage / stats.getKills()), Color.WHITE));
		}
		content.add(Box.createVerticalStrut(6));

		content.add(sectionTitle("By weapon (all kills)"));
		addWeaponCards(stats.getWeapons(), totalDamage);
	}

	private void showKill(BossStats stats, KillRecord kill, int killNumber)
	{
		SimpleDateFormat date = new SimpleDateFormat("MMM d, yyyy h:mm a");
		content.add(sectionTitle("Kill #" + killNumber));
		content.add(textLine(date.format(new Date(kill.getTimestamp())) + "   Time: " + duration(kill), Color.WHITE));
		content.add(textLine("Hits: " + NUMBER.format(kill.getTotalHits()) + "   Damage: " + NUMBER.format(kill.getTotalDamage()), Color.WHITE));
		content.add(Box.createVerticalStrut(6));

		content.add(sectionTitle("By weapon (this kill)"));
		addWeaponCards(kill.getWeapons(), kill.getTotalDamage());
	}

	private void addWeaponCards(Map<String, WeaponStats> weaponMap, long totalDamage)
	{
		List<Map.Entry<String, WeaponStats>> weapons = new ArrayList<>(weaponMap.entrySet());
		weapons.sort((a, b) -> Long.compare(b.getValue().getDamage(), a.getValue().getDamage()));
		for (Map.Entry<String, WeaponStats> e : weapons)
		{
			content.add(weaponCard(e.getKey(), e.getValue(), totalDamage));
			content.add(Box.createVerticalStrut(4));
		}
	}

	private JPanel weaponCard(String weapon, WeaponStats w, long totalDamage)
	{
		JPanel card = new JPanel(new GridLayout(0, 1, 0, 1));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JLabel name = new JLabel(weapon);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(ColorScheme.BRAND_ORANGE);
		card.add(name);

		double share = totalDamage == 0 ? 0 : 100.0 * w.getDamage() / totalDamage;
		card.add(small(String.format("Hits %s  ·  Dmg %s (%.0f%%)", NUMBER.format(w.getHits()), NUMBER.format(w.getDamage()), share)));
		card.add(small(String.format("Avg %.1f  ·  Max %d", w.getAverageHit(), w.getMaxHit())));
		card.add(small(String.format("Zeros %s  ·  Landed %.0f%%", NUMBER.format(w.getZeros()), w.getNonZeroPercent())));
		return card;
	}

	private JLabel killRow(BossStats stats, KillRecord k, int index)
	{
		boolean selected = index == selectedKill;
		JLabel row = textLine(String.format("#%d  %s  %s dmg  %d hits",
			stats.getKills() - index, duration(k), NUMBER.format(k.getTotalDamage()), k.getTotalHits()),
			selected ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				selectedKill = index;
				showSelectedBoss();
			}
		});
		return row;
	}

	private void finishContent()
	{
		content.revalidate();
		content.repaint();
	}

	private static String duration(KillRecord k)
	{
		long secs = k.getDurationMs() / 1000;
		return String.format("%d:%02d", secs / 60, secs % 60);
	}

	private void confirmReset()
	{
		String boss = (String) bossSelect.getSelectedItem();
		if (boss == null)
		{
			return;
		}
		int choice = JOptionPane.showConfirmDialog(this,
			"Delete all tracked hits for " + boss + "?", "Reset boss", JOptionPane.YES_NO_OPTION);
		if (choice == JOptionPane.YES_OPTION)
		{
			plugin.resetBoss(boss);
		}
	}

	private static JLabel fieldLabel(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel sectionTitle(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(Color.WHITE);
		l.setBorder(new EmptyBorder(0, 0, 3, 0));
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel textLine(String text, Color color)
	{
		JLabel l = new JLabel("<html>" + text + "</html>");
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel small(String text)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.TEXT_COLOR);
		return l;
	}
}
