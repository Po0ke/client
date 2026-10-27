package me.eldodebug.soar.ui.ambient;

import java.awt.Color;
import java.util.Random;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.animation.spring.Spring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Ambient layer painted behind the mod menu panel: a sparse field of accent-tinted
 * drifting dots plus a soft cursor spotlight. Everything is delta-time based and lives
 * in scaled-resolution space, so it behaves identically at any framerate or GUI scale.
 *
 * It is drawn inside the menu's shadow pass, which the menu composites with its intro
 * alpha, so the whole layer fades and settles together with the panel for free.
 */
public class AmbientBackdrop {

	private static final int DOT_COUNT = 30;
	private static final float MARGIN = 24.0F;
	private static final float SPOTLIGHT_RADIUS = 105.0F;
	private static final int SPOTLIGHT_ALPHA = 14;

	private final Random random = new Random();

	private float[] dotX, dotY, dotVx, dotVy, dotRadius, dotPhase, dotTwinkle;
	private float fieldWidth, fieldHeight;

	private final Spring spotX = new Spring(0.0D, 18.0D, 1.0D);
	private final Spring spotY = new Spring(0.0D, 18.0D, 1.0D);

	private long lastNanos;
	private float elapsed;

	public void draw(NanoVGManager nvg, int mouseX, int mouseY) {

		long now = System.nanoTime();
		float delta = lastNanos == 0L ? 0.0F : (now - lastNanos) / 1_000_000_000F;
		lastNanos = now;

		if (delta > 0.1F) {
			delta = 0.1F;
		}

		elapsed += delta;

		ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());

		ensureField(sr.getScaledWidth(), sr.getScaledHeight());

		spotX.update(mouseX, delta);
		spotY.update(mouseY, delta);

		AccentColor accent = Glide.getInstance().getColorManager().getCurrentColor();

		// Spotlight sits at the back so the panel covers the middle; it reads as a
		// pool of accent light gathering around the pointer in the margins.
		nvg.drawRadialGlow(spotX.get(), spotY.get(), SPOTLIGHT_RADIUS,
				ColorUtils.applyAlpha(accent.getInterpolateColor(), SPOTLIGHT_ALPHA));

		for (int i = 0; i < DOT_COUNT; i++) {

			dotX[i] += dotVx[i] * delta;
			dotY[i] += dotVy[i] * delta;

			// Wrap around the field with a margin so dots never pop at the edges.
			if (dotX[i] < -MARGIN) dotX[i] = fieldWidth + MARGIN;
			if (dotX[i] > fieldWidth + MARGIN) dotX[i] = -MARGIN;
			if (dotY[i] < -MARGIN) dotY[i] = fieldHeight + MARGIN;
			if (dotY[i] > fieldHeight + MARGIN) dotY[i] = -MARGIN;

			float wobble = (float) Math.sin(elapsed * dotTwinkle[i] + dotPhase[i]) * 5.0F;
			float twinkle = (float) Math.sin(elapsed * 0.9F + dotPhase[i] * 1.7F);

			int alpha = (int) Math.max(4, 13 + twinkle * 5.0F);

			Color base = (i % 2 == 0) ? accent.getColor1() : accent.getColor2();

			nvg.drawCircle(dotX[i] + wobble, dotY[i], dotRadius[i], ColorUtils.applyAlpha(base, alpha));
		}
	}

	private void ensureField(int width, int height) {

		if (dotX != null && fieldWidth == width && fieldHeight == height) {
			return;
		}

		fieldWidth = width;
		fieldHeight = height;

		dotX = new float[DOT_COUNT];
		dotY = new float[DOT_COUNT];
		dotVx = new float[DOT_COUNT];
		dotVy = new float[DOT_COUNT];
		dotRadius = new float[DOT_COUNT];
		dotPhase = new float[DOT_COUNT];
		dotTwinkle = new float[DOT_COUNT];

		for (int i = 0; i < DOT_COUNT; i++) {

			dotX[i] = random.nextInt(Math.max(1, width));
			dotY[i] = random.nextInt(Math.max(1, height));

			// Slow, slightly diagonal drift - fast enough to notice, calm enough to ignore.
			dotVx[i] = (random.nextFloat() * 4.0F) - 2.0F;
			dotVy[i] = -2.0F - (random.nextFloat() * 4.0F);

			dotRadius[i] = 1.0F + (random.nextFloat() * 1.7F);
			dotPhase[i] = random.nextFloat() * 6.2831855F;
			dotTwinkle[i] = 0.5F + (random.nextFloat() * 0.9F);
		}
	}

	/** Drops cached layout and time so reopening the menu does not jump. */
	public void reset() {
		lastNanos = 0L;
		elapsed = 0.0F;
	}
}