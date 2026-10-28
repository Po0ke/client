package me.eldodebug.soar.management.mods.impl;

import java.awt.Color;
import java.util.ArrayList;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.event.EventTarget;
import me.eldodebug.soar.management.event.impl.EventClickMouse;
import me.eldodebug.soar.management.event.impl.EventRender2D;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.mods.HUDMod;
import me.eldodebug.soar.management.mods.settings.impl.BooleanSetting;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.utils.animation.simple.SimpleAnimation;

public class KeystrokesMod extends HUDMod {

	private BooleanSetting spaceSetting = new BooleanSetting(TranslateText.SPACE, this, true);
	private BooleanSetting unmarkedSetting = new BooleanSetting(TranslateText.UNMARKED, this, false);
	private BooleanSetting cpsSetting = new BooleanSetting(TranslateText.KEYSTROKES_CPS, this, true);

	private SimpleAnimation[] animations = new SimpleAnimation[5];
	private final ArrayList<Long> leftPresses = new ArrayList<Long>();

	public KeystrokesMod() {
		super(TranslateText.KEYSTROKES, TranslateText.KEYSTROKES_DESCRIPTION);

		for(int i = 0; i < 5; i++) {
			animations[i] = new SimpleAnimation();
		}
	}

	@EventTarget
	public void onRender2D(EventRender2D event) {
		NanoVGManager nvg = Glide.getInstance().getNanoVGManager();
		nvg.setupAndDraw(() -> drawNanoVG());
	}

	@EventTarget
	public void onClickMouse(EventClickMouse event) {
		if(Mouse.getEventButtonState() && event.getButton() == 0) {
			leftPresses.add(System.currentTimeMillis());
		}
	}

	private void drawNanoVG() {

		boolean openGui = mc.currentScreen != null;

		animations[0].setAnimation(!openGui && Keyboard.isKeyDown(mc.gameSettings.keyBindForward.getKeyCode()) ? 1.0F : 0.0F, 16);
		animations[1].setAnimation(!openGui && Keyboard.isKeyDown(mc.gameSettings.keyBindLeft.getKeyCode()) ? 1.0F : 0.0F, 16);
		animations[2].setAnimation(!openGui && Keyboard.isKeyDown(mc.gameSettings.keyBindBack.getKeyCode()) ? 1.0F : 0.0F, 16);
		animations[3].setAnimation(!openGui && Keyboard.isKeyDown(mc.gameSettings.keyBindRight.getKeyCode()) ? 1.0F : 0.0F, 16);
		animations[4].setAnimation(!openGui && Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) ? 1.0F : 0.0F, 16);

		leftPresses.removeIf(t -> System.currentTimeMillis() - t > 1000);

		// W
		this.drawBackground(32, 0, 28, 28);

		// A
		this.drawBackground(0, 32, 28, 28);

		// S
		this.drawBackground(32, 32, 28, 28);

		// D
		this.drawBackground(64, 32, 28, 28);

		this.save();
		this.scale(32, 0, 28, 28, animations[0].getValue());
		this.drawHighlight(32, 0, 28, 28, 6, this.getFontColor((int) (120 * animations[0].getValue())));
		this.restore();

		this.save();
		this.scale(0, 32, 28, 28, animations[1].getValue());
		this.drawHighlight(0, 32, 28, 28, 6, this.getFontColor((int) (120 * animations[1].getValue())));
		this.restore();

		this.save();
		this.scale(32, 32, 28, 28, animations[2].getValue());
		this.drawHighlight(32, 32, 28, 28, 6, this.getFontColor((int) (120 * animations[2].getValue())));
		this.restore();

		this.save();
		this.scale(64, 32, 28, 28, animations[3].getValue());
		this.drawHighlight(64, 32, 28, 28, 6, this.getFontColor((int) (120 * animations[3].getValue())));
		this.restore();

		if(!unmarkedSetting.isToggled()) {
			this.drawCenteredText(Keyboard.getKeyName(mc.gameSettings.keyBindForward.getKeyCode()), 32 + (28 / 2), (28 / 2) - 4, 12, getHudFont(1));
			this.drawCenteredText(Keyboard.getKeyName(mc.gameSettings.keyBindLeft.getKeyCode()), 0 + (28 / 2), 32 + (28 / 2) - 4, 12, getHudFont(1));
			this.drawCenteredText(Keyboard.getKeyName(mc.gameSettings.keyBindBack.getKeyCode()), 32 + (28 / 2), 32 + (28 / 2) - 4, 12, getHudFont(1));
			this.drawCenteredText(Keyboard.getKeyName(mc.gameSettings.keyBindRight.getKeyCode()), 64 + (28 / 2), 32 + (28 / 2) - 4, 12, getHudFont(1));
		}

		if(spaceSetting.isToggled()) {

			this.drawBackground(0, 64, (28 * 3) + 8, 22);

			this.save();
			this.scale(0, 64, (28 * 3) + 8, 22, animations[4].getValue());
			this.drawHighlight(0, 64, (28 * 3) + 8, 22, 6, this.getFontColor((int) (120 * animations[4].getValue())));
			this.restore();

			if(!unmarkedSetting.isToggled()) {
				this.drawRoundedRect(10, 74F, (26 * 3) - 6, 2, 1);
			}
		}

		if(cpsSetting.isToggled()) {
			drawCps();
		}

		this.setWidth(28 * 3 + 8);
		this.setHeight(spaceSetting.isToggled() ? 64 + 22 : 32 + 28);
	}

	private void drawCps() {

		AccentColor accent = Glide.getInstance().getColorManager().getCurrentColor();
		int cps = leftPresses.size();

		this.drawCenteredText(String.valueOf(cps), 64 + 14, 7, 13, getHudFont(2), accent.getInterpolateColor());
		this.drawCenteredText("CPS", 64 + 14, 19, 6, getHudFont(1), this.getFontColor(180));
	}

	private void drawHighlight(float addX, float addY, float width, float height, float radius, Color color) {
		boolean rect = InternalSettingsMod.getInstance().getModThemeSetting().getOption().getTranslate().equals(TranslateText.RECT);
		if (!rect) this.drawRoundedRect(addX, addY, width, height, radius, color); else this.drawRect(addX, addY, width, height, color);
	}
}
