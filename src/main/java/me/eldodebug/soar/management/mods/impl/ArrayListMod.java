package me.eldodebug.soar.management.mods.impl;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.event.EventTarget;
import me.eldodebug.soar.management.event.impl.EventRender2D;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.mods.HUDMod;
import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.ModCategory;
import me.eldodebug.soar.management.mods.settings.impl.BooleanSetting;
import me.eldodebug.soar.management.mods.settings.impl.ComboSetting;
import me.eldodebug.soar.management.mods.settings.impl.combo.Option;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.utils.ColorUtils;

public class ArrayListMod extends HUDMod {

	private static final float ROW_HEIGHT = 12.0F;
	private static final float ENTER_TIME = 0.16F;
	private static final float EXIT_TIME = 0.13F;
	private static final float STAGGER_STEP = 0.03F;
	private static final float SLIDE_DISTANCE = 20.0F;
	private static final float BLUR_RADIUS = 2.5F;

	private BooleanSetting backgroundSetting = new BooleanSetting(TranslateText.BACKGROUND, this, true);

	private BooleanSetting hudSetting = new BooleanSetting(TranslateText.HUD, this, false);
	private BooleanSetting renderSetting = new BooleanSetting(TranslateText.RENDER, this, false);
	private BooleanSetting playerSetting = new BooleanSetting(TranslateText.PLAYER, this, false);
	private BooleanSetting otherSetting = new BooleanSetting(TranslateText.OTHER, this, false);

	private ComboSetting modeSetting = new ComboSetting(TranslateText.MODE, this, TranslateText.RIGHT, new ArrayList<Option>(Arrays.asList(
			new Option(TranslateText.RIGHT), new Option(TranslateText.LEFT))));

	/**
	 * Per-row animation state. Entries survive while a mod is being animated out, so the
	 * row can finish its exit even though it is no longer part of the enabled list.
	 */
	private static class EntryAnim {
		float progress;
		float delay = STAGGER_STEP;
		float y;
		float lastSlotY;
		int colorIndex;
	}

	private final LinkedHashMap<Mod, EntryAnim> entries = new LinkedHashMap<Mod, EntryAnim>();
	private long lastFrameNanos;

	public ArrayListMod() {
		super(TranslateText.ARRAY_LIST, TranslateText.ARRAY_LIST_DESCRIPTION);
	}

	@EventTarget
	public void onRender2D(EventRender2D event) {

		NanoVGManager nvg = Glide.getInstance().getNanoVGManager();

		nvg.setupAndDraw(() -> drawNanoVG());
	}

