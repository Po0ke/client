package me.eldodebug.soar.management.mods.impl;

import java.awt.Color;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.event.EventTarget;
import me.eldodebug.soar.management.event.impl.EventAttackEntity;
import me.eldodebug.soar.management.event.impl.EventRender2D;
import me.eldodebug.soar.management.event.impl.EventRenderCrosshair;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.ModCategory;
import me.eldodebug.soar.management.mods.settings.impl.BooleanSetting;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.animation.spring.Spring;
import net.minecraft.client.gui.ScaledResolution;

/**
 * A modern reticle made of four ring segments. It tightens on a target, opens horizontally while
 * sprinting, shrinks while sneaking, swells while blocking/eating and sends out a thin ring on
 * every hit. Everything is spring-driven so the change reads as motion, not a snap.
 */
public class DynamicCrosshairMod extends Mod {

	private static final long FLASH_NANOS = 200_000_000L;

	private final BooleanSetting targetShrinkSetting = new BooleanSetting(TranslateText.CROSSHAIR_TARGET_SHRINK, this, true);
	private final BooleanSetting hitFlashSetting = new BooleanSetting(TranslateText.CROSSHAIR_HIT_FLASH, this, true);
	private final BooleanSetting shapeShiftSetting = new BooleanSetting(TranslateText.CROSSHAIR_SHAPE_SHIFT, this, true);
	private final BooleanSetting hideVanillaSetting = new BooleanSetting(TranslateText.CROSSHAIR_HIDE_VANILLA, this, true);

	private final Spring radius = new Spring(8.0D, 28.0D, 0.82D);
	private final Spring horizontalArc = new Spring(54.0D, 26.0D, 0.85D);
	private final Spring verticalArc = new Spring(54.0D, 26.0D, 0.85D);

	private long lastFrameNanos;
	private long flashAt = -1L;

	public DynamicCrosshairMod() {
		super(TranslateText.DYNAMIC_CROSSHAIR, TranslateText.DYNAMIC_CROSSHAIR_DESCRIPTION, ModCategory.RENDER);
	}

	@Override
	public void onDisable() {
		super.onDisable();
		lastFrameNanos = 0L;
		flashAt = -1L;
	}

	@EventTarget
	public void onRenderCrosshair(EventRenderCrosshair event) {
		if(hideVanillaSetting.isToggled()) {
			event.setCancelled(true);
		}
	}

	@EventTarget
	public void onAttackEntity(EventAttackEntity event) {
		flashAt = System.nanoTime();
	}

	@EventTarget
	public void onRender2D(EventRender2D event) {

		if(mc.currentScreen != null || mc.thePlayer == null || mc.gameSettings.hideGUI) {
			return;
		}

		long now = System.nanoTime();
		float delta = lastFrameNanos == 0L ? 0.0F : (now - lastFrameNanos) / 1_000_000_000F;
		lastFrameNanos = now;
		delta = Math.max(0.0F, Math.min(0.1F, delta));

		boolean target = mc.objectMouseOver != null && mc.objectMouseOver.entityHit != null;
		boolean blocking = mc.thePlayer.isBlocking() || mc.thePlayer.getItemInUseCount() > 0;
		boolean sprinting = mc.thePlayer.isSprinting();
		boolean sneaking = mc.thePlayer.isSneaking();

		double radiusTarget = 8.0D;
		double horizontalTarget = 54.0D;
		double verticalTarget = 54.0D;

		if(targetShrinkSetting.isToggled() && target) {
			radiusTarget = 6.0D;
			horizontalTarget = 64.0D;
			verticalTarget = 64.0D;
		}

		if(shapeShiftSetting.isToggled()) {

			if(sprinting) {
				horizontalTarget = 72.0D;
				verticalTarget = 36.0D;
			}

			if(sneaking) {
				radiusTarget -= 1.2D;
				horizontalTarget = 42.0D;
				verticalTarget = 42.0D;
			}
		}

		if(blocking) {
			radiusTarget += 1.8D;
			horizontalTarget += 5.0D;
			verticalTarget += 5.0D;
		}

		radius.update(radiusTarget, delta);
		horizontalArc.update(horizontalTarget, delta);
		verticalArc.update(verticalTarget, delta);

		float flash = flashProgress(now);

		NanoVGManager nvg = Glide.getInstance().getNanoVGManager();
		nvg.setupAndDraw(() -> draw(nvg, flash, target, blocking));
	}

	private void draw(NanoVGManager nvg, float flash, boolean target, boolean blocking) {

		ScaledResolution sr = new ScaledResolution(mc);

		float centerX = sr.getScaledWidth() / 2.0F;
		float centerY = sr.getScaledHeight() / 2.0F;
		float ringRadius = radius.get();
		float horizontal = horizontalArc.get();
		float vertical = verticalArc.get();

		AccentColor accent = Glide.getInstance().getColorManager().getCurrentColor();
		Color accentColor = accent.getInterpolateColor();
		Color color = (target || blocking) ? accentColor : new Color(255, 255, 255, 225);

		drawArcPair(nvg, centerX, centerY, ringRadius, horizontal, vertical, new Color(0, 0, 0, 105), 2.9F);
		drawArcPair(nvg, centerX, centerY, ringRadius, horizontal, vertical, color, 1.7F);

		nvg.drawCircle(centerX, centerY, 1.15F, color);

		if(blocking) {
			nvg.drawArc(centerX, centerY, ringRadius + 3.0F, 0.0F, 360.0F, 0.8F,
					ColorUtils.applyAlpha(accentColor, 85));
		}

		if(flash > 0.01F) {
			float flashRadius = ringRadius + 5.0F + ((1.0F - flash) * 12.0F);
			nvg.drawArc(centerX, centerY, flashRadius, 0.0F, 360.0F, 1.2F + (1.0F * flash),
					ColorUtils.applyAlpha(accentColor, (int) (200.0F * flash)));
			nvg.drawArc(centerX, centerY, flashRadius + 2.0F, 0.0F, 360.0F, 0.7F,
					new Color(255, 255, 255, (int) (110.0F * flash)));
		}
	}

	private void drawArcPair(NanoVGManager nvg, float x, float y, float radius, float horizontal, float vertical,
			Color color, float stroke) {

		// Right / left segments are wider on sprint; top / bottom are shorter.
		nvg.drawArc(x, y, radius, -horizontal / 2.0F, horizontal / 2.0F, stroke, color);
		nvg.drawArc(x, y, radius, 180.0F - (horizontal / 2.0F), 180.0F + (horizontal / 2.0F), stroke, color);
		nvg.drawArc(x, y, radius, 90.0F - (vertical / 2.0F), 90.0F + (vertical / 2.0F), stroke, color);
		nvg.drawArc(x, y, radius, 270.0F - (vertical / 2.0F), 270.0F + (vertical / 2.0F), stroke, color);
	}

	private float flashProgress(long now) {

		if(flashAt < 0L) {
			return 0.0F;
		}

		long elapsed = now - flashAt;

		if(elapsed >= FLASH_NANOS) {
			flashAt = -1L;
			return 0.0F;
		}

		return 1.0F - (elapsed / (float) FLASH_NANOS);
	}
}
