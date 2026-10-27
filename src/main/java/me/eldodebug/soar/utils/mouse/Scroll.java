package me.eldodebug.soar.utils.mouse;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import me.eldodebug.soar.utils.animation.simple.SimpleAnimation;

public class Scroll {

	/** How quickly the glide after a wheel notch dies out, in seconds. */
	private static final float MOMENTUM_DECAY = 0.18F;

	/** Momentum distance is roughly one extra wheel notch, so the list stays easy to aim. */
	private static final float MOMENTUM_FACTOR = 5.0F;

	private float maxScroll = Float.MAX_VALUE, minScroll = 0, rawScroll;
	private SimpleAnimation scrollAnimation = new SimpleAnimation(0.0F);

	private float velocity;
	private long lastUpdateNanos = System.nanoTime();

	public void onScroll() {
		onScroll(2);
	}

	public void onAnimation() {
		onAnimation(14);
	}

	private void onScroll(int scrollSpeed) {

		int dWheel = Mouse.getDWheel();

		if (dWheel == 0) {
			return;
		}

		float multiplier = (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) ? 2.0F : 1.0F;
		float delta = (dWheel / (float) (scrollSpeed * 2)) * multiplier;

		rawScroll = clampScroll(rawScroll + delta);

		// Continue in the same direction for a moment after the wheel stops, which is the
		// difference between "steps" and "scrolling". A new notch replaces the old glide
		// instead of stacking on it, otherwise fast scrolling runs away.
		velocity = delta * MOMENTUM_FACTOR;
	}

	public void onKey(int keyCode) {

		int amount = 0;

		if(keyCode == Keyboard.KEY_DOWN) {amount = -30;}
		if(keyCode == Keyboard.KEY_UP) {amount = 30;}

		rawScroll = clampScroll(rawScroll + amount);
		velocity = 0.0F;
	}

	public void manualScroll(int amount) {

		rawScroll = clampScroll(rawScroll + amount);
		velocity = 0.0F;
	}

	private void onAnimation(int animationSpeed) {

		applyMomentum();

		scrollAnimation.setAnimation(rawScroll, animationSpeed);
	}

	private void applyMomentum() {

		long now = System.nanoTime();

		float delta = (now - lastUpdateNanos) / 1_000_000_000F;
		lastUpdateNanos = now;

		if (velocity == 0.0F) {
			return;
		}

		if (delta <= 0.0F || delta > 0.1F) {
			delta = 0.05F;
		}

		float moved = rawScroll + (velocity * delta);
		float clamped = clampScroll(moved);

		rawScroll = clamped;

		if (clamped != moved) {
			// Hit the end of the list, stop instead of pushing against it.
			velocity = 0.0F;
			return;
		}

		velocity *= (float) Math.exp(-delta / MOMENTUM_DECAY);

		if (Math.abs(velocity) < 2.0F) {
			velocity = 0.0F;
		}
	}

	private float clampScroll(float value) {
		return Math.max(Math.min(minScroll, value), -maxScroll);
	}

	public void setMaxScroll(float scroll) {
		maxScroll = scroll;
	}

	public float getValue() {
		return scrollAnimation.getValue();
	}

	public void setScrollPosition(float scroll) {
		rawScroll = scroll;
		scrollAnimation.setValue(scroll);
		velocity = 0.0F;
	}

	public void reset() {
		rawScroll = minScroll;
		velocity = 0.0F;
	}

	public void resetAll() {
		rawScroll = minScroll;
		scrollAnimation.setValue(minScroll);
		velocity = 0.0F;
		lastUpdateNanos = System.nanoTime();
	}
}
