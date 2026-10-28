package me.eldodebug.soar.management.mods.impl;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.event.EventTarget;
import me.eldodebug.soar.management.event.impl.EventClickMouse;
import me.eldodebug.soar.management.event.impl.EventPlayerHeadRotation;
import me.eldodebug.soar.management.event.impl.EventRender2D;
import me.eldodebug.soar.management.event.impl.EventScrollMouse;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.misc.Favourites;
import me.eldodebug.soar.management.misc.Recents;
import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.ModCategory;
import me.eldodebug.soar.management.mods.ModManager;
import me.eldodebug.soar.management.mods.settings.impl.KeybindSetting;
import me.eldodebug.soar.management.mods.settings.impl.NumberSetting;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.management.nanovg.font.LegacyIcon;
import me.eldodebug.soar.management.notification.NotificationType;
import me.eldodebug.soar.management.profile.Profile;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.animation.spring.Spring;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.item.ItemStack;

/**
 * Hold a key to open a liquid quick wheel around the crosshair. Direction comes from the mouse
 * while the wheel is open and the camera is frozen, so a player can toggle a module, load a
 * profile or pick a hotbar slot without looking away or opening the full menu.
 */
public class RadialMenuMod extends Mod {

	private static final int PAGE_MODULES = 0;
	private static final int PAGE_PROFILES = 1;
	private static final int PAGE_HOTBAR = 2;
	private static final int MAX_ITEMS = 9;

	private static final float CURSOR_DEADZONE = 16.0F;
	private static final float BAND = 25.0F;

	private final KeybindSetting keybindSetting = new KeybindSetting(TranslateText.KEYBIND, this, Keyboard.KEY_R);
	private final NumberSetting radiusSetting = new NumberSetting(TranslateText.RADIAL_RADIUS, this, 78, 74, 110, true);

	private final ArrayList<RadialItem> items = new ArrayList<RadialItem>();
	private final Spring openSpring = new Spring(0.0D, 26.0D, 1.0D);
	private final Spring[] emphasis = new Spring[MAX_ITEMS];
	private final Spring blobX = new Spring(0.0D, 28.0D, 0.9D);
	private final Spring blobY = new Spring(0.0D, 28.0D, 0.9D);

	private boolean active;
	private int page = PAGE_MODULES;
	private int selection = -1;

	private long lastFrameNanos;

	private long lastPageSwitch;
	private int lastWheel;
	private boolean leftHeld, rightHeld;
	private boolean cursorReleased;

	public RadialMenuMod() {
		super(TranslateText.RADIAL_MENU, TranslateText.RADIAL_MENU_DESCRIPTION, ModCategory.PLAYER);

		for(int i = 0; i < MAX_ITEMS; i++) {
			emphasis[i] = new Spring(0.0D, 30.0D, 0.8D);
		}
	}

	@Override
	public void onDisable() {
		super.onDisable();
		close();
		openSpring.setValue(0.0D);
	}

	@EventTarget
	public void onRender2D(EventRender2D event) {

		if(mc.currentScreen != null || mc.thePlayer == null || mc.gameSettings.hideGUI) {

			if(active) {
				close();
			}

			return;
		}

		boolean down = Keyboard.isKeyDown(keybindSetting.getKeyCode());

		if(down && !active) {
			open();
		} else if(!down && active) {
			activateSelection();
			close();
		}

		updateSelection();
		updatePageKeys();
		updateScrollPage();

		long now = System.nanoTime();
		float delta = lastFrameNanos == 0L ? 0.0F : (now - lastFrameNanos) / 1_000_000_000F;
		lastFrameNanos = now;
		delta = Math.max(0.0F, Math.min(0.1F, delta));

		openSpring.update(active ? 1.0D : 0.0D, delta);

		if(openSpring.get() <= 0.005F && !active) {
			return;
		}

		for(int i = 0; i < emphasis.length; i++) {
			boolean highlighted = active && selection == i && i < items.size();
			emphasis[i].update(highlighted ? 1.0D : 0.0D, delta);
		}

		updateBlob(delta);

		NanoVGManager nvg = Glide.getInstance().getNanoVGManager();
		nvg.setupAndDraw(() -> draw(nvg));
	}

