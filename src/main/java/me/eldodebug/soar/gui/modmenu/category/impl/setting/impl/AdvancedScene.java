package me.eldodebug.soar.gui.modmenu.category.impl.setting.impl;

import java.awt.Color;
import java.util.ArrayList;

import org.lwjgl.input.Mouse;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.gui.modmenu.category.impl.SettingCategory;
import me.eldodebug.soar.gui.modmenu.category.impl.setting.SettingScene;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.color.palette.ColorPalette;
import me.eldodebug.soar.management.color.palette.ColorType;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.misc.ClickTripler;
import me.eldodebug.soar.management.misc.Favourites;
import me.eldodebug.soar.management.misc.Recents;
import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.impl.InternalSettingsMod;
import me.eldodebug.soar.management.mods.settings.Setting;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.management.nanovg.font.LegacyIcon;
import me.eldodebug.soar.management.notification.NotificationType;
import me.eldodebug.soar.ui.comp.impl.CompSlider;
import me.eldodebug.soar.ui.comp.impl.CompToggleButton;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.mouse.MorphCursor;
import me.eldodebug.soar.utils.mouse.MouseUtils;
import me.eldodebug.soar.utils.mouse.Scroll;
import net.minecraft.client.gui.GuiScreen;

/**
 * Maintenance actions, cursor preferences and the optional click-tripler helper.
 *
 * The scene scrolls as one column instead of hiding controls in a separate rail entry. The
 * destructive actions use the cursor's hold ring; the helper controls stay at the bottom where
 * they belong.
 */
public class AdvancedScene extends SettingScene {

	private static final float ROW_HEIGHT = 41.0F;
	private static final float ROW_STEP = 51.0F;
	private static final float BUTTON_WIDTH = 58.0F;
	private static final float BUTTON_HEIGHT = 18.0F;
	private static final float STATUS_HEIGHT = 104.0F;
	private static final long CONFIRM_WINDOW_MS = 4000L;
	private static final long HOLD_NANOS = 650_000_000L;

	private static final int ROW_RESET = 0;
	private static final int ROW_DISABLE = 1;
	private static final int ROW_CLEAR_PINS = 2;
	private static final int ROW_CLEAR_RECENTS = 3;
	private static final int ROW_CLEAR_NOTIFICATIONS = 4;
	private static final int ROW_MAGNET = 5;
	private static final int ROW_MAGNET_STRENGTH = 6;
	private static final int ROW_HOLD = 7;
	private static final int ROW_TRIPLER = 8;
	private static final int ROW_TRIPLER_MIN = 9;
	private static final int ROW_TRIPLER_MAX = 10;
	private static final int ROW_STATUS = 11;

	private final Scroll scroll = new Scroll();

	private CompToggleButton magnetToggle;
	private CompSlider magnetSlider;
	private CompToggleButton holdToggle;
	private CompToggleButton triplerToggle;
	private CompSlider triplerMin;
	private CompSlider triplerMax;

	private int confirmAction = -1;
	private long confirmAt;

	private int holdAction = -1;
	private boolean holdTriggered;
	private long holdStartedAt;
	private float holdProgress;

	private float copyX, copyY, copyWidth, copyHeight;
	private boolean interactive;

	public AdvancedScene(SettingCategory parent) {
		super(parent, TranslateText.ADVANCED, TranslateText.ADVANCED_DESCRIPTION, LegacyIcon.SLIDERS);
	}

