package me.eldodebug.soar.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.lwjgl.input.Keyboard;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.color.palette.ColorPalette;
import me.eldodebug.soar.management.color.palette.ColorType;
import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.ModManager;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.ui.comp.impl.field.CompSearchBox;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.SearchUtils;
import me.eldodebug.soar.utils.animation.simple.SimpleAnimation;
import me.eldodebug.soar.utils.mouse.MorphCursor;
import me.eldodebug.soar.utils.mouse.MouseUtils;

/**
 * A launcher for the client, opened with Ctrl+K inside the mod menu.
 *
 * Modules are matched by name, description, alias and by the names of their settings, so
 * typing a setting name ("reach") surfaces the module that owns it. Only the modules that
 * are currently passed through the same visibility rules as the module list are offered.
 */
public class CommandPalette {

	/**
	 * A palette entry that is not a module, such as "Edit HUD".
	 */
	public static class Action {

		private final String name;
		private final String hint;
		private final Runnable runnable;

		public Action(String name, String hint, Runnable runnable) {
			this.name = name;
			this.hint = hint;
			this.runnable = runnable;
		}

		public String getName() {
			return name;
		}

		public String getHint() {
			return hint;
		}

		public void run() {
			runnable.run();
		}
	}

	private static final int MAX_RESULTS = 6;
	private static final float ROW_HEIGHT = 26.0F;
	private static final float ROW_STEP = 28.0F;
	private static final float CONTENT_TOP = 42.0F;

	private final CompSearchBox searchBox = new CompSearchBox();
	private final ArrayList<Mod> results = new ArrayList<Mod>();
	private final ArrayList<Action> actions = new ArrayList<Action>();

	private Consumer<Mod> settingsListener;
	private final SimpleAnimation openAnimation = new SimpleAnimation();

	private boolean open;
	private int selectedIndex;
	private String lastQuery = "";

	private float x, y, width, height;

	private int lastMouseX = Integer.MIN_VALUE;
	private int lastMouseY = Integer.MIN_VALUE;

	public boolean isOpen() {
		return open;
	}

	public void setActions(List<Action> paletteActions) {
		actions.clear();
		actions.addAll(paletteActions);
	}

	public void setSettingsListener(Consumer<Mod> listener) {
		this.settingsListener = listener;
	}

	/**
	 * With no query the palette offers actions; typing switches it to module search.
	 */
	private boolean isActionMode() {
		return searchBox.getText().trim().isEmpty() && !actions.isEmpty();
	}

	private int itemCount() {
		return isActionMode() ? actions.size() : results.size();
	}

	public void open(float panelX, float panelY, float panelWidth, float panelHeight) {

		layout(panelX, panelY, panelWidth, panelHeight);

		this.open = true;
		this.selectedIndex = 0;
		this.lastQuery = "";
		this.results.clear();

		searchBox.setText("");
		searchBox.setFocused(true);
		searchBox.setPosition(x + 10.0F, y + 10.0F, width - 20.0F, 22.0F);

		refresh();
	}

	public void close() {
		this.open = false;
		this.results.clear();
		this.lastQuery = "";
		searchBox.setFocused(false);
	}

