package me.eldodebug.soar.gui.modmenu.category.impl.setting.impl;

import java.awt.Color;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.gui.modmenu.category.impl.SettingCategory;
import me.eldodebug.soar.gui.modmenu.category.impl.setting.SettingScene;
import me.eldodebug.soar.management.color.ColorManager;
import me.eldodebug.soar.management.color.palette.ColorPalette;
import me.eldodebug.soar.management.color.palette.ColorType;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.misc.ClickTripler;
import me.eldodebug.soar.management.mods.impl.InternalSettingsMod;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.management.nanovg.font.LegacyIcon;
import me.eldodebug.soar.ui.comp.impl.CompSlider;
import me.eldodebug.soar.ui.comp.impl.CompToggleButton;
import me.eldodebug.soar.utils.mouse.MorphCursor;

public class ClickTriplerScene extends SettingScene {

	private CompToggleButton enabledToggle;
	private CompSlider minSlider;
	private CompSlider maxSlider;

	public ClickTriplerScene(SettingCategory parent) {
		super(parent, TranslateText.CLICK_TRIPLER, TranslateText.CLICK_TRIPLER_DESCRIPTION, LegacyIcon.MOUSE_POINTER);
	}

	@Override
	public void initGui() {

		InternalSettingsMod settings = InternalSettingsMod.getInstance();

		enabledToggle = new CompToggleButton(settings.getClickTriplerSetting());
		minSlider = new CompSlider(settings.getClickTriplerMinDelay());
		maxSlider = new CompSlider(settings.getClickTriplerMaxDelay());

		minSlider.setCircle(true);
		maxSlider.setCircle(true);
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {

		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		ColorManager colorManager = instance.getColorManager();
		ColorPalette palette = colorManager.getPalette();
		InternalSettingsMod settings = InternalSettingsMod.getInstance();

		boolean enabled = settings.getClickTriplerSetting().isToggled();
		int minDelay = settings.getClickTriplerMinDelay().getValueInt();
		int maxDelay = settings.getClickTriplerMaxDelay().getValueInt();

		// Keep the helper's state file in step with the panel, including live slider drags.
		ClickTripler.sync(enabled, minDelay, maxDelay);

		float cardX = this.getX();
		float cardWidth = this.getWidth();

		drawCard(nvg, palette, cardX, this.getY(), cardWidth, 41);
		nvg.drawText(TranslateText.CLICK_TRIPLER.getText(), cardX + 8, this.getY() + 9.5F, palette.getFontColor(ColorType.DARK), 12.5F, Fonts.MEDIUM);
		nvg.drawText("Adds a third click when two clicks land close together", cardX + 8, this.getY() + 23.5F, palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

		enabledToggle.setX(cardX + cardWidth - 46);
		enabledToggle.setY(this.getY() + 12.5F);
		enabledToggle.draw(mouseX, mouseY, partialTicks);

		MorphCursor.getInstance().hover(enabledToggle.getX(), enabledToggle.getY(), 34, 16, 7, "Toggle the click tripler");

		drawSliderRow(nvg, palette, mouseX, mouseY, partialTicks,
				TranslateText.TRIPLER_MIN_DELAY.getText(), "Shortest wait before the extra click",
				this.getY() + 51, minSlider, minDelay);

		drawSliderRow(nvg, palette, mouseX, mouseY, partialTicks,
				TranslateText.TRIPLER_MAX_DELAY.getText(), "Longest wait before the extra click",
				this.getY() + 102, maxSlider, maxDelay);

		float statusY = this.getY() + 153;

		drawCard(nvg, palette, cardX, statusY, cardWidth, 41);

		boolean running = ClickTripler.isRunning();
		boolean misconfigured = minDelay > maxDelay;

		String statusText = running ? "Helper detected" : "Helper not running";
		Color statusColor = running ? new Color(96, 200, 124) : new Color(232, 168, 88);

		nvg.drawText(statusText, cardX + 8, statusY + 9.5F, statusColor, 12.5F, Fonts.MEDIUM);

		String detail;

		if (misconfigured) {
			detail = "Minimum is above maximum, the helper clamps it";
		} else if (running) {
			detail = "Changes apply instantly, no restart needed";
		} else {
			detail = "Start it once with sudo, then this toggle controls it";
		}

		nvg.drawText(detail, cardX + 8, statusY + 23.5F, palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);
		nvg.drawText(ClickTripler.getStatePath().toString(), cardX + 8, statusY + 33.5F, palette.getFontColor(ColorType.NORMAL), 6.5F, Fonts.REGULAR);
	}

	private void drawSliderRow(NanoVGManager nvg, ColorPalette palette, int mouseX, int mouseY, float partialTicks,
			String title, String description, float y, CompSlider slider, int value) {

		drawCard(nvg, palette, this.getX(), y, this.getWidth(), 41);

		nvg.drawText(title, this.getX() + 8, y + 9.5F, palette.getFontColor(ColorType.DARK), 12.5F, Fonts.MEDIUM);
		nvg.drawText(description, this.getX() + 8, y + 23.5F, palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

		nvg.drawText(value + " ms", this.getX() + this.getWidth() - 8 - nvg.getTextWidth(value + " ms", 9, Fonts.MEDIUM),
				y + 9.5F, palette.getFontColor(ColorType.NORMAL), 9, Fonts.MEDIUM);

		slider.setX(this.getX() + this.getWidth() - 128);
		slider.setY(y + 26);
		slider.setWidth(112);
		slider.draw(mouseX, mouseY, partialTicks);

		MorphCursor.getInstance().hover(slider.getX() - 4, slider.getY() - 4, (float) slider.getWidth() + 8, 12, 4, title);
	}

	private void drawCard(NanoVGManager nvg, ColorPalette palette, float x, float y, float width, float height) {
		nvg.drawRoundedRect(x, y, width, height, 6, palette.getBackgroundColor(ColorType.DARK));
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int mouseButton) {

		enabledToggle.mouseClicked(mouseX, mouseY, mouseButton);
		minSlider.mouseClicked(mouseX, mouseY, mouseButton);
		maxSlider.mouseClicked(mouseX, mouseY, mouseButton);
	}

	@Override
	public void mouseReleased(int mouseX, int mouseY, int mouseButton) {

		minSlider.mouseReleased(mouseX, mouseY, mouseButton);
		maxSlider.mouseReleased(mouseX, mouseY, mouseButton);
	}
}