	@EventTarget
	public void onPlayerHeadRotation(EventPlayerHeadRotation event) {

		// The cursor is released while the wheel is open, so the renderer can still see mouse
		// deltas. Cancel them so the camera stays still while the player points at a sector.
		if(active) {
			event.setCancelled(true);
		}
	}

	@EventTarget
	public void onScroll(EventScrollMouse event) {

		if(!active) {
			return;
		}

		event.setCancelled(true);

		if(event.getAmount() != 0) {
			requestPageSwitch(event.getAmount() > 0 ? -1 : 1);
		}
	}

	@EventTarget
	public void onClickMouse(EventClickMouse event) {

		if(active) {
			event.setCancelled(true);
		}
	}

	private void open() {

		int first = firstEnabledPage();

		if(first < 0) {
			return;
		}

		page = first;
		active = true;
		selection = -1;
		lastFrameNanos = 0L;
		lastPageSwitch = 0L;
		lastWheel = 0;
		leftHeld = false;
		rightHeld = false;

		if(Mouse.isCreated() && mc.mouseHelper != null) {
			mc.mouseHelper.ungrabMouseCursor();
			Mouse.setCursorPosition(mc.displayWidth / 2, mc.displayHeight / 2);
			cursorReleased = true;
		}

		rebuildItems();
	}

	private void close() {

		active = false;
		selection = -1;
		leftHeld = false;
		rightHeld = false;

		if(cursorReleased && Mouse.isCreated() && mc.mouseHelper != null && mc.currentScreen == null) {
			mc.mouseHelper.grabMouseCursor();
			mc.mouseHelper.deltaX = 0;
			mc.mouseHelper.deltaY = 0;
		}

		cursorReleased = false;
	}

	private void updatePageKeys() {

		if(!active) {
			leftHeld = false;
			rightHeld = false;
			return;
		}

		boolean left = Keyboard.isKeyDown(Keyboard.KEY_LEFT);
		boolean right = Keyboard.isKeyDown(Keyboard.KEY_RIGHT);

		if(left && !leftHeld) {
			switchPage(-1);
		}

		if(right && !rightHeld) {
			switchPage(1);
		}

		leftHeld = left;
		rightHeld = right;
	}

	private void updateScrollPage() {

		if(!active) {
			return;
		}

		int wheel = Mouse.getDWheel();

		if(wheel != 0 && wheel != lastWheel) {
			requestPageSwitch(wheel > 0 ? -1 : 1);
		}

		lastWheel = wheel;
	}

	private void requestPageSwitch(int direction) {

		long now = System.currentTimeMillis();

		if(now - lastPageSwitch < 45L) {
			return;
		}

		lastPageSwitch = now;
		switchPage(direction);
	}

	private void switchPage(int direction) {

		page = (page + direction + 3) % 3;

		selection = -1;

		for(Spring spring : emphasis) {
			spring.setValue(0.0D);
		}

		rebuildItems();
	}

	private int firstEnabledPage() {
		return PAGE_MODULES;
	}

	private int enabledPageCount() {
		return 3;
	}

	private boolean isPageEnabled(int page) {
		return page >= PAGE_MODULES && page <= PAGE_HOTBAR;
	}

	private void rebuildItems() {

		items.clear();

		switch(page) {
			case PAGE_MODULES:
				rebuildModules();
				break;
			case PAGE_PROFILES:
				rebuildProfiles();
				break;
			case PAGE_HOTBAR:
				rebuildHotbar();
				break;
		}

		for(Spring spring : emphasis) {
			spring.setValue(0.0D);
		}
	}

	private void rebuildModules() {

		ModManager modManager = Glide.getInstance().getModManager();
		LinkedHashSet<Mod> picked = new LinkedHashSet<Mod>();

		for(Mod mod : modManager.getMods()) {
			if(mod != this && Favourites.isPinned(mod) && isUsable(mod)) {
				picked.add(mod);
			}
		}

		if(picked.size() < MAX_ITEMS) {
			for(Mod mod : Recents.resolve(modManager)) {
				if(mod != this && isUsable(mod)) {
					picked.add(mod);
				}
			}
		}

		if(picked.size() < MAX_ITEMS) {
			for(Mod mod : modManager.getMods()) {
				if(mod != this && mod.isToggled() && isUsable(mod)) {
					picked.add(mod);
				}
			}
		}

		for(Mod mod : picked) {

			if(items.size() >= MAX_ITEMS) {
				break;
			}

			items.add(new RadialItem(mod, mod.getName(), mod.isToggled() ? "On" : "Off",
					categoryIcon(mod.getCategory()), true));
		}
	}

