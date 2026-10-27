package me.eldodebug.soar.management.misc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.ModManager;

/**
 * Last few modules touched in this session. In memory on purpose: a fresh start should not show
 * a stale "recent" list from three days ago.
 */
public class Recents {

	private static final int MAX_ENTRIES = 5;

	private static final ArrayDeque<String> keys = new ArrayDeque<String>();

	private Recents() {
	}

	public static void push(Mod mod) {

		keys.remove(mod.getNameKey());
		keys.addFirst(mod.getNameKey());

		while (keys.size() > MAX_ENTRIES) {
			keys.removeLast();
		}
	}

	public static boolean contains(Mod mod) {
		return keys.contains(mod.getNameKey());
	}

	public static List<Mod> resolve(ModManager modManager) {

		ArrayList<Mod> result = new ArrayList<Mod>();

		for (String key : keys) {

			Mod mod = modManager.getModByTranslateKey(key);

			if (mod != null) {
				result.add(mod);
			}
		}

		return result;
	}
}