	@Override
	public void initGui() {

		InternalSettingsMod settings = InternalSettingsMod.getInstance();

		magnetToggle = new CompToggleButton(settings.getMagneticCursorSetting());
		magnetSlider = new CompSlider(settings.getMagnetStrengthSetting());
		holdToggle = new CompToggleButton(settings.getHoldToConfirmSetting());
		triplerToggle = new CompToggleButton(settings.getClickTriplerSetting());
		triplerMin = new CompSlider(settings.getClickTriplerMinDelay());
		triplerMax = new CompSlider(settings.getClickTriplerMaxDelay());

		magnetSlider.setCircle(true);
		triplerMin.setCircle(true);
		triplerMax.setCircle(true);

		confirmAction = -1;
		cancelHold();
		scroll.resetAll();
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {

		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		ColorPalette palette = instance.getColorManager().getPalette();
		AccentColor accent = instance.getColorManager().getCurrentColor();
		InternalSettingsMod settings = InternalSettingsMod.getInstance();

		if(confirmAction >= 0 && System.currentTimeMillis() - confirmAt > CONFIRM_WINDOW_MS) {
			confirmAction = -1;
		}

		float sceneX = this.getX();
		float sceneY = this.getY();
		float sceneWidth = this.getWidth();
		float sceneHeight = this.getHeight();
		interactive = getParent().isSceneSettled();

		if(MouseUtils.isInside(mouseX, mouseY, sceneX, sceneY, sceneWidth, sceneHeight)) {
			scroll.onScroll();
		}

		scroll.onAnimation();

		float contentHeight = (ROW_STATUS * ROW_STEP) + STATUS_HEIGHT + 10.0F;
		scroll.setMaxScroll(Math.max(0.0F, contentHeight - sceneHeight));

		updateHold(mouseX, mouseY);

		if(interactive && holdAction >= 0) {
			MorphCursor.getInstance().setHoldProgress(holdProgress);
		}

		int adjustedMouseY = (int) (mouseY - scroll.getValue());

		nvg.save();
		nvg.scissor(sceneX, sceneY, sceneWidth, sceneHeight);
		nvg.translate(0, scroll.getValue());

		if(interactive) {
			MorphCursor.getInstance().pushClip(sceneX, sceneY, sceneWidth, sceneHeight);
		}

		boolean holdEnabled = settings.getHoldToConfirmSetting().isToggled();

		drawActionRow(nvg, palette, mouseX, mouseY, ROW_RESET, "Reset all module settings",
				"Restore every module and setting to its default",
				holdEnabled ? "Hold" : (confirmAction == 0 ? "Confirm" : "Reset"), true, holdEnabled);
		drawActionRow(nvg, palette, mouseX, mouseY, ROW_DISABLE, "Disable all modules",
				"Turn every module off without touching its settings",
				holdEnabled ? "Hold" : (confirmAction == 1 ? "Confirm" : "Disable"), true, holdEnabled);
		drawActionRow(nvg, palette, mouseX, mouseY, ROW_CLEAR_PINS, "Clear pinned modules",
				"Remove every module from the pinned section", "Clear", false, false);
		drawActionRow(nvg, palette, mouseX, mouseY, ROW_CLEAR_RECENTS, "Clear recent modules",
				"Forget the modules used this session", "Clear", false, false);
		drawActionRow(nvg, palette, mouseX, mouseY, ROW_CLEAR_NOTIFICATIONS, "Clear notifications",
				"Empty the notification history", "Clear", false, false);

		drawToggleRow(nvg, palette, mouseX, adjustedMouseY, partialTicks, ROW_MAGNET, magnetToggle,
				TranslateText.MAGNETIC_CURSOR.getText(), TranslateText.MAGNETIC_CURSOR_DESCRIPTION.getText());

		drawSliderRow(nvg, palette, mouseX, adjustedMouseY, partialTicks, ROW_MAGNET_STRENGTH, magnetSlider,
				TranslateText.MAGNET_STRENGTH.getText(), "How strongly the ring is pulled toward nearby controls",
				Math.round(settings.getMagnetStrengthSetting().getValueFloat() * 100.0F) + "%");

		drawToggleRow(nvg, palette, mouseX, adjustedMouseY, partialTicks, ROW_HOLD, holdToggle,
				TranslateText.HOLD_TO_CONFIRM.getText(), TranslateText.HOLD_TO_CONFIRM_DESCRIPTION.getText());

		drawToggleRow(nvg, palette, mouseX, adjustedMouseY, partialTicks, ROW_TRIPLER, triplerToggle,
				TranslateText.CLICK_TRIPLER.getText(), "Adds a third click when two clicks land close together");

		drawSliderRow(nvg, palette, mouseX, adjustedMouseY, partialTicks, ROW_TRIPLER_MIN, triplerMin,
				TranslateText.TRIPLER_MIN_DELAY.getText(), "Shortest wait before the extra click",
				settings.getClickTriplerMinDelay().getValueInt() + " ms");

		drawSliderRow(nvg, palette, mouseX, adjustedMouseY, partialTicks, ROW_TRIPLER_MAX, triplerMax,
				TranslateText.TRIPLER_MAX_DELAY.getText(), "Longest wait before the extra click",
				settings.getClickTriplerMaxDelay().getValueInt() + " ms");

		drawStatusCard(nvg, palette, mouseX, mouseY);

		if(interactive) {
			MorphCursor.getInstance().popClip();
		}

		nvg.restore();

		drawScrollbar(nvg, accent, sceneX, sceneY, sceneWidth, sceneHeight);
	}

	private float rowTop(int index) {
		return this.getY() + (index * ROW_STEP);
	}

	private void drawActionRow(NanoVGManager nvg, ColorPalette palette, int mouseX, int mouseY, int index,
			String title, String description, String buttonLabel, boolean danger, boolean holdable) {

		float rowY = rowTop(index);
		float buttonX = this.getX() + this.getWidth() - BUTTON_WIDTH - 8;
		float buttonY = rowY + ((ROW_HEIGHT - BUTTON_HEIGHT) / 2.0F);

		nvg.drawRoundedRect(this.getX(), rowY, this.getWidth(), ROW_HEIGHT, 6, palette.getBackgroundColor(ColorType.DARK));
		nvg.drawText(title, this.getX() + 8, rowY + 9.5F, palette.getFontColor(ColorType.DARK), 12.5F, Fonts.MEDIUM);
		nvg.drawText(description, this.getX() + 8, rowY + 23.5F, palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

		boolean hovered = MouseUtils.isInside(mouseX, mouseY, buttonX, buttonY + scroll.getValue(), BUTTON_WIDTH, BUTTON_HEIGHT);
		boolean holding = holdable && holdAction == index;

		Color fill = danger ? new Color(220, 70, 70, hovered || holding ? 220 : 180)
				: palette.getBackgroundColor(ColorType.NORMAL);

		nvg.drawRoundedRect(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT, 5, fill);

		if(holding) {
			nvg.drawArc(buttonX + (BUTTON_WIDTH / 2.0F), buttonY + (BUTTON_HEIGHT / 2.0F), 7.5F,
					-90.0F, -90.0F + (360.0F * holdProgress), 2.0F, Color.WHITE);
		}

		nvg.drawCenteredText(buttonLabel, buttonX + (BUTTON_WIDTH / 2.0F), buttonY + 5.0F,
				danger ? Color.WHITE : palette.getFontColor(ColorType.DARK), 8, Fonts.MEDIUM);

		String tooltip = holdable
				? (holding ? "Keep holding to confirm" : "Hold to " + title.toLowerCase())
				: title;

		if(interactive) {
			MorphCursor.getInstance().hover(buttonX, buttonY + scroll.getValue(), BUTTON_WIDTH, BUTTON_HEIGHT, 5, tooltip);
		}
	}

	private void drawToggleRow(NanoVGManager nvg, ColorPalette palette, int mouseX, int mouseY, float partialTicks,
			int index, CompToggleButton toggle, String title, String description) {

		float rowY = rowTop(index);
		float toggleX = this.getX() + this.getWidth() - 44;

		nvg.drawRoundedRect(this.getX(), rowY, this.getWidth(), ROW_HEIGHT, 6, palette.getBackgroundColor(ColorType.DARK));
		nvg.drawText(title, this.getX() + 8, rowY + 9.5F, palette.getFontColor(ColorType.DARK), 12.5F, Fonts.MEDIUM);
		nvg.drawText(description, this.getX() + 8, rowY + 23.5F, palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

		toggle.setX(toggleX);
		toggle.setY(rowY + 12.5F);
		toggle.draw(mouseX, mouseY, partialTicks);

		if(interactive) {
			MorphCursor.getInstance().hover(toggleX, rowY + 12.5F + scroll.getValue(), 34, 16, 7, title);
		}
	}

	private void drawSliderRow(NanoVGManager nvg, ColorPalette palette, int mouseX, int mouseY, float partialTicks,
			int index, CompSlider slider, String title, String description, String valueText) {

		float rowY = rowTop(index);
		float sliderX = this.getX() + this.getWidth() - 128;

		nvg.drawRoundedRect(this.getX(), rowY, this.getWidth(), ROW_HEIGHT, 6, palette.getBackgroundColor(ColorType.DARK));
		nvg.drawText(title, this.getX() + 8, rowY + 9.5F, palette.getFontColor(ColorType.DARK), 12.5F, Fonts.MEDIUM);
		nvg.drawText(description, this.getX() + 8, rowY + 23.5F, palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

		nvg.drawText(valueText, this.getX() + this.getWidth() - 8 - nvg.getTextWidth(valueText, 9, Fonts.MEDIUM),
				rowY + 9.5F, palette.getFontColor(ColorType.NORMAL), 9, Fonts.MEDIUM);

		slider.setX(sliderX);
		slider.setY(rowY + 27);
		slider.setWidth(112);
		slider.draw(mouseX, mouseY, partialTicks);

		if(interactive) {
			MorphCursor.getInstance().hover(sliderX - 4, rowY + 23 + scroll.getValue(), 120, 12, 4, title);
		}
	}

	private void drawStatusCard(NanoVGManager nvg, ColorPalette palette, int mouseX, int mouseY) {

		float rowY = rowTop(ROW_STATUS);
		float width = this.getWidth();

		nvg.drawRoundedRect(this.getX(), rowY, width, STATUS_HEIGHT, 6, palette.getBackgroundColor(ColorType.DARK));

		boolean supported = ClickTripler.isSupported();
		boolean running = supported && ClickTripler.isRunning();
		boolean misconfigured = InternalSettingsMod.getInstance().getClickTriplerMinDelay().getValueInt()
				> InternalSettingsMod.getInstance().getClickTriplerMaxDelay().getValueInt();

		String statusText;
		Color statusColor;

		if(!supported) {
			statusText = "Helper is only available on Linux";
			statusColor = new Color(232, 168, 88);
		} else if(running) {
			statusText = "Helper detected";
			statusColor = new Color(96, 200, 124);
		} else {
			statusText = "Helper not running";
			statusColor = new Color(232, 168, 88);
		}

		nvg.drawText(statusText, this.getX() + 8, rowY + 9.5F, statusColor, 12.5F, Fonts.MEDIUM);

		String detail;

		if(!supported) {
			detail = "This optional helper uses Linux raw input and is not started by the client.";
		} else if(misconfigured) {
			detail = "Minimum is above maximum, the helper clamps it.";
		} else if(running) {
			detail = "Changes apply live. The toggle above controls the helper through its state file.";
		} else {
			detail = "Run the command below once in a terminal, leave it running, then use the toggle.";
		}

		nvg.drawText(detail, this.getX() + 8, rowY + 23.5F, palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

		String command = ClickTripler.getSuggestedCommand();

		nvg.drawText("Start command", this.getX() + 8, rowY + 38.0F, palette.getFontColor(ColorType.NORMAL), 7.0F, Fonts.MEDIUM);
		nvg.drawText(nvg.getLimitText(command, 6.6F, Fonts.REGULAR, width - 92.0F), this.getX() + 8, rowY + 49.0F,
				palette.getFontColor(ColorType.DARK), 6.6F, Fonts.REGULAR);

		nvg.drawText("State file: " + ClickTripler.getStatePath(), this.getX() + 8, rowY + 62.0F,
				palette.getFontColor(ColorType.NORMAL), 6.4F, Fonts.REGULAR);

		nvg.drawText("The helper only adds the extra click; the client never starts privileged processes.",
				this.getX() + 8, rowY + 75.0F, palette.getFontColor(ColorType.NORMAL), 6.4F, Fonts.REGULAR);

		nvg.drawText("Copy the command, run it once with sudo, then use the toggle above.",
				this.getX() + 8, rowY + 87.0F, palette.getFontColor(ColorType.NORMAL), 6.4F, Fonts.REGULAR);

		copyWidth = 66.0F;
		copyHeight = 16.0F;
		copyX = this.getX() + width - copyWidth - 8;
		copyY = rowY + 42.0F;

		boolean hovered = MouseUtils.isInside(mouseX, mouseY, copyX, copyY + scroll.getValue(), copyWidth, copyHeight);

		nvg.drawRoundedRect(copyX, copyY, copyWidth, copyHeight, 5,
				hovered ? ColorUtils.applyAlpha(Glide.getInstance().getColorManager().getCurrentColor().getColor1(), 70)
						: palette.getBackgroundColor(ColorType.NORMAL));
		nvg.drawCenteredText("Copy command", copyX + (copyWidth / 2.0F), copyY + 4.5F,
				hovered ? Color.WHITE : palette.getFontColor(ColorType.DARK), 7.0F, Fonts.MEDIUM);

		if(interactive) {
			MorphCursor.getInstance().hover(copyX, copyY + scroll.getValue(), copyWidth, copyHeight, 5, "Copy the start command");
		}
	}

	private void drawScrollbar(NanoVGManager nvg, AccentColor accent, float sceneX, float sceneY, float sceneWidth, float sceneHeight) {

		float max = scroll.getMaxScroll();

		if(max <= 0.0F) {
			return;
		}

		float trackX = sceneX + sceneWidth - 4.0F;
		float trackY = sceneY + 4.0F;
		float trackHeight = sceneHeight - 8.0F;
		float content = trackHeight + max;
		float thumbHeight = Math.max(20.0F, trackHeight * (trackHeight / content));
		float progress = Math.max(0.0F, Math.min(1.0F, -scroll.getValue() / max));
		float thumbY = trackY + (progress * (trackHeight - thumbHeight));

		nvg.drawRoundedRect(trackX, trackY, 2.0F, trackHeight, 1.0F, new Color(255, 255, 255, 18));
		nvg.drawGradientRoundedRect(trackX, thumbY, 2.0F, thumbHeight, 1.0F,
				ColorUtils.applyAlpha(accent.getColor1(), 160), ColorUtils.applyAlpha(accent.getColor2(), 160));
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int mouseButton) {

		if(mouseButton != 0) {
			return;
		}

		if(!MouseUtils.isInside(mouseX, mouseY, this.getX(), this.getY(), this.getWidth(), this.getHeight())) {
			return;
		}

		if(MouseUtils.isInside(mouseX, mouseY, copyX, copyY + scroll.getValue(), copyWidth, copyHeight)) {
			GuiScreen.setClipboardString(ClickTripler.getSuggestedCommand());
			Glide.getInstance().getNotificationManager().post("Click Tripler",
					"Start command copied to the clipboard", NotificationType.SUCCESS);
			return;
		}

		InternalSettingsMod settings = InternalSettingsMod.getInstance();
		int adjustedMouseY = (int) (mouseY - scroll.getValue());

		magnetToggle.mouseClicked(mouseX, adjustedMouseY, mouseButton);
		magnetSlider.mouseClicked(mouseX, adjustedMouseY, mouseButton);
		holdToggle.mouseClicked(mouseX, adjustedMouseY, mouseButton);
		triplerToggle.mouseClicked(mouseX, adjustedMouseY, mouseButton);
		triplerMin.mouseClicked(mouseX, adjustedMouseY, mouseButton);
		triplerMax.mouseClicked(mouseX, adjustedMouseY, mouseButton);

		for(int i = 0; i <= 4; i++) {

			float rowY = rowTop(i);
			float buttonX = this.getX() + this.getWidth() - BUTTON_WIDTH - 8;
			float buttonY = rowY + ((ROW_HEIGHT - BUTTON_HEIGHT) / 2.0F) + scroll.getValue();

			if(!MouseUtils.isInside(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
				continue;
			}

			if(i <= 1) {
				if(settings.getHoldToConfirmSetting().isToggled()) {
					startHold(i);
				} else {
					handleConfirm(i);
				}
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

	@Override
	public void mouseReleased(int mouseX, int mouseY, int mouseButton) {

		magnetSlider.mouseReleased(mouseX, mouseY, mouseButton);
		triplerMin.mouseReleased(mouseX, mouseY, mouseButton);
		triplerMax.mouseReleased(mouseX, mouseY, mouseButton);
	}

	private void startHold(int action) {
		holdAction = action;
		holdTriggered = false;
		holdStartedAt = System.nanoTime();
		holdProgress = 0.0F;
	}

	private void cancelHold() {
		holdAction = -1;
		holdTriggered = false;
		holdProgress = 0.0F;
	}

	private void updateHold(int mouseX, int mouseY) {

		if(holdAction < 0) {
			return;
		}

		if(!Mouse.isButtonDown(0)) {
			cancelHold();
			return;
		}

		float buttonX = this.getX() + this.getWidth() - BUTTON_WIDTH - 8;
		float buttonY = rowTop(holdAction) + ((ROW_HEIGHT - BUTTON_HEIGHT) / 2.0F) + scroll.getValue();

		if(!MouseUtils.isInside(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
			cancelHold();
			return;
		}

		if(holdTriggered) {
			return;
		}

		holdProgress = Math.min(1.0F, (System.nanoTime() - holdStartedAt) / (float) HOLD_NANOS);

		if(holdProgress >= 1.0F) {
			holdTriggered = true;

			int action = holdAction;

			holdAction = -1;
			holdProgress = 0.0F;

			if(action == 0) {
				resetAllSettings();
			} else {
				disableAllModules();
			}
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
