package me.eldodebug.soar.ui;

import java.awt.Color;
import java.util.List;

import org.lwjgl.input.Keyboard;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.color.palette.ColorPalette;
import me.eldodebug.soar.management.color.palette.ColorType;
import me.eldodebug.soar.management.notification.NotificationManager.NotificationRecord;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.management.nanovg.font.LegacyIcon;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.animation.simple.SimpleAnimation;
import me.eldodebug.soar.utils.mouse.MorphCursor;
import me.eldodebug.soar.utils.mouse.MouseUtils;
import me.eldodebug.soar.utils.mouse.Scroll;

/**
 * A history panel for the toasts that otherwise vanish after three seconds. Opened from the
 * bell in the sidebar or with Ctrl+N while the menu is open.
 */
public class NotificationCenter {

	private static final float PANEL_WIDTH = 300.0F;
	private static final float PANEL_HEIGHT = 236.0F;
	private static final float ROW_HEIGHT = 38.0F;
	private static final float ROW_STEP = 41.0F;
	private static final float HEADER = 34.0F;

	private final SimpleAnimation openAnimation = new SimpleAnimation();
	private final Scroll scroll = new Scroll();

	private boolean open;
	private float x, y, width, height;
	private float clearX, clearY, clearWidth, clearHeight;

	public boolean isOpen() {
		return open;
	}

	public void open(float panelX, float panelY, float panelWidth, float panelHeight) {
		layout(panelX, panelY, panelWidth, panelHeight);
		open = true;
		scroll.resetAll();
		Glide.getInstance().getNotificationManager().markAllRead();
	}

	public void close() {
		open = false;
	}

	public boolean keyTyped(char typedChar, int keyCode) {

		if (!open) {
			return false;
		}

		if (keyCode == Keyboard.KEY_ESCAPE || keyCode == Keyboard.KEY_N && isCtrlDown()) {
			close();
			return true;
		}

		return true;
	}

	public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {

		if (!open) {
			return false;
		}

		if (mouseButton == 0 && !MouseUtils.isInside(mouseX, mouseY, x, y, width, height)) {
			close();
			return true;
		}

		if (mouseButton == 0 && MouseUtils.isInside(mouseX, mouseY, clearX, clearY, clearWidth, clearHeight)) {
			Glide.getInstance().getNotificationManager().clearHistory();
			scroll.resetAll();
			return true;
		}

		return true;
	}

	public void draw(int mouseX, int mouseY, float partialTicks, float panelX, float panelY, float panelWidth, float panelHeight) {

		if (!open) {
			openAnimation.setAnimation(0.0F, 18);
			return;
		}

		layout(panelX, panelY, panelWidth, panelHeight);
		openAnimation.setAnimation(1.0F, 18);

		float alpha = openAnimation.getValue();

		if (alpha < 0.02F) {
			return;
		}

		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		ColorPalette palette = instance.getColorManager().getPalette();
		AccentColor accentColor = instance.getColorManager().getCurrentColor();
		List<NotificationRecord> records = instance.getNotificationManager().getHistory();

		nvg.save();

		nvg.setAlpha(alpha);
		nvg.scale(x, y, width, height, 0.97F + (0.03F * alpha));

		nvg.drawRoundedRect(panelX, panelY, panelWidth, panelHeight, 12, new Color(0, 0, 0, 140));

		nvg.drawSoftShadow(x, y, width, height, 10, 150);
		nvg.drawRoundedRect(x, y, width, height, 10, palette.getBackgroundColor(ColorType.NORMAL));
		nvg.drawGradientOutlineRoundedRect(x, y, width, height, 10, 1.0F,
				ColorUtils.applyAlpha(accentColor.getColor1(), 60),
				ColorUtils.applyAlpha(accentColor.getColor2(), 60));

		nvg.drawText("Notifications", x + 12, y + 10, palette.getFontColor(ColorType.DARK), 11, Fonts.SEMIBOLD);
		nvg.drawText(records.isEmpty() ? "Nothing yet" : records.size() + " events", x + 12, y + 22,
				palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

		clearWidth = 44.0F;
		clearHeight = 16.0F;
		clearX = x + width - clearWidth - 10.0F;
		clearY = y + 9.0F;

		boolean clearHovered = MouseUtils.isInside(mouseX, mouseY, clearX, clearY, clearWidth, clearHeight);

		nvg.drawRoundedRect(clearX, clearY, clearWidth, clearHeight, 5,
				clearHovered ? ColorUtils.applyAlpha(accentColor.getColor1(), 70) : palette.getBackgroundColor(ColorType.DARK));
		nvg.drawCenteredText("Clear", clearX + (clearWidth / 2.0F), clearY + 4.0F,
				clearHovered ? Color.WHITE : palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.MEDIUM);
		MorphCursor.getInstance().hover(clearX, clearY, clearWidth, clearHeight, 5);

		nvg.drawRoundedRect(x + 10, y + HEADER - 2, width - 20, 1, 0, palette.getBackgroundColor(ColorType.DARK));

		nvg.save();
		nvg.scissor(x + 8, y + HEADER, width - 16, height - HEADER - 8);

		if (MouseUtils.isInside(mouseX, mouseY, x + 8, y + HEADER, width - 16, height - HEADER - 8)) {
			scroll.onScroll();
			scroll.onAnimation();
		}

		nvg.translate(0, scroll.getValue());

		float contentTop = y + HEADER + 4;

		if (records.isEmpty()) {
			nvg.drawCenteredText("Notifications you receive will appear here", x + (width / 2.0F), contentTop + 60,
					palette.getFontColor(ColorType.NORMAL), 8.5F, Fonts.REGULAR);
		}

		for (int i = 0; i < records.size(); i++) {

			NotificationRecord record = records.get(i);
			float rowY = contentTop + (i * ROW_STEP);

			nvg.drawRoundedRect(x + 10, rowY, width - 20, ROW_HEIGHT, 6, palette.getBackgroundColor(ColorType.DARK));

			nvg.drawCenteredText(record.getType().getIcon(), x + 26, rowY + 12,
					accentColor.getInterpolateColor(), 11, Fonts.LEGACYICON);

			nvg.drawText(nvg.getLimitText(record.getTitle(), 9, Fonts.MEDIUM, width - 78), x + 42, rowY + 7,
					palette.getFontColor(ColorType.DARK), 9, Fonts.MEDIUM);
			nvg.drawText(nvg.getLimitText(record.getMessage(), 7.5F, Fonts.REGULAR, width - 78), x + 42, rowY + 19,
					palette.getFontColor(ColorType.NORMAL), 7.5F, Fonts.REGULAR);

			nvg.drawText(relativeTime(record.getTimestamp()), x + width - 44, rowY + 7,
					palette.getFontColor(ColorType.NORMAL), 7, Fonts.REGULAR);
		}

		scroll.setMaxScroll(Math.max(0.0F, (records.size() * ROW_STEP) - (height - HEADER - 12)));

		nvg.restore();
		nvg.restore();
	}

	private void layout(float panelX, float panelY, float panelWidth, float panelHeight) {

		width = PANEL_WIDTH;
		height = PANEL_HEIGHT;
		x = panelX + ((panelWidth - width) / 2.0F);
		y = panelY + ((panelHeight - height) / 2.0F);
	}

	private boolean isCtrlDown() {
		return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
	}

	private String relativeTime(long timestamp) {

		long seconds = Math.max(0L, (System.currentTimeMillis() - timestamp) / 1000L);

		if (seconds < 60) {
			return seconds + "s";
		}

		if (seconds < 3600) {
			return (seconds / 60) + "m";
		}

		return (seconds / 3600) + "h";
	}
}
