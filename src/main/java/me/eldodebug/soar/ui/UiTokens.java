package me.eldodebug.soar.ui;

/**
 * Shared design tokens for the interface.
 *
 * These are deliberately plain constants: scenes can adopt them incrementally without any
 * behavioural change, and new work has one place to look instead of copying magic numbers.
 */
public final class UiTokens {

	private UiTokens() {
	}

	/* Corner radii */
	public static final float RADIUS_TINY = 4.0F;
	public static final float RADIUS_SMALL = 6.0F;
	public static final float RADIUS_CARD = 8.0F;
	public static final float RADIUS_PANEL = 12.0F;

	/* Spacing */
	public static final float SPACE_XS = 4.0F;
	public static final float SPACE_SM = 8.0F;
	public static final float SPACE_MD = 12.0F;
	public static final float SPACE_LG = 16.0F;

	/* Vertical rhythm of a module row */
	public static final float ROW_HEIGHT = 40.0F;
	public static final float ROW_GAP = 10.0F;
	public static final float ROW_PITCH = ROW_HEIGHT + ROW_GAP;

	/* Accent overlay strengths, used for hover, selection and pressed states */
	public static final int TINT_HOVER = 22;
	public static final int TINT_SELECTED = 48;
	public static final int TINT_PRESSED = 62;

	/* Motion */
	public static final double SPRING_SNAPPY = 30.0D;
	public static final double SPRING_DEFAULT = 22.0D;
	public static final double SPRING_SOFT = 16.0D;

	public static final float DURATION_FAST = 0.12F;
	public static final float DURATION_NORMAL = 0.22F;

	/* Interaction */
	public static final float PRESS_SCALE = 0.985F;
	public static final long TOOLTIP_DELAY_MS = 450L;
}
