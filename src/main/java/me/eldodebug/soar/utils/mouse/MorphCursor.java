package me.eldodebug.soar.utils.mouse;

import java.awt.Color;
import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.input.Cursor;
import org.lwjgl.input.Mouse;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.logger.GlideLogger;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.ui.UiTokens;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.animation.spring.Spring;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/**
 * A single overlay cursor for Glide's interfaces.
 *
 * The pointer is modelled as a rounded rectangle (x, y, width, height, radius) instead of a
 * circle, because a circle is just the case width == height and radius == width / 2. That makes
 * "follow the mouse" and "hug the hovered button" the same interpolation of five numbers, with
 * no shape switching and no seam when a target is picked up or dropped.
 *
 * Rendering is immediate mode, so hover targets are re-declared every frame: call
 * {@link #beginFrame(int, int)} before drawing a screen, {@link #hover(float, float, float, float, float)}
 * from each interactive element (paint order decides overlap, last one wins) and {@link #draw()}
 * once at the very end.
 */
public class MorphCursor {

	private static final MorphCursor INSTANCE = new MorphCursor();

	private static final float IDLE_DIAMETER = 16.0F;
	private static final float HOVER_INFLATE = 4.0F;
	private static final float MIN_RADIUS = 3.0F;

	private static final double POSITION_OMEGA = 24.0D;
	private static final double SIZE_OMEGA = 20.0D;
	private static final double SIZE_DAMPING = 0.75D;

	private final Spring x = new Spring(0.0D, POSITION_OMEGA, 1.0D);
	private final Spring y = new Spring(0.0D, POSITION_OMEGA, 1.0D);
	private final Spring width = new Spring(0.0D, SIZE_OMEGA, SIZE_DAMPING);
	private final Spring height = new Spring(0.0D, SIZE_OMEGA, SIZE_DAMPING);
	private final Spring radius = new Spring(0.0D, SIZE_OMEGA, SIZE_DAMPING);
	private final Spring hoverAmount = new Spring(0.0D, 18.0D, 1.0D);
	private final Spring pressAmount = new Spring(0.0D, 32.0D, 0.9D);

	private boolean enabled;
	private boolean visible;
	private boolean initialized;
	private boolean nativeCursorHidden;
	private boolean nativeCursorHidePending;
	private boolean pressed;

	private boolean hasHover;
	private float hoverX, hoverY, hoverWidth, hoverHeight, hoverRadius;

	private String tooltipText;
	private String lastTooltip;
	private long tooltipStartNanos;

	private int hitX, hitY;
	private float preciseX, preciseY;
	private float globalAlpha = 1.0F;

	private long lastFrameNanos;
	private Cursor blankCursor;

	private MorphCursor() {
	}

	public static MorphCursor getInstance() {
		return INSTANCE;
	}

	public boolean isEnabled() {
		return enabled;
	}

	/**
	 * Turns the overlay on or off. Enabling hides the native cursor, disabling restores it.
	 * Safe to call every frame; only real transitions do work.
	 */
	public void setEnabled(boolean enabled) {
		if (this.enabled == enabled) {
			return;
		}

		this.enabled = enabled;

		if (enabled) {
			this.visible = true;
			this.initialized = false;
			this.lastFrameNanos = System.nanoTime();
			this.hasHover = false;
			this.pressed = false;
			// Only hide the real pointer once the overlay has actually rendered, so a
			// rendering failure can never leave the user without a visible cursor.
			this.nativeCursorHidePending = true;
		} else {
			this.visible = false;
			this.hasHover = false;
			this.pressed = false;
			restoreNativeCursor();
		}
	}

	/**
	 * Re-applies the hidden cursor. Minecraft recreates the display and calls initGui again on
	 * resize or a fullscreen toggle, which can reset the native cursor behind our back.
	 */
	public void refreshNativeCursor() {

		if (!enabled || !nativeCursorHidden) {
			return;
		}

		nativeCursorHidden = false;
		nativeCursorHidePending = true;
	}

	/**
	 * Must run when the screen closes, otherwise the pointer could stay hidden.
	 */
	public void onScreenClosed() {
		this.enabled = false;
		this.visible = false;
		this.hasHover = false;
		this.pressed = false;
		this.nativeCursorHidePending = false;
		restoreNativeCursor();
	}

	public void setGlobalAlpha(float alpha) {
		this.globalAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
	}

	public void beginFrame(int mouseX, int mouseY) {
		this.hitX = mouseX;
		this.hitY = mouseY;
		this.hasHover = false;
		this.tooltipText = null;

		updatePrecisePosition(mouseX, mouseY);
	}

	/**
	 * Declares that the rectangle under the pointer is interactive. Later calls in the same frame
	 * win, which matches paint order and resolves overlapping elements for free.
	 */
	public void hover(float x, float y, float width, float height, float radius) {
		hover(x, y, width, height, radius, null);
	}

