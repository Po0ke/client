package me.eldodebug.soar.ui;

import java.util.ArrayList;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.ModCategory;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.animation.normal.Animation;
import me.eldodebug.soar.utils.animation.normal.Direction;
import me.eldodebug.soar.utils.animation.normal.easing.EaseBackIn;
import me.eldodebug.soar.utils.mouse.MorphCursor;

public class ClickEffects {

	private static ClickEffects instance;
	
	private ArrayList<ClickEffect> effects = new ArrayList<ClickEffect>();
	private ArrayList<ClickEffect> removeEffects = new ArrayList<ClickEffect>();
	
	public ClickEffects() {
		instance = this;
	}
	
	public void drawClickEffects() {
		
		for(ClickEffect ce : effects) {
			
			if(ce.isDone()) {
				removeEffects.add(ce);
			}
			
			ce.draw();
		}
		
		effects.removeAll(removeEffects);
	}
	
	/**
	 * Ripples out of the centre of whatever the pointer is hovering, so a click on a button reads as
	 * the button reacting rather than the pointer. Falls back to the raw click point.
	 */
	public void addClickEffect(int mouseX, int mouseY) {
		
		MorphCursor cursor = MorphCursor.getInstance();
		
		if(cursor.hasHoverTarget()) {
			effects.add(new ClickEffect(Math.round(cursor.getHoverCenterX()), Math.round(cursor.getHoverCenterY()), cursor.getHoverSpan() + 5.0F));
		} else {
			effects.add(new ClickEffect(mouseX, mouseY, 8.0F));
		}
	}
	
	public static ClickEffects getInstance() {
		return instance;
	}

	private class ClickEffect {
		
		private Animation animation;
		private int x, y;
		private float radius;
		
		private ClickEffect(int x, int y, float radius) {
			this.x = x;
			this.y = y;
			this.radius = radius;
			this.animation = new EaseBackIn(650, 1, 0.0F);
		}
		
		public void draw() {
			
			Glide instance = Glide.getInstance();
			NanoVGManager nvg = instance.getNanoVGManager();
			AccentColor currentColor = instance.getColorManager().getCurrentColor();
			
			nvg.setupAndDraw(() -> {
				nvg.drawArc(x, y, animation.getValueFloat() * radius, 0, 360, 2, ColorUtils.applyAlpha(currentColor.getInterpolateColor(0), (int) (255 - (animation.getValueFloat() * 255))));
			});
		}
		
		public boolean isDone() {
			return animation.isDone(Direction.FORWARDS);
		}
	}
}
