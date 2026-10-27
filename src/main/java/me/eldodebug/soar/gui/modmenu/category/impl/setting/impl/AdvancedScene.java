package me.eldodebug.soar.gui.modmenu.category.impl.setting.impl;

import java.awt.Color;
import java.util.ArrayList;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.gui.modmenu.category.impl.SettingCategory;
import me.eldodebug.soar.gui.modmenu.category.impl.setting.SettingScene;
import me.eldodebug.soar.management.color.palette.ColorPalette;
import me.eldodebug.soar.management.color.palette.ColorType;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.misc.Favourites;
import me.eldodebug.soar.management.misc.Recents;
import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.impl.InternalSettingsMod;
import me.eldodebug.soar.management.mods.settings.Setting;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.management.nanovg.font.LegacyIcon;
import me.eldodebug.soar.management.notification.NotificationType;
import me.eldodebug.soar.utils.mouse.MorphCursor;
import me.eldodebug.soar.utils.mouse.MouseUtils;

/**
 * Destructive-but-recoverable maintenance actions, each behind a two-step confirmation.
 */
public class AdvancedScene extends SettingScene {

	private static final float ROW_HEIGHT = 41.0F;
	private static final float ROW_STEP = 51.0F;
	private static final float BUTTON_WIDTH = 58.0F;
	private static final float BUTTON_HEIGHT = 18.0F;
	private static final long CONFIRM_WINDOW_MS = 4000L;

	private int confirmAction = -1;
	private long confirmAt;

	public AdvancedScene(SettingCategory parent) {
		super(parent, TranslateText.ADVANCED, TranslateText.ADVANCED_DESCRIPTION, LegacyIcon.SLIDERS);
	}

	@Override
	public void initGui() {
		confirmAction = -1;
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {

		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		ColorPalette palette = instance.getColorManager().getPalette();

		if(confirmAction >= 0 && System.currentTimeMillis() - confirmAt > CONFIRM_WINDOW_MS) {
			confirmAction = -1;
		}

		drawRow(nvg, palette, mouseX, mouseY, 0, "Reset all module settings",
				"Restore every module and setting to its default",
				confirmAction == 0 ? "Confirm" : "Reset", true);
		drawRow(nvg, palette, mouseX, mouseY, 1, "Disable all modules",
				"Turn every module off without touching its settings",
				confirmAction == 1 ? "Confirm" : "Disable", true);
		drawRow(nvg, palette, mouseX, mouseY, 2, "Clear pinned modules",
				"Remove every module from the pinned section", "Clear", false);
		drawRow(nvg, palette, mouseX, mouseY, 3, "Clear recent modules",
				"Forget the modules used this session", "Clear", false);
		drawRow(nvg, palette, mouseX, mouseY, 4, "Clear notifications",
				"Empty the notification history", "Clear", false);
	}

	private void drawRow(NanoVGManager nvg, ColorPalette palette, int mouseX, int mouseY, int index,
			String title, String description, String buttonLabel, boolean danger) {

		float rowY = this.getY() + (index * ROW_STEP);
		float buttonX = this.getX() + this.getWidth() - BUTTON_WIDTH - 8;
		float buttonY = rowY + ((ROW_HEIGHT - BUTTON_HEIGHT) / 2.0F);

		nvg.drawRoundedRect(this.getX(), rowY, this.getWidth(), ROW_HEIGHT, 6, palette.getBackgroundColor(ColorType.DARK));
		nvg.drawText(title, this.getX() + 8, rowY + 9.5F, palette.getFontColor(ColorType.DARK), 12.5F, Fonts.MEDIUM);
		nvg.drawText(description, this.getX() + 8, rowY + 23.5F, palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

		boolean hovered = MouseUtils.isInside(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);

		Color fill = danger ? new Color(220, 70, 70, hovered ? 220 : 180)
				: palette.getBackgroundColor(ColorType.NORMAL);

		nvg.drawRoundedRect(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, 5, fill);
		nvg.drawCenteredText(buttonLabel, buttonX + (BUTTON_WIDTH / 2.0F), buttonY + 5.0F,
				danger ? Color.WHITE : palette.getFontColor(ColorType.DARK), 8, Fonts.MEDIUM);

		MorphCursor.getInstance().hover(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, 5,
				confirmAction == index ? "Click again to confirm" : title);
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int mouseButton) {

		if(mouseButton != 0) {
			return;
		}

		for(int i = 0; i < 5; i++) {

			float rowY = this.getY() + (i * ROW_STEP);
			float buttonX = this.getX() + this.getWidth() - BUTTON_WIDTH - 8;
			float buttonY = rowY + ((ROW_HEIGHT - BUTTON_HEIGHT) / 2.0F);

			if(!MouseUtils.isInside(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
				continue;
			}

			if(i <= 1) {
				handleConfirm(i);
			} else if(i == 2) {
				Favourites.clear();
				Glide.getInstance().getNotificationManager().post("Pinned modules", "Cleared", NotificationType.SUCCESS);
			} else if(i == 3) {
				Recents.clear();
				Glide.getInstance().getNotificationManager().post("Recent modules", "Cleared", NotificationType.SUCCESS);
			} else if(i == 4) {
				Glide.getInstance().getNotificationManager().clearHistory();
			}

			return;
		}
	}

	private void handleConfirm(int action) {

		long now = System.currentTimeMillis();

		if(confirmAction != action || now - confirmAt > CONFIRM_WINDOW_MS) {
			confirmAction = action;
			confirmAt = now;
			Glide.getInstance().getNotificationManager().post(
					action == 0 ? "Reset all settings" : "Disable all modules",
					"Click Confirm within 4 seconds", NotificationType.WARNING);
			return;
		}

		confirmAction = -1;

		if(action == 0) {
			resetAllSettings();
		} else {
			disableAllModules();
		}
	}

	private void resetAllSettings() {

		ArrayList<Mod> mods = Glide.getInstance().getModManager().getMods();
		int reset = 0;

		for(Mod mod : mods) {

			if(mod instanceof InternalSettingsMod) {
				continue;
			}

			ArrayList<Setting> settings = Glide.getInstance().getModManager().getSettingsByMod(mod);

			if(settings == null) {
				continue;
			}

			for(Setting setting : settings) {
				setting.reset();
			}

			reset++;
		}

		Glide.getInstance().getNotificationManager().post("Module settings",
				"Reset " + reset + " modules to defaults", NotificationType.SUCCESS);
	}

	private void disableAllModules() {

		ArrayList<Mod> mods = Glide.getInstance().getModManager().getMods();
		int disabled = 0;

		for(Mod mod : mods) {

			if(mod instanceof InternalSettingsMod || !mod.isToggled()) {
				continue;
			}

			mod.setToggled(false);
			disabled++;
		}

		Glide.getInstance().getNotificationManager().post("Modules",
				"Disabled " + disabled + " modules", NotificationType.SUCCESS);
	}
}