	/**
	 * @param tooltip shown after a short dwell, or null for none
	 */
	public void hover(float x, float y, float width, float height, float radius, String tooltip) {
		if (!enabled || !visible) {
			return;
		}

		if (hitX > x && hitX < x + width && hitY > y && hitY < y + height) {
			this.hasHover = true;

			this.hoverX = x;
			this.hoverY = y;
			this.hoverWidth = width;
			this.hoverHeight = height;
			this.hoverRadius = radius;

			if (tooltip != null) {

				if (!tooltip.equals(lastTooltip)) {
					lastTooltip = tooltip;
					tooltipStartNanos = System.nanoTime();
				}

				tooltipText = tooltip;
			}
		}
	}

	public boolean hasHoverTarget() {
		return enabled && visible && hasHover;
	}

	public float getHoverCenterX() {
		return hoverX + (hoverWidth / 2.0F);
	}

	public float getHoverCenterY() {
		return hoverY + (hoverHeight / 2.0F);
	}

	/**
	 * Half the longer side of the hovered element, so a click ripple can grow to cover it.
	 */
	public float getHoverSpan() {
		return Math.max(hoverWidth, hoverHeight) / 2.0F;
	}

	public void onMouseDown() {
		this.pressed = true;
	}

	public void onMouseUp() {
		this.pressed = false;
	}

	public void draw() {
		if (!enabled || !visible) {
			return;
		}

		update();

		final NanoVGManager nvg = Glide.getInstance().getNanoVGManager();

		nvg.setupAndDraw(() -> render(nvg));

		if (nativeCursorHidePending) {
			nativeCursorHidePending = false;
			hideNativeCursor();
		}
	}

	private void update() {

		long now = System.nanoTime();

		if (!initialized) {
			lastFrameNanos = now;
			applyTargets();
			snapSprings();
			initialized = true;
			return;
		}

		float delta = (now - lastFrameNanos) / 1_000_000_000F;
		lastFrameNanos = now;

		if (delta <= 0.0F) {
			return;
		}

		if (delta > 0.1F) {
			delta = 0.1F;
		}

		applyTargets();

		x.update(delta);
		y.update(delta);
		width.update(delta);
		height.update(delta);
		radius.update(delta);
		hoverAmount.update(delta);
		pressAmount.update(delta);
	}

	private void applyTargets() {

		float alpha = globalAlpha;

		// The idle ring grows out of nothing while the screen fades in, which hides the
		// first frame where the springs are still at their initial position.
		float diameter = IDLE_DIAMETER * (0.55F + 0.45F * alpha);

		if (hasHover) {

			float targetWidth = hoverWidth + (HOVER_INFLATE * 2.0F);
			float targetHeight = hoverHeight + (HOVER_INFLATE * 2.0F);

			x.setTarget(hoverX - HOVER_INFLATE);
			y.setTarget(hoverY - HOVER_INFLATE);
			width.setTarget(targetWidth);
			height.setTarget(targetHeight);
			radius.setTarget(Math.min(Math.max(hoverRadius + HOVER_INFLATE, MIN_RADIUS), Math.min(targetWidth, targetHeight) / 2.0F));
			hoverAmount.setTarget(1.0D);
		} else {

			x.setTarget(preciseX - (diameter / 2.0F));
			y.setTarget(preciseY - (diameter / 2.0F));
			width.setTarget(diameter);
			height.setTarget(diameter);
			radius.setTarget(diameter / 2.0F);
			hoverAmount.setTarget(0.0D);
			lastTooltip = null;
		}

		pressAmount.setTarget(pressed ? 1.0D : 0.0D);
	}

	private void snapSprings() {
		x.snap();
		y.snap();
		width.snap();
		height.snap();
		radius.snap();
		hoverAmount.snap();
		pressAmount.snap();
	}