	private void drawNanoVG() {

		Glide instance = Glide.getInstance();
		AccentColor currentColor = instance.getColorManager().getCurrentColor();
		NanoVGManager nvg = instance.getNanoVGManager();

		long now = System.nanoTime();
		float delta = lastFrameNanos == 0L ? 0.0F : (now - lastFrameNanos) / 1_000_000_000F;
		lastFrameNanos = now;

		if (delta > 0.1F) {
			delta = 0.1F;
		}

		ArrayList<Mod> enabledMods = new ArrayList<Mod>();
		int maxWidth = 0;

		for(Mod m : instance.getModManager().getMods()) {

			if(!hudSetting.isToggled() && m.getCategory().equals(ModCategory.HUD)) {
				continue;
			}

			if(!renderSetting.isToggled() && m.getCategory().equals(ModCategory.RENDER)) {
				continue;
			}

			if(!playerSetting.isToggled() && m.getCategory().equals(ModCategory.PLAYER)) {
				continue;
			}

			if(!otherSetting.isToggled() && m.getCategory().equals(ModCategory.OTHER)) {
				continue;
			}

			if(m.isToggled() && !m.isHide()) {

				float nameWidth = this.getTextWidth(m.getName(), 8.5F, getHudFont(1));

				enabledMods.add(m);

				if(maxWidth < nameWidth) {
					maxWidth = (int) nameWidth;
				}
			}
		}

		enabledMods.sort((m1, m2) -> (int) this.getTextWidth(m2.getName(), 8.5F, getHudFont(1)) - (int) this.getTextWidth(m1.getName(), 8.5F, getHudFont(1)));

		boolean isRight = modeSetting.getOption().getTranslate().equals(TranslateText.RIGHT);
		float slideDirection = isRight ? 1.0F : -1.0F;

		Set<Mod> active = new HashSet<Mod>(enabledMods);

		// Rows still animating out draw first, so the reflowed list sits on top of them.
		if(!entries.isEmpty()) {

			Iterator<Map.Entry<Mod, EntryAnim>> iterator = entries.entrySet().iterator();

			while(iterator.hasNext()) {

				Map.Entry<Mod, EntryAnim> pair = iterator.next();

				if(active.contains(pair.getKey())) {
					continue;
				}

				EntryAnim anim = pair.getValue();

				anim.progress -= delta / EXIT_TIME;

				if(anim.progress <= 0.0F) {
					iterator.remove();
					continue;
				}

				drawRow(nvg, currentColor, pair.getKey(), anim.lastSlotY, anim.colorIndex,
						smoothstep(anim.progress), slideDirection, maxWidth, isRight);
			}
		}

		int index = 0;

		for(Mod m : enabledMods) {

			float slotY = index * ROW_HEIGHT;

			EntryAnim anim = entries.get(m);

			if(anim == null) {
				anim = new EntryAnim();
				// Rows cascade in from the top; later rows wait their turn.
				anim.delay = Math.min(index * STAGGER_STEP, 0.3F);
				anim.y = slotY;
				entries.put(m, anim);
			}

			anim.lastSlotY = slotY;
			anim.colorIndex = -(index * 10);

			if(anim.delay > 0.0F) {
				anim.delay -= delta;
			} else {
				anim.progress = Math.min(1.0F, anim.progress + (delta / ENTER_TIME));
			}

			// Exponential smoothing keeps the vertical reflow framerate independent.
			if(delta > 0.0F) {
				float t = 1.0F - (float) Math.exp(-14.0D * delta);
				anim.y += (slotY - anim.y) * t;

				if(Math.abs(slotY - anim.y) < 0.05F) {
					anim.y = slotY;
				}
			}

			drawRow(nvg, currentColor, m, anim.y, anim.colorIndex,
					smoothstep(Math.max(0.0F, Math.min(1.0F, anim.progress))), slideDirection, maxWidth, isRight);

			index++;
		}

		this.setWidth(maxWidth + 4);
		this.setHeight(Math.round(index * ROW_HEIGHT));
	}

	private void drawRow(NanoVGManager nvg, AccentColor currentColor, Mod m, float y, int colorIndex,
			float ease, float slideDirection, int maxWidth, boolean isRight) {

		float nameWidth = this.getTextWidth(m.getName(), 8.5F, getHudFont(1));
		float baseX = isRight ? (maxWidth - nameWidth) : 0;
		float slide = slideDirection * (1.0F - ease) * SLIDE_DISTANCE;

		if(ease <= 0.01F) {
			return;
		}

		if(backgroundSetting.isToggled()) {
			this.drawRect(baseX + slide, y, nameWidth + 5, ROW_HEIGHT, new Color(0, 0, 0, (int) (100 * ease)));
		}

		// Blur eases off as the row settles, so entries arrive soft and land crisp.
		nvg.save();

		if(ease < 0.995F) {
			nvg.fontBlur((1.0F - ease) * BLUR_RADIUS);
		}

		this.drawText(m.getName(), 3 + baseX + slide, y + 2.5F, 8.5F, getHudFont(1),
				ColorUtils.applyAlpha(currentColor.getInterpolateColor(colorIndex), (int) (ease * 255)));

		nvg.restore();
	}

	private static float smoothstep(float value) {
		return value * value * (3.0F - (2.0F * value));
	}
}
