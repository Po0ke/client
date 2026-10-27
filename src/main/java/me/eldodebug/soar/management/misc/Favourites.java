package me.eldodebug.soar.management.misc;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.google.gson.Gson;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.logger.GlideLogger;
import me.eldodebug.soar.management.mods.Mod;

/**
 * Pinned modules. Deliberately stored in its own file instead of the profile system: pins are
 * a personal preference about the interface, not part of a config that gets swapped per server.
 */
public class Favourites {

	private static Set<String> keys;

	private Favourites() {
	}

	private static File file() {
		return new File(Glide.getInstance().getFileManager().getGlideDir(), "favourites.json");
	}

	private static Set<String> keys() {

		if (keys == null) {
			load();
		}

		return keys;
	}

	private static void load() {

		keys = new LinkedHashSet<String>();

		File file = file();

		if (!file.exists()) {
			return;
		}

		try (FileReader reader = new FileReader(file)) {

			String[] stored = new Gson().fromJson(reader, String[].class);

			if (stored != null) {
				Collections.addAll(keys, stored);
			}
		} catch (Exception e) {
			GlideLogger.error("Failed to load favourite modules", e);
		}
	}

	private static void save() {

		try (FileWriter writer = new FileWriter(file())) {
			new Gson().toJson(keys, writer);
		} catch (Exception e) {
			GlideLogger.error("Failed to save favourite modules", e);
		}
	}

	public static boolean isPinned(Mod mod) {
		return keys().contains(mod.getNameKey());
	}

	public static void toggle(Mod mod) {

		if (!keys().add(mod.getNameKey())) {
			keys().remove(mod.getNameKey());
		}

		save();
	}
	
	public static void clear() {
		keys().clear();
		save();
	}
}
