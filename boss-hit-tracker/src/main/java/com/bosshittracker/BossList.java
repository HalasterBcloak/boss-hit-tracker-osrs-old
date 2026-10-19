package com.bosshittracker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Built-in list of boss NPC names (lower case). Anything missing can be added
 * with the "Extra NPCs to track" setting.
 */
final class BossList
{
	private static final Set<String> BOSSES = new HashSet<>(Arrays.asList(
		// Slayer and wilderness bosses
		"abyssal sire", "alchemical hydra", "araxxor", "cerberus", "kraken", "thermonuclear smoke devil",
		"grotesque guardians", "dusk", "dawn", "artio", "callisto", "calvar'ion", "vet'ion", "spindel",
		"venenatis", "chaos elemental", "chaos fanatic", "crazy archaeologist", "scorpia", "king black dragon",
		// God Wars Dungeon
		"general graardor", "commander zilyana", "kree'arra", "k'ril tsutsaroth", "nex",
		// Desert Treasure II
		"duke sucellus", "the leviathan", "the whisperer", "vardorvis",
		// Varlamore and newer bosses
		"sol heredit", "amoxliatl", "the hueycoatl", "eldric the ice king", "branda the fire queen",
		"yama", "doom of mokhaiotl",
		// Other bosses
		"barrows", "ahrim the blighted", "dharok the wretched", "guthan the infested", "karil the tainted",
		"torag the corrupted", "verac the defiled", "bryophyta", "obor", "corporeal beast",
		"dagannoth prime", "dagannoth rex", "dagannoth supreme", "deranged archaeologist", "giant mole",
		"hespori", "kalphite queen", "phantom muspah", "sarachnis", "scurrius", "skotizo", "vorkath",
		"zulrah", "zalcano", "the nightmare", "phosani's nightmare", "tztok-jad", "tzkal-zuk",
		"crystalline hunllef", "corrupted hunllef", "the mimic", "mimic",
		// Chambers of Xeric
		"great olm", "great olm left claw", "great olm right claw", "tekton", "tekton (enraged)",
		"vasa nistirio", "vespula", "vanguard", "muttadile", "ice demon", "skeletal mystic", "lizardman shaman",
		// Theatre of Blood
		"the maiden of sugadinti", "pestilent bloat", "nylocas vasilias", "sotetseg", "xarpus", "verzik vitur",
		// Tombs of Amascut
		"ba-ba", "kephri", "zebak", "akkha", "tumeken's warden", "elidinis' warden"
	));

	private BossList()
	{
	}

	static boolean isBoss(String lowerCaseName)
	{
		return BOSSES.contains(lowerCaseName);
	}
}