	public boolean keyTyped(char typedChar, int keyCode) {

		if (!open) {
			return false;
		}

		switch (keyCode) {
		case Keyboard.KEY_ESCAPE:
			close();
			return true;
		case Keyboard.KEY_DOWN:
			moveSelection(1);
			return true;
		case Keyboard.KEY_UP:
			moveSelection(-1);
			return true;
		case Keyboard.KEY_RETURN:
			activateSelection();
			return true;
		default:
			break;
		}

		searchBox.keyTyped(typedChar, keyCode);

		if (!searchBox.getText().equals(lastQuery)) {
			lastQuery = searchBox.getText();
			refresh();
			selectedIndex = 0;
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

		if (mouseButton != 0) {
			return true;
		}

		searchBox.mouseClicked(mouseX, mouseY, mouseButton);

		boolean actionMode = isActionMode();
		int rowCount = actionMode ? Math.min(actions.size(), MAX_RESULTS) : Math.min(results.size(), MAX_RESULTS);

		for (int i = 0; i < rowCount; i++) {

			float rowY = getRowY(i);

			if (MouseUtils.isInside(mouseX, mouseY, x + 8.0F, rowY, width - 16.0F, ROW_HEIGHT)) {
				selectedIndex = i;
				activateSelection();
				return true;
			}
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

		boolean mouseMoved = mouseX != lastMouseX || mouseY != lastMouseY;
		boolean actionMode = isActionMode();

		nvg.save();

		nvg.setAlpha(alpha);
		nvg.scale(x, y, width, height, 0.97F + (0.03F * alpha));

		// Dim the panel so the palette reads as a modal layer.
		nvg.drawRoundedRect(panelX, panelY, panelWidth, panelHeight, 12, new Color(0, 0, 0, 140));

		nvg.drawRoundedRect(x, y, width, height, 10, palette.getBackgroundColor(ColorType.NORMAL));
		nvg.drawGradientOutlineRoundedRect(x, y, width, height, 10, 1.0F,
				ColorUtils.applyAlpha(accentColor.getColor1(), 60),
				ColorUtils.applyAlpha(accentColor.getColor2(), 60));

		searchBox.setPosition(x + 10.0F, y + 10.0F, width - 20.0F, 22.0F);
		searchBox.draw(mouseX, mouseY, partialTicks);

		MorphCursor.getInstance().hover(searchBox.getX(), searchBox.getY(), searchBox.getWidth(), searchBox.getHeight(), 6);

		if (actionMode) {

			int actionCount = Math.min(actions.size(), MAX_RESULTS);

			for (int i = 0; i < actionCount; i++) {

				Action action = actions.get(i);
				float rowY = getRowY(i);

				boolean hovered = MouseUtils.isInside(mouseX, mouseY, x + 8.0F, rowY, width - 16.0F, ROW_HEIGHT);

				if (hovered) {

					MorphCursor.getInstance().hover(x + 8.0F, rowY, width - 16.0F, ROW_HEIGHT, 6);

					if (mouseMoved) {
						selectedIndex = i;
					}
				}

				if (i == selectedIndex) {
					nvg.drawGradientRoundedRect(x + 8.0F, rowY, width - 16.0F, ROW_HEIGHT, 6,
							ColorUtils.applyAlpha(accentColor.getColor1(), 55),
							ColorUtils.applyAlpha(accentColor.getColor2(), 55));
				}

				nvg.drawText(action.getName(), x + 16.0F, rowY + 5.0F, palette.getFontColor(ColorType.DARK), 10, Fonts.MEDIUM);

				float hintX = x + 16.0F + nvg.getTextWidth(action.getName(), 10, Fonts.MEDIUM) + 8.0F;

				if (hintX < x + width - 20.0F) {
					nvg.drawText(nvg.getLimitText(action.getHint(), 8, Fonts.REGULAR, (x + width - 20.0F) - hintX),
							hintX, rowY + 7.0F, palette.getFontColor(ColorType.NORMAL), 8, Fonts.REGULAR);
				}
			}
		} else if (results.isEmpty()) {

			String hint = searchBox.getText().trim().isEmpty()
					? "Type to search modules and settings"
					: "No matching module";

			nvg.drawCenteredText(hint, x + (width / 2.0F), y + CONTENT_TOP + 30.0F,
					palette.getFontColor(ColorType.NORMAL), 9, Fonts.REGULAR);
		}

		for (int i = 0; !actionMode && i < results.size() && i < MAX_RESULTS; i++) {

			Mod mod = results.get(i);
			float rowY = getRowY(i);

			boolean hovered = MouseUtils.isInside(mouseX, mouseY, x + 8.0F, rowY, width - 16.0F, ROW_HEIGHT);

			if (hovered) {

				MorphCursor.getInstance().hover(x + 8.0F, rowY, width - 16.0F, ROW_HEIGHT, 6);

				if (mouseMoved) {
					selectedIndex = i;
				}
			}

			if (i == selectedIndex) {
				nvg.drawGradientRoundedRect(x + 8.0F, rowY, width - 16.0F, ROW_HEIGHT, 6,
						ColorUtils.applyAlpha(accentColor.getColor1(), 55),
						ColorUtils.applyAlpha(accentColor.getColor2(), 55));
			}

			float textY = rowY + 5.0F;

			nvg.drawText(mod.getName(), x + 16.0F, textY, palette.getFontColor(ColorType.DARK), 10, Fonts.MEDIUM);

			float descriptionX = x + 16.0F + nvg.getTextWidth(mod.getName(), 10, Fonts.MEDIUM) + 8.0F;

			if (descriptionX < x + width - 60.0F) {
				nvg.drawText(nvg.getLimitText(mod.getDescription(), 8, Fonts.REGULAR, (x + width - 60.0F) - descriptionX),
						descriptionX, textY + 2.0F, palette.getFontColor(ColorType.NORMAL), 8, Fonts.REGULAR);
			}

			drawStatePill(nvg, palette, accentColor, mod, rowY);
		}

		nvg.drawText(actionMode ? "Enter to run" : "Enter toggles / Shift+Enter for settings", x + 10.0F, y + height - 16.0F, palette.getFontColor(ColorType.NORMAL), 8, Fonts.REGULAR);
		nvg.drawCenteredText("Esc to close", x + width - 40.0F, y + height - 16.0F, palette.getFontColor(ColorType.NORMAL), 8, Fonts.REGULAR);

		nvg.restore();

		lastMouseX = mouseX;
		lastMouseY = mouseY;
	}

	private void drawStatePill(NanoVGManager nvg, ColorPalette palette, AccentColor accentColor, Mod mod, float rowY) {

		float pillWidth = 26.0F;
		float pillHeight = 13.0F;
		float pillX = x + width - 10.0F - pillWidth;
		float pillY = rowY + ((ROW_HEIGHT - pillHeight) / 2.0F);

		nvg.drawRoundedRect(pillX, pillY, pillWidth, pillHeight, pillHeight / 2.0F, palette.getBackgroundColor(ColorType.DARK));

		if (mod.isToggled()) {
			nvg.drawGradientRoundedRect(pillX, pillY, pillWidth, pillHeight, pillHeight / 2.0F,
					accentColor.getColor1(), accentColor.getColor2());
		}

		float knob = pillHeight - 4.0F;
		float knobX = pillX + 2.0F + (knob / 2.0F) + (mod.isToggled() ? (pillWidth - knob - 4.0F) : 0.0F);

		nvg.drawCircle(knobX, pillY + (pillHeight / 2.0F), knob / 2.0F,
				mod.isToggled() ? Color.WHITE : palette.getFontColor(ColorType.NORMAL));
	}

	private void moveSelection(int delta) {

		int count = itemCount();

		if (count == 0) {
			return;
		}

		selectedIndex = (selectedIndex + delta + count) % count;
	}

	private void activateSelection() {

		if (isActionMode()) {

			if (selectedIndex >= 0 && selectedIndex < actions.size()) {

				Action action = actions.get(selectedIndex);
				close();
				action.run();
			}

			return;
		}

		if (selectedIndex < 0 || selectedIndex >= results.size()) {
			return;
		}

		Mod mod = results.get(selectedIndex);

		boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

		if (shift && settingsListener != null) {
			close();
			settingsListener.accept(mod);
		} else {
			mod.toggle();
		}
	}

	private void refresh() {

		results.clear();

		String query = searchBox.getText().trim();

		if (query.isEmpty()) {
			return;
		}

		ModManager modManager = Glide.getInstance().getModManager();

		for (Mod mod : modManager.getMods()) {

			if (mod.isHide() || !mod.getAllowed()) {
				continue;
			}

			String words = mod.getName() + " " + mod.getDescription() + " " + mod.getAlias() + " " + modManager.getWords(mod);

			if (SearchUtils.isSimillar(words, query)) {
				results.add(mod);
			}
		}

		final String lowerQuery = query.toLowerCase(Locale.ENGLISH);

		results.sort((a, b) -> {

			boolean aStarts = a.getName().toLowerCase(Locale.ENGLISH).startsWith(lowerQuery);
			boolean bStarts = b.getName().toLowerCase(Locale.ENGLISH).startsWith(lowerQuery);

			if (aStarts != bStarts) {
				return aStarts ? -1 : 1;
			}

			return a.getName().compareToIgnoreCase(b.getName());
		});

		while (results.size() > MAX_RESULTS) {
			results.remove(results.size() - 1);
		}
	}

	private float getRowY(int index) {
		return y + CONTENT_TOP + (index * ROW_STEP);
	}

	private void layout(float panelX, float panelY, float panelWidth, float panelHeight) {
		this.x = panelX + 25.0F;
		this.y = panelY + 18.0F;
		this.width = panelWidth - 50.0F;
		this.height = panelHeight - 36.0F;
	}
}
