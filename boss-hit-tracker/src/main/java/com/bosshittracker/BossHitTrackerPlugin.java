package com.bosshittracker;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Boss Hit Tracker",
	description = "Tracks every hit you land on bosses, broken down by boss, kill and weapon",
	tags = {"boss", "hit", "hitsplat", "damage", "weapon", "dps", "tracker", "pvm"}
)
public class BossHitTrackerPlugin extends Plugin
{
	private static final String DATA_KEY = "data";
	private static final String AUTOCAST_NAMES_KEY = "autocastNames";
	/** Words in the autocast menu that aren't spell names. */
	private static final Set<String> NOT_SPELL_NAMES = new HashSet<>(Arrays.asList(
		"autocast", "select", "choose", "cancel", "ok", "close", "spells", "defensive", "back"));
	private static final String UNARMED = "Unarmed";
	/** How long (in game ticks) an attack animation's weapon is used for the hitsplats that follow it. */
	private static final int ATTACK_WEAPON_TICKS = 8;
	/** COM_MODE value used for autocast on staves. */
	private static final int AUTOCAST_STYLE = 4;

	@Inject
	private Client client;

	@Inject
	private BossHitTrackerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BossHitTrackerOverlay overlay;

	@Inject
	private Gson gson;

	private final Object lock = new Object();
	private HitData data = new HitData();
	private final Map<NPC, ActiveFight> activeFights = new HashMap<>();
	private ActiveFight currentFight;
	private Set<String> extraNames = new HashSet<>();

	// Weapon held when you last started an attack, so a weapon switch while a
	// projectile is in flight still credits the weapon that made the attack.
	private String lastAttackWeapon;
	private int lastAttackTick = -100;
	// Spell you clicked to cast on an NPC, used for your next attack
	private String pendingCast;
	// Spell clicked in the autocast menu, waiting for the game to confirm its number
	private String pendingAutocastName;
	private int pendingAutocastTick = -100;
	// Autocast spell number -> spell name, learned when you pick a spell to autocast
	private Map<Integer, String> autocastNames = new HashMap<>();

	private BossHitTrackerPanel panel;
	private NavigationButton navButton;