	private boolean isUsable(Mod mod) {
		return !mod.isHide() && mod.getAllowed();
	}

	private void rebuildProfiles() {

		for(Profile profile : Glide.getInstance().getProfileManager().getProfiles()) {

			if(items.size() >= MAX_ITEMS) {
				break;
			}

			if(profile.getJsonFile() == null || !profile.getJsonFile().isFile()) {
				continue;
			}

			items.add(new RadialItem(profile, profile.getName(), "Profile", LegacyIcon.EDIT, true));
		}
	}

	private void rebuildHotbar() {

		if(mc.thePlayer == null) {
			return;
		}

		for(int i = 0; i < 9; i++) {
			ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
			String name = stack == null ? "Empty" : stack.getDisplayName();

			items.add(new RadialItem(Integer.valueOf(i), name, String.valueOf(i + 1), null, false));
		}
	}

	private void updateSelection() {

		if(!active) {
			return;
		}

		int count = items.size();

		if(count == 0) {
			selection = -1;
			return;
		}

		if(mc.displayWidth <= 0 || mc.displayHeight <= 0 || !Mouse.isCreated()) {
			return;
		}

		ScaledResolution sr = new ScaledResolution(mc);

		float mouseX = Mouse.getX() * (float) sr.getScaledWidth() / mc.displayWidth;
		float mouseY = sr.getScaledHeight() - (Mouse.getY() * (float) sr.getScaledHeight() / mc.displayHeight);

		float dx = mouseX - (sr.getScaledWidth() / 2.0F);
		float dy = mouseY - (sr.getScaledHeight() / 2.0F);

		if((dx * dx) + (dy * dy) < (CURSOR_DEADZONE * CURSOR_DEADZONE)) {
			// Keep the last selection while the cursor is near the hub.
			return;
		}

		double angle = Math.toDegrees(Math.atan2(dx, -dy));

		if(angle < 0.0D) {
			angle += 360.0D;
		}

		double sector = 360.0D / count;
		int index = (int) Math.round(angle / sector);

		selection = ((index % count) + count) % count;
	}

	private void updateBlob(float delta) {

		if(!active || selection < 0 || selection >= items.size()) {
			blobX.update(0.0D, delta);
			blobY.update(0.0D, delta);
			return;
		}

		float[] point = itemPoint(selection, items.size());
		blobX.update(point[0], delta);
		blobY.update(point[1], delta);
	}

	private void activateSelection() {

		if(selection < 0 || selection >= items.size()) {
			return;
		}

		RadialItem item = items.get(selection);

		// Actions are deferred to the next scheduled task: toggling a module or loading a
		// profile mutates the event registry, and doing that while EventRender2D is iterating
		// its handlers can skip another module's frame.
		if(page == PAGE_MODULES && item.payload instanceof Mod) {

			Mod mod = (Mod) item.payload;
			mc.addScheduledTask(() -> {
				mod.toggle();
				Recents.push(mod);
			});

		} else if(page == PAGE_PROFILES && item.payload instanceof Profile) {

			Profile profile = (Profile) item.payload;
			mc.addScheduledTask(() -> {
				Glide.getInstance().getProfileManager().load(profile.getJsonFile());
				Glide.getInstance().getNotificationManager().post("Profile", profile.getName(), NotificationType.SUCCESS);
			});

		} else if(page == PAGE_HOTBAR && item.payload instanceof Integer) {

			int slot = (Integer) item.payload;
			mc.addScheduledTask(() -> mc.thePlayer.inventory.currentItem = slot);
		}
	}