	private void render(NanoVGManager nvg) {

		float alpha = globalAlpha;

		if (alpha <= 0.01F) {
			return;
		}

		float centerX = x.get() + (width.get() / 2.0F);
		float centerY = y.get() + (height.get() / 2.0F);

		float press = pressAmount.get();
		float hover = hoverAmount.get();

		float scale = 1.0F - (0.055F * press);
		float drawWidth = Math.max(1.0F, width.get() * scale);
		float drawHeight = Math.max(1.0F, height.get() * scale);

		float drawX = centerX - (drawWidth / 2.0F);
		float drawY = centerY - (drawHeight / 2.0F);
		float drawRadius = Math.min(radius.get() * scale, Math.min(drawWidth, drawHeight) / 2.0F);

		int baseAlpha = clampAlpha((int) (255.0F * alpha));

		AccentColor accentColor = Glide.getInstance().getColorManager().getCurrentColor();

		Color spotlight = accentColor.getColor1();

		nvg.drawRadialGlow(preciseX, preciseY, 78, new Color(spotlight.getRed(), spotlight.getGreen(), spotlight.getBlue(), 26));

		if (hover > 0.01F) {
			int fillAlpha = clampAlpha((int) ((hover * 26.0F * alpha) + (press * 10.0F * alpha)));

			nvg.drawRoundedRect(drawX, drawY, drawWidth, drawHeight, drawRadius,
					ColorUtils.applyAlpha(accentColor.getInterpolateColor(), fillAlpha));
		}

		float stroke = 1.55F - (0.45F * hover) + (0.25F * press);

		nvg.drawGradientOutlineRoundedRect(drawX, drawY, drawWidth, drawHeight, drawRadius, stroke,
				ColorUtils.applyAlpha(accentColor.getColor1(), baseAlpha),
				ColorUtils.applyAlpha(accentColor.getColor2(), baseAlpha));

		// A precise dot keeps pointing exact even though the ring lags behind.
		nvg.drawCircle(preciseX, preciseY, 1.5F + (0.35F * press), new Color(255, 255, 255, clampAlpha((int) (235.0F * alpha))));

		long tooltipDelayNanos = UiTokens.TOOLTIP_DELAY_MS * 1_000_000L;
		long elapsed = System.nanoTime() - tooltipStartNanos;

		if (tooltipText != null && elapsed > tooltipDelayNanos) {
			drawTooltip(nvg, Math.min(1.0F, (elapsed - tooltipDelayNanos) / 120_000_000F));
		}
	}

	private void drawTooltip(NanoVGManager nvg, float alpha) {

		ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());

		float padding = 6.0F;
		float width = nvg.getTextWidth(tooltipText, 8, Fonts.REGULAR) + (padding * 2.0F);
		float height = 16.0F;

		float tooltipX = preciseX + 14.0F;
		float tooltipY = preciseY + 18.0F;

		if (tooltipX + width > sr.getScaledWidth() - 4) {
			tooltipX = sr.getScaledWidth() - width - 4;
		}

		if (tooltipY + height > sr.getScaledHeight() - 4) {
			tooltipY = preciseY - height - 10;
		}

		tooltipX = Math.max(4.0F, tooltipX);
		tooltipY = Math.max(4.0F, tooltipY);

		float totalAlpha = alpha * globalAlpha;

		nvg.drawRoundedRect(tooltipX, tooltipY, width, height, 4, new Color(20, 20, 24, clampAlpha((int) (235.0F * totalAlpha))));
		nvg.drawText(tooltipText, tooltipX + padding, tooltipY + 4.0F, new Color(235, 235, 240, clampAlpha((int) (255.0F * totalAlpha))), 8, Fonts.REGULAR);
	}

	private void updatePrecisePosition(int mouseX, int mouseY) {

		Minecraft mc = Minecraft.getMinecraft();

		if (mc.displayWidth <= 0 || mc.displayHeight <= 0 || !Mouse.isCreated()) {
			preciseX = mouseX;
			preciseY = mouseY;
			return;
		}

		ScaledResolution sr = new ScaledResolution(mc);

		// Mirrors how Minecraft derives the integer mouse position it hands to GuiScreen,
		// but keeps the sub-pixel part so the dot stays smooth at GUI scale 2 and above.
		preciseX = Mouse.getX() * (float) sr.getScaledWidth() / (float) mc.displayWidth;
		preciseY = (float) sr.getScaledHeight() - (Mouse.getY() * (float) sr.getScaledHeight() / (float) mc.displayHeight) - 1.0F;
	}

	private void hideNativeCursor() {

		if (nativeCursorHidden || !Mouse.isCreated()) {
			return;
		}

		try {

			if (blankCursor == null) {

				// A fully transparent 16x16 cursor. Power-of-two size is the portable choice.
				IntBuffer image = BufferUtils.createIntBuffer(16 * 16);

				blankCursor = new Cursor(16, 16, 0, 0, 1, image, null);
			}

			Mouse.setNativeCursor(blankCursor);
			nativeCursorHidden = true;
		} catch (Exception e) {
			GlideLogger.error("Failed to hide the native cursor", e);
			nativeCursorHidden = false;
		}
	}

	private void restoreNativeCursor() {

		if (!nativeCursorHidden && !nativeCursorHidePending) {
			return;
		}

		nativeCursorHidePending = false;

		if (!Mouse.isCreated()) {
			nativeCursorHidden = false;
			return;
		}

		try {
			Mouse.setNativeCursor(null);
		} catch (Exception e) {
			GlideLogger.error("Failed to restore the native cursor", e);
		}

		nativeCursorHidden = false;
	}

	private int clampAlpha(int alpha) {
		return Math.max(0, Math.min(255, alpha));
	}
}