	@Provides
	BossHitTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BossHitTrackerConfig.class);
	}

	@Override
	protected void startUp()
	{
		parseExtraNames();
		loadAutocastNames();

		panel = new BossHitTrackerPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Boss Hit Tracker")
			.icon(createIcon())
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadData();
		}
	}

	@Override
	protected void shutDown()
	{
		saveData();
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		synchronized (lock)
		{
			activeFights.clear();
			currentFight = null;
			data = new HitData();
		}
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		loadData();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BossHitTrackerConfig.GROUP.equals(event.getGroup()) && "extraNpcs".equals(event.getKey()))
		{
			parseExtraNames();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.HOPPING || state == GameState.LOGIN_SCREEN)
		{
			// Any fight in progress can't finish now
			synchronized (lock)
			{
				activeFights.clear();
				currentFight = null;
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Player local = client.getLocalPlayer();
		if (local == null || event.getActor() != local || local.getAnimation() == -1)
		{
			return;
		}
		if (local.getInteracting() instanceof NPC)
		{
			lastAttackWeapon = attackLabel();
			lastAttackTick = client.getTickCount();
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction();
		if (WidgetUtil.componentToInterface(event.getWidgetId()) == InterfaceID.AUTOCAST)
		{
			// Picking a spell in the autocast menu: remember its name until the game confirms the choice
			Widget widget = event.getWidget();
			String name = firstSpellName(widget == null ? null : widget.getName(), event.getMenuTarget(), event.getMenuOption());
			log.debug("Autocast menu click: option={} target={} -> {}", event.getMenuOption(), event.getMenuTarget(), name);
			if (name != null)
			{
				pendingAutocastName = name;
				pendingAutocastTick = client.getTickCount();
			}
			return;
		}

		if (action == MenuAction.WIDGET_TARGET_ON_NPC && "Cast".equalsIgnoreCase(Text.removeTags(event.getMenuOption())))
		{
			// Target looks like "Ice Barrage -> Vorkath  (level-732)"
			String target = Text.removeTags(event.getMenuTarget());
			int arrow = target.indexOf("->");
			String spell = (arrow > 0 ? target.substring(0, arrow) : target).trim();
			pendingCast = spell.isEmpty() ? null : spell;
		}
		else if (action == MenuAction.WALK
			|| action == MenuAction.NPC_FIRST_OPTION
			|| action == MenuAction.NPC_SECOND_OPTION
			|| action == MenuAction.NPC_THIRD_OPTION
			|| action == MenuAction.NPC_FOURTH_OPTION
			|| action == MenuAction.NPC_FIFTH_OPTION)
		{
			// Walking away or a normal attack cancels a manual cast that never happened
			pendingCast = null;
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// The game confirmed a new autocast spell right after you clicked one
		if (event.getVarbitId() == VarbitID.AUTOCAST_SPELL && pendingAutocastName != null
			&& client.getTickCount() - pendingAutocastTick <= 10)
		{
			learnAutocastName(event.getValue());
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Re-picking the spell that's already set doesn't change the game value, so also check
		// shortly after the click, once the autocast menu has closed (it closes on a successful pick).
		if (pendingAutocastName == null || client.getTickCount() - pendingAutocastTick < 2)
		{
			return;
		}
		Widget menu = client.getWidget(InterfaceID.Autocast.UNIVERSE);
		if (menu == null || menu.isHidden())
		{
			learnAutocastName(client.getVarbitValue(VarbitID.AUTOCAST_SPELL));
		}
		else if (client.getTickCount() - pendingAutocastTick > 10)
		{
			pendingAutocastName = null;
		}
	}

	private void learnAutocastName(int spellIndex)
	{
		String name = pendingAutocastName;
		pendingAutocastName = null;
		if (spellIndex <= 0 || name == null)
		{
			return;
		}
		autocastNames.put(spellIndex, name);
		configManager.setConfiguration(BossHitTrackerConfig.GROUP, AUTOCAST_NAMES_KEY, gson.toJson(autocastNames));
		log.debug("Learned autocast spell {} = {}", spellIndex, name);
		renameUnknownSpell(spellIndex, name);
	}

	/** Relabel hits saved before the spell's name was known, e.g. "Staff (Autocast spell 1)". */
	private void renameUnknownSpell(int spellIndex, String name)
	{
		String oldSuffix = "(Autocast spell " + spellIndex + ")";
		String newSuffix = "(" + name + ")";
		boolean changed = false;
		synchronized (lock)
		{
			for (BossStats stats : data.bosses.values())
			{
				changed |= renameKeys(stats.weapons, oldSuffix, newSuffix);
				for (KillRecord kill : stats.getRecentKills())
				{
					changed |= renameKeys(kill.weapons, oldSuffix, newSuffix);
				}
			}
			for (ActiveFight fight : activeFights.values())
			{
				renameKeys(fight.record.weapons, oldSuffix, newSuffix);
			}
			if (lastAttackWeapon != null && lastAttackWeapon.endsWith(oldSuffix))
			{
				lastAttackWeapon = lastAttackWeapon.substring(0, lastAttackWeapon.length() - oldSuffix.length()) + newSuffix;
			}
		}
		if (changed)
		{
			saveData();
			refreshPanel();
		}
	}

	private static boolean renameKeys(Map<String, WeaponStats> weapons, String oldSuffix, String newSuffix)
	{
		if (weapons == null)
		{
			return false;
		}
		List<String> matches = new ArrayList<>();
		for (String key : weapons.keySet())
		{
			if (key.endsWith(oldSuffix))
			{
				matches.add(key);
			}
		}
		for (String key : matches)
		{
			WeaponStats stats = weapons.remove(key);
			String renamed = key.substring(0, key.length() - oldSuffix.length()) + newSuffix;
			WeaponStats existing = weapons.get(renamed);
			if (existing == null)
			{
				weapons.put(renamed, stats);
			}
			else
			{
				existing.merge(stats);
			}
		}
		return !matches.isEmpty();
	}

	private static String firstSpellName(String... candidates)
	{
		for (String c : candidates)
		{
			if (c == null)
			{
				continue;
			}
			String name = Text.removeTags(c).trim();
			if (!name.isEmpty() && !"null".equalsIgnoreCase(name) && !NOT_SPELL_NAMES.contains(name.toLowerCase()))
			{
				return name;
			}
		}
		return null;
	}

	private void loadAutocastNames()
	{
		Map<Integer, String> loaded = null;
		String json = configManager.getConfiguration(BossHitTrackerConfig.GROUP, AUTOCAST_NAMES_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				loaded = gson.fromJson(json, new TypeToken<Map<Integer, String>>(){}.getType());
			}
			catch (RuntimeException e)
			{
				log.warn("Couldn't read saved autocast spell names", e);
			}
		}
		autocastNames = loaded == null ? new HashMap<>() : new HashMap<>(loaded);
	}

	/** What to call the attack you're starting now: the weapon, plus the spell if it's a spell. */
	private String attackLabel()
	{
		String weapon = currentWeapon();
		if (!config.trackSpells())
		{
			pendingCast = null;
			return weapon;
		}
		String spell = pendingCast;
		pendingCast = null;
		if (spell == null)
		{
			spell = autocastSpell();
		}
		return spell == null ? weapon : weapon + " (" + spell + ")";
	}

	/** The spell being autocast, or null when you aren't autocasting. */
	private String autocastSpell()
	{
		// Attack style slot 4 on a staff is autocast (normal or defensive)
		if (client.getVarpValue(VarPlayerID.COM_MODE) != AUTOCAST_STYLE)
		{
			return null;
		}
		int spellIndex = client.getVarbitValue(VarbitID.AUTOCAST_SPELL);
		String learned = autocastNames.get(spellIndex);
		if (learned != null)
		{
			return learned;
		}
		int spellObj = client.getVarpValue(VarPlayerID.AUTOCAST_SPELL_OBJ);
		if (spellObj > 0)
		{
			String name = itemManager.getItemComposition(spellObj).getName();
			if (name != null)
			{
				// Strip any leading spell-number prefix, e.g. "94 Ice Barrage"
				name = Text.removeTags(name).replaceFirst("^\\d+\\s+", "").trim();
				if (!name.isEmpty() && !"null".equalsIgnoreCase(name))
				{
					return name;
				}
			}
		}
		return spellIndex > 0 ? "Autocast spell " + spellIndex : null;
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Hitsplat hitsplat = event.getHitsplat();
		// Only your own hits: excludes other players, poison, venom, recoil, etc.
		if (!hitsplat.isMine() || !(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		String name = npcName(npc);
		if (name == null || !shouldTrack(npc, name))
		{
			return;
		}

		String weapon = weaponForHit();
		synchronized (lock)
		{
			ActiveFight fight = activeFights.get(npc);
			if (fight == null)
			{
				fight = new ActiveFight(name, System.currentTimeMillis());
				activeFights.put(npc, fight);
			}
			fight.record.addHit(weapon, hitsplat.getAmount());
			currentFight = fight;
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor actor = event.getActor();
		if (!(actor instanceof NPC))
		{
			return;
		}
		// Only mark the fight as won here. Hits can still land after the death (projectiles in
		// flight, multi-hit attacks), so the kill is saved once, when the NPC despawns.
		synchronized (lock)
		{
			ActiveFight fight = activeFights.get(actor);
			if (fight != null && !fight.died)
			{
				fight.died = true;
				fight.record.durationMs = System.currentTimeMillis() - fight.record.timestamp;
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		NPC npc = event.getNpc();
		ActiveFight fight;
		synchronized (lock)
		{
			fight = activeFights.remove(npc);
			if (fight == null)
			{
				return;
			}
			if (currentFight == fight)
			{
				currentFight = null;
			}
			// An NPC that despawns alive (you ran, it reset, you teleported) is not counted as a kill.
			if (!fight.died && !npc.isDead())
			{
				return;
			}

			if (!fight.died)
			{
				fight.record.durationMs = System.currentTimeMillis() - fight.record.timestamp;
			}
			BossStats stats = data.bosses.get(fight.boss);
			if (stats == null)
			{
				stats = new BossStats();
				data.bosses.put(fight.boss, stats);
			}
			stats.addKill(fight.record, config.historySize());
		}
		log.debug("Recorded {} kill: {} hits, {} damage", fight.boss, fight.record.getTotalHits(), fight.record.getTotalDamage());
		saveData();
		refreshPanel();
	}

	private boolean shouldTrack(NPC npc, String name)
	{
		String lower = name.toLowerCase();
		if (extraNames.contains(lower))
		{
			return true;
		}
		if (config.bossesOnly())
		{
			return BossList.isBoss(lower);
		}
		return npc.getCombatLevel() > 0;
	}

	private static String npcName(NPC npc)
	{
		NPCComposition comp = npc.getTransformedComposition();
		if (comp == null)
		{
			comp = npc.getComposition();
		}
		if (comp == null || comp.getName() == null)
		{
			return null;
		}
		String name = Text.removeTags(comp.getName()).trim();
		return name.isEmpty() || "null".equalsIgnoreCase(name) ? null : name;
	}

	private String weaponForHit()
	{
		if (lastAttackWeapon != null && client.getTickCount() - lastAttackTick <= ATTACK_WEAPON_TICKS)
		{
			return lastAttackWeapon;
		}
		return currentWeapon();
	}

	private String currentWeapon()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment == null)
		{
			return UNARMED;
		}
		Item item = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		if (item == null || item.getId() <= 0)
		{
			return UNARMED;
		}
		String name = itemManager.getItemComposition(item.getId()).getMembersName();
		return name == null ? UNARMED : name;
	}

	private void parseExtraNames()
	{
		Set<String> names = new HashSet<>();
		for (String part : config.extraNpcs().split(","))
		{
			String trimmed = part.trim().toLowerCase();
			if (!trimmed.isEmpty())
			{
				names.add(trimmed);
			}
		}
		extraNames = names;
	}

	// ---- Saving and loading (stored per RuneScape account) ----

	private void loadData()
	{
		HitData loaded = null;
		String json = configManager.getRSProfileConfiguration(BossHitTrackerConfig.GROUP, DATA_KEY);
		if (json != null && !json.isEmpty())
		{
			try
			{
				loaded = gson.fromJson(json, HitData.class);
			}
			catch (RuntimeException e)
			{
				log.warn("Couldn't read saved Boss Hit Tracker data", e);
			}
		}
		if (loaded == null || loaded.bosses == null)
		{
			loaded = new HitData();
		}
		synchronized (lock)
		{
			data = loaded;
			activeFights.clear();
			currentFight = null;
		}
		refreshPanel();
	}

	private void saveData()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		String json;
		synchronized (lock)
		{
			json = gson.toJson(data);
		}
		configManager.setRSProfileConfiguration(BossHitTrackerConfig.GROUP, DATA_KEY, json);
	}

	private void refreshPanel()
	{
		BossHitTrackerPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::rebuild);
		}
	}

	// ---- Used by the panel and overlay ----

	List<String> getBossNames()
	{
		synchronized (lock)
		{
			List<String> names = new ArrayList<>(data.bosses.keySet());
			names.sort(String.CASE_INSENSITIVE_ORDER);
			return names;
		}
	}

	BossStats getBossStats(String boss)
	{
		synchronized (lock)
		{
			BossStats stats = data.bosses.get(boss);
			return stats == null ? null : stats.copy();
		}
	}

	/** Snapshot of the fight shown in the overlay, or null when you aren't fighting a tracked boss. */
	ActiveFight getCurrentFightSnapshot()
	{
		synchronized (lock)
		{
			if (currentFight == null)
			{
				return null;
			}
			ActiveFight copy = new ActiveFight(currentFight.boss, currentFight.record.timestamp);
			copy.record.weapons.putAll(currentFight.record.copy().weapons);
			return copy;
		}
	}

	void resetBoss(String boss)
	{
		synchronized (lock)
		{
			data.bosses.remove(boss);
		}
		saveData();
		refreshPanel();
	}

	void resetAll()
	{
		synchronized (lock)
		{
			data.bosses.clear();
		}
		saveData();
		refreshPanel();
	}

	String exportCsv()
	{
		StringBuilder sb = new StringBuilder("Boss,Kills,Weapon,Hits,Zero hits,Total damage,Average hit,Max hit\n");
		synchronized (lock)
		{
			List<String> names = new ArrayList<>(data.bosses.keySet());
			names.sort(String.CASE_INSENSITIVE_ORDER);
			for (String boss : names)
			{
				BossStats stats = data.bosses.get(boss);
				for (Map.Entry<String, WeaponStats> e : stats.getWeapons().entrySet())
				{
					WeaponStats w = e.getValue();
					sb.append(csv(boss)).append(',')
						.append(stats.kills).append(',')
						.append(csv(e.getKey())).append(',')
						.append(w.hits).append(',')
						.append(w.zeros).append(',')
						.append(w.damage).append(',')
						.append(String.format("%.2f", w.getAverageHit())).append(',')
						.append(w.maxHit).append('\n');
				}
			}
		}
		return sb.toString();
	}

	private static String csv(String s)
	{
		return "\"" + s.replace("\"", "\"\"") + "\"";
	}

	private static BufferedImage createIcon()
	{
		// A small red hitsplat
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(170, 20, 20));
		g.fillOval(1, 1, 14, 14);
		g.setColor(new Color(240, 240, 240));
		g.setStroke(new BasicStroke(1.6f));
		g.drawLine(5, 8, 11, 8);
		g.drawLine(8, 5, 8, 11);
		g.dispose();
		return img;
	}
}