	private void draw(NanoVGManager nvg) {

		float alpha = Math.max(0.0F, Math.min(1.0F, openSpring.get()));

		if(alpha <= 0.01F) {
			return;
		}

		ScaledResolution sr = new ScaledResolution(mc);

		float centerX = sr.getScaledWidth() / 2.0F;
		float centerY = sr.getScaledHeight() / 2.0F;
		float radius = currentRadius() * (0.72F + (0.28F * alpha));

		AccentColor accent = Glide.getInstance().getColorManager().getCurrentColor();
		Color accentColor = accent.getInterpolateColor();

		nvg.save();
		nvg.setAlpha(alpha);

		// Soft glassy body. The wheel sits over the game, so a dark translucent disc keeps the
		// labels readable without hiding what is happening around the player.
		nvg.drawRadialGlow(centerX, centerY, radius + 20.0F, ColorUtils.applyAlpha(accentColor, a(26 * alpha)));
		nvg.drawCircle(centerX, centerY, radius + 10.0F, new Color(10, 12, 18, a(150 * alpha)));
		nvg.drawArc(centerX, centerY, radius + 11.0F, 0.0F, 360.0F, 1.0F, new Color(255, 255, 255, a(38 * alpha)));
		nvg.drawCircle(centerX, centerY, radius - (BAND / 2.0F) - 8.0F, new Color(16, 18, 25, a(185 * alpha)));

		int count = items.size();

		if(count == 0) {
			nvg.drawCenteredText("No items on this page", centerX, centerY - 4,
					new Color(235, 235, 240, a(235 * alpha)), 10, Fonts.MEDIUM);
			nvg.restore();
			return;
		}

		// The selected sector pulls a liquid blob toward itself; with no selection it rests in
		// the middle so the wheel always feels anchored.
		float blobDrawX = blobX.get() + centerX;
		float blobDrawY = blobY.get() + centerY;

		nvg.drawRadialGlow(blobDrawX, blobDrawY, BAND + 10.0F,
				ColorUtils.applyAlpha(accentColor, a((selection >= 0 ? 52 : 16) * alpha)));

		double sector = 360.0D / count;

		for(int i = 0; i < count; i++) {

			RadialItem item = items.get(i);

			float emphasisValue = emphasis[Math.min(i, emphasis.length - 1)].get();
			double midAngle = Math.toRadians(-90.0D + (i * sector));
			double start = -90.0D + ((i - 0.5D) * sector) + 2.4D;
			double end = -90.0D + ((i + 0.5D) * sector) - 2.4D;

			float arcRadius = radius + (emphasisValue * 3.0F);
			float stroke = BAND - (emphasisValue * 2.0F);
			int arcAlpha = a((42 + (emphasisValue * 98)) * alpha);

			nvg.drawArc(centerX, centerY, arcRadius, (float) start, (float) end, stroke,
					ColorUtils.applyAlpha(accentColor, arcAlpha));

			float iconX = centerX + (float) Math.cos(midAngle) * radius;
			float iconY = centerY + (float) Math.sin(midAngle) * radius;
			float iconSize = 11.0F + (emphasisValue * 2.0F);

			int textAlpha = a((160 + (emphasisValue * 95)) * alpha);
			Color iconColor = ColorUtils.applyAlpha(emphasisValue > 0.2F ? Color.WHITE : accentColor, textAlpha);

			if(emphasisValue > 0.01F) {
				nvg.drawCircle(iconX, iconY - 3.0F, 12.0F + (emphasisValue * 1.5F),
						ColorUtils.applyAlpha(accentColor, a(38 * emphasisValue * alpha)));
			}

			if(item.legacyIcon) {
				nvg.drawCenteredText(item.icon, iconX, iconY - 10.0F, iconColor, iconSize, Fonts.LEGACYICON);
			} else {
				nvg.drawCenteredText(item.icon, iconX, iconY - 10.0F, iconColor, iconSize + 1.0F, Fonts.SEMIBOLD);
			}

			String label = nvg.getLimitText(item.label, 7.2F, Fonts.MEDIUM, 66.0F);
			nvg.drawCenteredText(label, iconX, iconY + 2.0F,
					new Color(220, 225, 235, a((135 + (emphasisValue * 120)) * alpha)), 7.2F, Fonts.MEDIUM);
		}

		drawCenterCard(nvg, centerX, centerY, alpha, accentColor);
		drawPageDots(nvg, centerX, centerY + radius + 22.0F, alpha, accentColor);

		nvg.drawCenteredText("Release to use  ·  Scroll or ← → for page", centerX, centerY + radius + 34.0F,
				new Color(225, 228, 236, a(150 * alpha)), 7.0F, Fonts.REGULAR);

		nvg.restore();
	}

	private void drawCenterCard(NanoVGManager nvg, float centerX, float centerY, float alpha, Color accentColor) {

		float width = 104.0F;
		float height = 34.0F;

		nvg.drawRoundedRect(centerX - (width / 2.0F), centerY - (height / 2.0F), width, height, 9.0F,
				new Color(10, 12, 18, a(195 * alpha)));

		String pageName = pageName();
		String title;
		String subTitle;

		if(selection >= 0 && selection < items.size()) {
			RadialItem item = items.get(selection);
			title = item.label;
			subTitle = item.subLabel;
		} else {
			title = "Move to choose";
			subTitle = "";
		}

		nvg.drawCenteredText(pageName, centerX, centerY - 9.0F,
				new Color(185, 192, 206, a(210 * alpha)), 6.8F, Fonts.REGULAR);
		nvg.drawCenteredText(nvg.getLimitText(title, 10.0F, Fonts.SEMIBOLD, width - 12.0F), centerX, centerY - 2.0F,
				selection >= 0 ? accentColor : new Color(235, 235, 240, a(235 * alpha)), 10.0F, Fonts.SEMIBOLD);

		if(!subTitle.isEmpty()) {
			nvg.drawCenteredText(nvg.getLimitText(subTitle, 6.5F, Fonts.REGULAR, width - 12.0F), centerX, centerY + 9.0F,
					new Color(190, 196, 210, a(190 * alpha)), 6.5F, Fonts.REGULAR);
		}
	}

	private void drawPageDots(NanoVGManager nvg, float centerX, float y, float alpha, Color accentColor) {

		int enabled = enabledPageCount();
		int offset = 0;
		int[] pageOrder = new int[] { PAGE_MODULES, PAGE_PROFILES, PAGE_HOTBAR };

		for(int i = 0; i < pageOrder.length; i++) {

			if(!isPageEnabled(pageOrder[i])) {
				continue;
			}

			float dotX = centerX + ((offset - ((enabled - 1) / 2.0F)) * 12.0F);
			boolean current = pageOrder[i] == page;

			nvg.drawCircle(dotX, y, current ? 3.0F : 1.9F,
					current ? ColorUtils.applyAlpha(accentColor, a(230 * alpha))
							: new Color(255, 255, 255, a(70 * alpha)));

			offset++;
		}
	}

	private String pageName() {

		switch(page) {
			case PAGE_MODULES:
				return "MODULES";
			case PAGE_PROFILES:
				return "PROFILES";
			case PAGE_HOTBAR:
				return "HOTBAR";
			default:
				return "";
		}
	}

	private float[] itemPoint(int index, int count) {

		double sector = 360.0D / count;
		double midAngle = Math.toRadians(-90.0D + (index * sector));

		float radius = currentRadius();

		return new float[] {
				(float) Math.cos(midAngle) * radius,
				(float) Math.sin(midAngle) * radius
		};
	}

	private int a(float value) {
		return Math.max(0, Math.min(255, (int) value));
	}

	private float currentRadius() {

		ScaledResolution sr = new ScaledResolution(mc);

		return Math.min(radiusSetting.getValueFloat(),
				Math.min(sr.getScaledWidth(), sr.getScaledHeight()) * 0.36F);
	}

	private String categoryIcon(ModCategory category) {

		switch(category) {
			case PLAYER:
				return LegacyIcon.USER;
			case RENDER:
				return LegacyIcon.IMAGE;
			case HUD:
				return LegacyIcon.LAYOUT;
			case WORLD:
				return LegacyIcon.GLOBE;
			case OTHER:
				return LegacyIcon.MORE_HORIZONTAL;
			default:
				return LegacyIcon.GRID;
		}
	}

	private static class RadialItem {

		private final Object payload;
		private final String label;
		private final String subLabel;
		private final String icon;
		private final boolean legacyIcon;

		private RadialItem(Object payload, String label, String subLabel, String icon, boolean legacyIcon) {
			this.payload = payload;
			this.label = label;
			this.subLabel = subLabel;
			this.icon = icon;
			this.legacyIcon = legacyIcon;
		}
	}
}
