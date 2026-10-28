package me.eldodebug.soar.gui.modmenu.category.impl;

import java.awt.Color;
import java.util.ArrayList;

import org.lwjgl.input.Keyboard;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.gui.modmenu.GuiModMenu;
import me.eldodebug.soar.gui.modmenu.category.Category;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.color.ColorManager;
import me.eldodebug.soar.management.color.palette.ColorPalette;
import me.eldodebug.soar.management.color.palette.ColorType;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.mods.Mod;
import me.eldodebug.soar.management.mods.ModCategory;
import me.eldodebug.soar.management.misc.Favourites;
import me.eldodebug.soar.management.misc.Recents;
import me.eldodebug.soar.management.mods.ModManager;
import me.eldodebug.soar.management.mods.settings.Setting;
import me.eldodebug.soar.management.mods.settings.impl.BooleanSetting;
import me.eldodebug.soar.management.mods.settings.impl.ColorSetting;
import me.eldodebug.soar.management.mods.settings.impl.ComboSetting;
import me.eldodebug.soar.management.mods.settings.impl.ImageSetting;
import me.eldodebug.soar.management.mods.settings.impl.KeybindSetting;
import me.eldodebug.soar.management.mods.settings.impl.NumberSetting;
import me.eldodebug.soar.management.mods.settings.impl.SoundSetting;
import me.eldodebug.soar.management.mods.settings.impl.TextSetting;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.management.nanovg.font.LegacyIcon;
import me.eldodebug.soar.ui.UiTokens;
import me.eldodebug.soar.ui.comp.Comp;
import me.eldodebug.soar.ui.comp.impl.CompColorPicker;
import me.eldodebug.soar.ui.comp.impl.CompComboBox;
import me.eldodebug.soar.ui.comp.impl.CompImageSelect;
import me.eldodebug.soar.ui.comp.impl.CompKeybind;
import me.eldodebug.soar.ui.comp.impl.CompSlider;
import me.eldodebug.soar.ui.comp.impl.CompSoundSelect;
import me.eldodebug.soar.ui.comp.impl.CompToggleButton;
import me.eldodebug.soar.ui.comp.impl.field.CompModTextBox;
import me.eldodebug.soar.utils.ColorUtils;
import me.eldodebug.soar.utils.MathUtils;
import me.eldodebug.soar.utils.SearchUtils;
import me.eldodebug.soar.utils.animation.normal.Animation;
import me.eldodebug.soar.utils.animation.normal.Direction;
import me.eldodebug.soar.utils.animation.normal.other.SmoothStepAnimation;
import me.eldodebug.soar.utils.animation.simple.SimpleAnimation;
import me.eldodebug.soar.utils.mouse.MorphCursor;
import me.eldodebug.soar.utils.mouse.MouseUtils;
import me.eldodebug.soar.utils.mouse.Scroll;

public class ModuleCategory extends Category {

	private ModCategory currentCategory;
	
	private Scroll settingScroll = new Scroll();
	private boolean openSetting;
	private Animation settingAnimation;
	private Mod currentMod;
	Color noColour = new Color(0, 0, 0, 0);
	
	private ArrayList<ModuleSetting> comps = new ArrayList<ModuleSetting>();
	
	private static final float ROW_PITCH = 50F;
	private static final float LIST_TOP = 36F;
	
	private final ArrayList<Mod> orderedMods = new ArrayList<Mod>();
	private final SimpleAnimation scrollbarAnimation = new SimpleAnimation();
	
	private Mod pressedMod;
	private Mod lastClickedMod;
	private Mod keyboardMod;
	private long categoryOpenedAt;
	
	private boolean emptyStateVisible;
	private float clearSearchX, clearSearchY, clearSearchWidth, clearSearchHeight;
	
	private Mod contextMod;
	private float contextX, contextY;
	private boolean pinnedOnly;
	private final SimpleAnimation pinnedAnimation = new SimpleAnimation();
	private static final float CTX_WIDTH = 124;
	private static final float CTX_ITEM = 20;
	private static final float CTX_PAD = 4;
	private static final int CTX_ITEMS = 5;
	private static final float CTX_HEIGHT = (CTX_PAD * 2) + (CTX_ITEM * CTX_ITEMS);
	
	public ModuleCategory(GuiModMenu parent) {
		super(parent, TranslateText.MODULE, LegacyIcon.ARCHIVE, true, true);
	}
	
	@Override
	public void initGui() {
		currentCategory = ModCategory.ALL;
		openSetting = false;
		settingAnimation = new SmoothStepAnimation(260, 1.0);
		settingAnimation.setValue(1.0);
		categoryOpenedAt = System.currentTimeMillis();
	}

	@Override
	public void initCategory() {
		scroll.resetAll();
		openSetting = false;
		settingAnimation = new SmoothStepAnimation(260, 1.0);
		settingAnimation.setValue(1.0);
		categoryOpenedAt = System.currentTimeMillis();
		lastClickedMod = null;
		pressedMod = null;
		contextMod = null;
		keyboardMod = null;
		pinnedOnly = false;
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		
		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		ModManager modManager = instance.getModManager();
		ColorManager colorManager = instance.getColorManager();
		ColorPalette palette = colorManager.getPalette();
		AccentColor accentColor = colorManager.getCurrentColor();

		int offsetX = 0;
		float offsetY = 13;
		int index = 1;
		float scrollValue = scroll.getValue();
		
		settingAnimation.setDirection(openSetting ? Direction.BACKWARDS : Direction.FORWARDS);
		
		if(settingAnimation.isDone(Direction.FORWARDS)) {
			this.setCanClose(true);
			currentMod = null;
		}
		
		boolean settingsInteractive = openSetting && settingAnimation.isDone(Direction.BACKWARDS);
		
		nvg.save();
		nvg.translate((float) -(600 - (settingAnimation.getValue() * 600)), 0);
		
		//Draw mod scene
		
		nvg.save();
		nvg.translate(0, scrollValue);
		
		for(ModCategory c : ModCategory.values()) {
			
			float textWidth = nvg.getTextWidth(c.getName(), 9, Fonts.MEDIUM);
			boolean isCurrentCategory = c.equals(currentCategory);
			
			c.getBackgroundAnimation().setAnimation(isCurrentCategory ? 1.0F : 0.0F, 16);
			
			Color defaultColor = palette.getBackgroundColor(ColorType.DARK);
			Color color1 = ColorUtils.applyAlpha(accentColor.getColor1(), (int) (c.getBackgroundAnimation().getValue() * 255));
			Color color2 = ColorUtils.applyAlpha(accentColor.getColor2(), (int) (c.getBackgroundAnimation().getValue() * 255));
			Color textColor = c.getTextColorAnimation().getColor(isCurrentCategory ? Color.WHITE : palette.getFontColor(ColorType.DARK), 20);

			nvg.drawRoundedRect(this.getX() + 15 + offsetX, this.getY() + offsetY - 3, textWidth + 20, 16, 6, defaultColor);
			nvg.drawGradientRoundedRect(this.getX() + 15 + offsetX, this.getY() + offsetY - 3, textWidth + 20, 16, 6, color1, color2);
			
			nvg.drawText(c.getName(), this.getX() + 15 + offsetX + ((textWidth + 20) - textWidth) / 2, this.getY() + offsetY + 1.5F, textColor, 9, Fonts.MEDIUM);
			
			if(!openSetting) {
				MorphCursor.getInstance().hover(this.getX() + 15 + offsetX, this.getY() + offsetY - 3 + scrollValue, textWidth + 20, 16, 6, c.getName());
			}
			
			offsetX+=textWidth + 28;
		}
		
		// A pinned-only filter sits at the right end of the category row, out of the chip flow.
		float pinnedX = this.getX() + this.getWidth() - 27;
		float pinnedY = this.getY() + offsetY - 3;
		float pinnedWidth = 16;
		float pinnedHeight = 16;
		
		pinnedAnimation.setAnimation(pinnedOnly ? 1.0F : 0.0F, 16);
		
		nvg.drawRoundedRect(pinnedX, pinnedY, pinnedWidth, pinnedHeight, 6, palette.getBackgroundColor(ColorType.DARK));
		nvg.drawGradientRoundedRect(pinnedX, pinnedY, pinnedWidth, pinnedHeight, 6,
				ColorUtils.applyAlpha(accentColor.getColor1(), (int) (pinnedAnimation.getValue() * 255)),
				ColorUtils.applyAlpha(accentColor.getColor2(), (int) (pinnedAnimation.getValue() * 255)));
		nvg.drawCenteredText(pinnedOnly ? LegacyIcon.STAR_FILL : LegacyIcon.STAR, pinnedX + (pinnedWidth / 2.0F), pinnedY + 4,
				pinnedOnly ? Color.WHITE : palette.getFontColor(ColorType.NORMAL), 9, Fonts.LEGACYICON);
		if(!openSetting) {
			MorphCursor.getInstance().hover(pinnedX, pinnedY + scrollValue, pinnedWidth, pinnedHeight, 6, "Show only pinned modules");
		}
		
		offsetY = offsetY + 23;
		
		rebuildOrderedMods();
		
		long elapsed = System.currentTimeMillis() - categoryOpenedAt;
		int rowCount = orderedMods.size();
		
		emptyStateVisible = false;
		
		for(int i = 0; i < rowCount; i++) {
			
			Mod m = orderedMods.get(i);
			
			offsetY = 36 + (i * ROW_PITCH);
			
			if(offsetY + scrollValue + 45 > 0 && offsetY + scrollValue < this.getHeight()) {
				
				float entrance = clamp01((elapsed - Math.min(i, 12) * 12F) / 180F);
				boolean hovered = MouseUtils.isInside(mouseX, mouseY, this.getX() + 15, this.getY() + offsetY + scrollValue, this.getWidth() - 30, 40);
				boolean pressed = pressedMod != null && pressedMod.equals(m);
				
				m.getHoverAnimation().setAnimation(hovered ? 1.0F : 0.0F, 18);
				
				float hover = m.getHoverAnimation().getValue();
				float rowX = this.getX() + 15;
				float rowWidth = this.getWidth() - 30;
				float right = this.getX() + this.getWidth();
				
				nvg.save();
				nvg.setAlpha(0.2F + (0.8F * entrance));
				nvg.translate(0, (1.0F - entrance) * 10.0F);
				
				if(pressed) {
					nvg.scale(rowX, this.getY() + offsetY, rowWidth, 40, UiTokens.PRESS_SCALE);
				}
				
				nvg.drawRoundedRect(rowX, this.getY() + offsetY, rowWidth, 40, 8, palette.getBackgroundColor(ColorType.DARK));
				
				if(hover > 0.01F) {
					nvg.drawRoundedRect(rowX, this.getY() + offsetY, rowWidth, 40, 8, ColorUtils.applyAlpha(accentColor.getColor1(), (int) (hover * UiTokens.TINT_HOVER)));
				}
				
				if(keyboardMod == m && !hovered) {
					nvg.drawOutlineRoundedRect(rowX, this.getY() + offsetY, rowWidth, 40, 8, 1.2F,
							ColorUtils.applyAlpha(accentColor.getColor1(), 170));
				}
				
				nvg.drawRoundedRect(this.getX() + 21, this.getY() + offsetY + 6, 28, 28, 6, palette.getBackgroundColor(ColorType.NORMAL));
				
				nvg.drawText(m.getName(), this.getX() + 56, this.getY() + offsetY + 15F, palette.getFontColor(ColorType.DARK), 13, Fonts.MEDIUM);
				
				float descriptionX = this.getX() + 56 + (nvg.getTextWidth(m.getName(), 13, Fonts.MEDIUM)) + 5;
				float descriptionWidth = (right - 104) - descriptionX;
				
				// The inline description gets truncated to fit; the row tooltip reveals the full text.
				String fullDescription = (m.getDescription() == null || m.getDescription().isEmpty()) ? null : m.getDescription();
				boolean descriptionCut = fullDescription != null
						&& (descriptionWidth <= 24 || nvg.getTextWidth(fullDescription, 9, Fonts.REGULAR) > descriptionWidth);
				
				if(descriptionWidth > 24) {
					nvg.drawText(nvg.getLimitText(m.getDescription(), 9, Fonts.REGULAR, descriptionWidth), descriptionX, this.getY() + offsetY + 17, palette.getFontColor(ColorType.NORMAL), 9, Fonts.REGULAR);
				}
				
				if(m.isRestricted()) {
					nvg.drawText(LegacyIcon.INFO, right - 100, this.getY() + offsetY + 14, new Color(255, 145, 0), 11, Fonts.LEGACYICON);
				}
				
				boolean pinned = Favourites.isPinned(m);
				
				nvg.drawText(pinned ? LegacyIcon.STAR_FILL : LegacyIcon.STAR, right - 68, this.getY() + offsetY + 13.5F,
						pinned ? accentColor.getInterpolateColor() : palette.getFontColor(ColorType.NORMAL), 10, Fonts.LEGACYICON);

				m.getAnimation().setAnimation(m.isToggled() ? 1.0F : 0.0F, 16);
				
				nvg.save();
				nvg.scale(this.getX() + 21, this.getY() + offsetY + 6, 28, 28, m.getAnimation().getValue());
				
				nvg.drawGradientRoundedRect(this.getX() + 21, this.getY() + offsetY + 6, 28, 28, 6, ColorUtils.applyAlpha(accentColor.getColor1(), (int) (m.getAnimation().getValue() * 255)), ColorUtils.applyAlpha(accentColor.getColor2(), (int) (m.getAnimation().getValue() * 255)));
				
				nvg.restore();
				
				// A category glyph turns the flat toggle square into something scannable.
				nvg.drawCenteredText(getCategoryIcon(m.getCategory()), this.getX() + 35,
						this.getY() + offsetY + 13.5F, m.isToggled() ? Color.WHITE : palette.getFontColor(ColorType.NORMAL), 13, Fonts.LEGACYICON);
				
				if(modManager.getSettingsByMod(m) != null) {
					nvg.drawText(LegacyIcon.SETTINGS, right - 39, this.getY() + offsetY + 13.5F, palette.getFontColor(ColorType.NORMAL), 13, Fonts.LEGACYICON);
				}
				
				nvg.restore();
				
				float localTop = offsetY + scrollValue;
				
				if(!openSetting && rowInteractive(localTop)) {
					MorphCursor.getInstance().hover(rowX, this.getY() + localTop, rowWidth, 40, 8, descriptionCut ? fullDescription : null);
					MorphCursor.getInstance().hover(right - 70, this.getY() + offsetY + 11 + scrollValue, 18, 18, 4, pinned ? "Unpin module" : "Pin module");
					
					if(modManager.getSettingsByMod(m) != null) {
						MorphCursor.getInstance().hover(right - 44, this.getY() + offsetY + 9 + scrollValue, 22, 22, 6, "Module settings");
					}
				}
			}
		}
		
		if(rowCount == 0) {
			drawEmptyState(nvg, palette, this.getY() + LIST_TOP, scrollValue);
		}
		
		nvg.restore();
		
		drawScrollbar(nvg, accentColor);
		
		nvg.drawVerticalGradientRect(getX() + 15,  this.getY(), getWidth() - 30, 12,  palette.getBackgroundColor(ColorType.NORMAL), noColour); //top
		nvg.drawVerticalGradientRect(getX() + 15,  this.getY()+ this.getHeight() - 12, getWidth() - 30, 12, noColour, palette.getBackgroundColor(ColorType.NORMAL)); // bottom
		nvg.restore();


		
		//Draw mod setting scene
		
		nvg.save();
		nvg.translate((float) (settingAnimation.getValue() * 600), 0);
		
		if(currentMod != null) {
			
			int setIndex = 0;
			
			if(MouseUtils.isInside(mouseX, mouseY, this.getX(), this.getY(), this.getWidth(), this.getHeight())) {
				settingScroll.onScroll();
				settingScroll.onAnimation();
			}
			
			offsetY = 15;
			offsetX = 0;
			
			nvg.save();
			
			nvg.drawRoundedRect(this.getX() + 15, this.getY() + offsetY, this.getWidth() - 30, this.getHeight() - 30, 10, palette.getBackgroundColor(ColorType.DARK));
			nvg.drawText(LegacyIcon.CHEVRON_LEFT, this.getX() + 25, this.getY() + offsetY + 8, palette.getFontColor(ColorType.DARK), 13, Fonts.LEGACYICON);
			
			if(settingsInteractive) {
				MorphCursor.getInstance().hover(this.getX() + 22, this.getY() + 20, 18, 18, 6);
			}
			nvg.drawText(currentMod.getName(), this.getX() + 42, this.getY() + offsetY + 9, palette.getFontColor(ColorType.DARK), 13, Fonts.MEDIUM);
			nvg.drawText(LegacyIcon.REFRESH, this.getX() + this.getWidth() - 39, this.getY() + offsetY + 7.5F, palette.getFontColor(ColorType.DARK), 13, Fonts.LEGACYICON);
			
			if(settingsInteractive) {
				MorphCursor.getInstance().hover(this.getX() + this.getWidth() - 41, this.getY() + 21, 16, 16, 6);
			}
			
			offsetY = 44;
			
			nvg.scissor(this.getX() + 15, this.getY() + offsetY, this.getWidth() - 30, this.getHeight() - 59);
			nvg.translate(0, settingScroll.getValue());
			
			if(settingsInteractive) {
				MorphCursor.getInstance().pushClip(this.getX() + 15, this.getY() + offsetY, this.getWidth() - 30, this.getHeight() - 59);
			}
			
			for(ModuleSetting s : comps) {
				
				s.openAnimation.setAnimation(s.openY, 16);
				
				nvg.drawText(s.setting.getName(), this.getX() + offsetX + 26, this.getY() + offsetY + 15F + s.openAnimation.getValue(), palette.getFontColor(ColorType.DARK), 10, Fonts.MEDIUM);
				
				if(s.setting instanceof KeybindSetting) {
					
					String conflict = findKeybindConflict((KeybindSetting) s.setting);
					
					if(conflict != null) {
						
						float nameWidth = nvg.getTextWidth(s.setting.getName(), 10, Fonts.MEDIUM);
						float iconX = this.getX() + offsetX + 30 + nameWidth;
						float iconY = this.getY() + offsetY + 11 + s.openAnimation.getValue();
						
						nvg.drawText(LegacyIcon.INFO, iconX, iconY, new Color(255, 145, 0), 8, Fonts.LEGACYICON);
						if(settingsInteractive) {
							MorphCursor.getInstance().hover(iconX - 2, iconY - 4, 11, 12, 3, "Also bound to " + conflict);
						}
					}
				}
				
				if(s.comp instanceof CompToggleButton) {
					
					CompToggleButton toggleButton = (CompToggleButton) s.comp;
					
					toggleButton.setX(this.getX() + offsetX + 168);
					toggleButton.setY(this.getY() + offsetY + 12 + s.openAnimation.getValue());
					toggleButton.setScale(0.85F);
				}
				
				if(s.comp instanceof CompSlider) {
					
					CompSlider slider = (CompSlider) s.comp;
					
					slider.setX(this.getX() + offsetX + 122);
					slider.setY(this.getY() + offsetY + 17 + s.openAnimation.getValue());
					slider.setWidth(75);
				}
				
				if(s.comp instanceof CompComboBox) {
					
					CompComboBox comboBox = (CompComboBox) s.comp;
					
					comboBox.setX(this.getX() + offsetX + 122);
					comboBox.setY(this.getY() + offsetY + 11 + s.openAnimation.getValue());
				}
				
				if(s.comp instanceof CompKeybind) {
					
					CompKeybind keybind = (CompKeybind) s.comp;
					
					keybind.setX(this.getX() + offsetX + 122);
					keybind.setY(this.getY() + offsetY + 11 + s.openAnimation.getValue());
				}
				
				if(s.comp instanceof CompModTextBox) {
					
					CompModTextBox textBox = (CompModTextBox) s.comp;
					
					textBox.setX(this.getX() + offsetX + 122);
					textBox.setY(this.getY() + offsetY + 11 + s.openAnimation.getValue());
					textBox.setWidth(75);
					textBox.setHeight(16);
				}
				
				if(s.comp instanceof CompColorPicker) {
					
					CompColorPicker picker = (CompColorPicker) s.comp;
					
					picker.setX(this.getX() + offsetX + 98);
					picker.setY(this.getY() + offsetY + 12.5F + s.openAnimation.getValue());
					picker.setScale(0.8F);
				}
				
				if(s.comp instanceof CompImageSelect) {
					
					CompImageSelect imageSelect = (CompImageSelect) s.comp;
					
					imageSelect.setX(this.getX() + offsetX + 181);
					imageSelect.setY(this.getY() + offsetY + 11 + s.openAnimation.getValue());
				}
				
				if(s.comp instanceof CompSoundSelect) {
					
					CompSoundSelect soundSelect = (CompSoundSelect) s.comp;
					
					soundSelect.setX(this.getX() + offsetX + 181);
					soundSelect.setY(this.getY() + offsetY + 11 + s.openAnimation.getValue());
				}
				
				if(settingsInteractive) {
					hoverComp(s.comp);
				}
				
				s.comp.draw(mouseX, (int) (mouseY - settingScroll.getValue()), partialTicks);
				
				offsetX+=194;
				setIndex++;
				
				if(setIndex % 2 == 0) {
					offsetY+=29;
					offsetX = 0;
				}
			}
			
			if(settingsInteractive) {
				MorphCursor.getInstance().popClip();
			}
			
			nvg.restore();
			
			settingScroll.setMaxScroll(this.getModuleSettingHeight());
		}
		
		nvg.restore();
		
		drawContextMenu(nvg, palette, accentColor, mouseX, mouseY);
		
		updateScrollBounds();
	}
	
	private void updateScrollBounds() {
		int visibleCount = orderedMods.size();
		float contentHeight = LIST_TOP + (Math.max(0, visibleCount - 1) * ROW_PITCH) + 40.0F + 8.0F;
		scroll.setMaxScroll(Math.max(0.0F, contentHeight - this.getHeight()));
	}
	
	/**
	 * A row only becomes interactive once a useful part of it is inside the list viewport.
	 * This keeps the cursor from latching onto a row hidden under the header or below the
	 * bottom edge while the list is scrolling.
	 */
	private boolean rowInteractive(float localTop) {
		float visibleTop = Math.max(localTop, 0.0F);
		float visibleBottom = Math.min(localTop + 40.0F, this.getHeight() - 6.0F);
		return visibleBottom - visibleTop >= 18.0F;
	}
	
	@Override
	public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		
		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		ModManager modManager = instance.getModManager();
		
		if(contextMod != null) {
			
			float menuX = contextMenuX();
			float menuY = contextMenuY();
			
			if(MouseUtils.isInside(mouseX, mouseY, menuX, menuY, CTX_WIDTH, CTX_HEIGHT)) {
				
				int item = (int) ((mouseY - (menuY + CTX_PAD)) / CTX_ITEM);
				
				if(item >= 0 && item < CTX_ITEMS) {
					handleContextAction(item);
				}
			} else {
				contextMod = null;
			}
			
			return;
		}
		
		int offsetX = 0;
		float offsetY = 13 + scroll.getValue();
		
		if(!openSetting) {
			for(ModCategory c : ModCategory.values()) {
				
				float textWidth = nvg.getTextWidth(c.getName(), 9, Fonts.MEDIUM);
				
				if(MouseUtils.isInside(mouseX, mouseY, this.getX() + 15 + offsetX, this.getY() + offsetY - 3, textWidth + 20, 16) && mouseButton == 0) {
					currentCategory = c;
					scroll.reset();
				}
				
				offsetX+=textWidth + 28;
			}
			
			if(mouseButton == 0 && MouseUtils.isInside(mouseX, mouseY, this.getX() + this.getWidth() - 27, this.getY() + offsetY - 3, 16, 16)) {
				pinnedOnly = !pinnedOnly;
				scroll.reset();
			}
			
			offsetY = offsetY + 23;
			
			rebuildOrderedMods();
			
			if(emptyStateVisible && mouseButton == 0 && MouseUtils.isInside(mouseX, mouseY, clearSearchX, clearSearchY, clearSearchWidth, clearSearchHeight)) {
				this.getSearchBox().setText("");
				this.getSearchBox().setFocused(false);
				scroll.resetAll();
				return;
			}
			
			for(Mod m : orderedMods) {
				
				if(!rowInteractive(offsetY)) {
					offsetY+=50;
					continue;
				}
				
				if(mouseButton == 1 && MouseUtils.isInside(mouseX, mouseY, this.getX() + 15, this.getY() + offsetY, this.getWidth() - 30, 40)) {
					contextMod = m;
					contextX = mouseX;
					contextY = mouseY;
					return;
				}
				
				if(MouseUtils.isInside(mouseX, mouseY, this.getX(), this.getY(), this.getWidth(), this.getHeight()) && mouseButton == 0) {
					
					if(MouseUtils.isInside(mouseX, mouseY, this.getX() + 15, this.getY() + offsetY, this.getWidth() - 98, 40)) {
						pressedMod = m;
						toggleRow(m);
					}
					
					if(MouseUtils.isInside(mouseX, mouseY, this.getX() + this.getWidth() - 70, this.getY() + offsetY + 11, 18, 18)) {
						Favourites.toggle(m);
					}
					
					if(MouseUtils.isInside(mouseX, mouseY, this.getX() + this.getWidth() - 44, this.getY() + offsetY + 9, 22, 22) && !openSetting) {
						
						ArrayList<Setting> settings = modManager.getSettingsByMod(m);
						int setIndex = 0;
						
						offsetX = 0;
						offsetY = 44;
						
						if(settings != null) {

							comps.clear();
							
							for(Setting s : settings) {
								
								if(s instanceof BooleanSetting) {
									
									BooleanSetting bSetting = (BooleanSetting) s;
									
									CompToggleButton toggleButton = new CompToggleButton(bSetting);
									
									toggleButton.setX(this.getX() + offsetX + 168);
									toggleButton.setY(this.getY() + offsetY + 8);
									toggleButton.setScale(0.85F);
									
									comps.add(new ModuleSetting(s, toggleButton));
								}
								
								if(s instanceof NumberSetting) {
									
									NumberSetting nSetting = (NumberSetting)s;
									
									CompSlider slider = new CompSlider(nSetting);
									
									slider.setX(this.getX() + offsetX + 122);
									slider.setY(this.getY() + offsetY + 13);
									slider.setWidth(75);
									
									comps.add(new ModuleSetting(s, slider));
								}
								
								if(s instanceof ComboSetting) {
									
									ComboSetting cSetting = (ComboSetting) s;
									
									CompComboBox comboBox = new CompComboBox(75, cSetting);
									
									comboBox.setX(this.getX() + offsetX + 122);
									comboBox.setY(this.getY() + offsetY + 11);
									
									comps.add(new ModuleSetting(s, comboBox));
								}
								
								if(s instanceof ImageSetting) {
									
									ImageSetting iSetting = (ImageSetting) s;
									CompImageSelect imageSelect = new CompImageSelect(iSetting);
									
									imageSelect.setX(this.getX() + offsetX + 181);
									imageSelect.setY(this.getY() + offsetY + 11);
									
									comps.add(new ModuleSetting(s, imageSelect));
								}
								
								if(s instanceof SoundSetting) {
									
									SoundSetting sSetting = (SoundSetting) s;
									CompSoundSelect soundSelect = new CompSoundSelect(sSetting);
									
									soundSelect.setX(this.getX() + offsetX + 181);
									soundSelect.setY(this.getY() + offsetY + 11);
									
									comps.add(new ModuleSetting(s, soundSelect));
								}
								
								if(s instanceof KeybindSetting) {
									
									KeybindSetting kSetting = (KeybindSetting) s;
									CompKeybind keybind = new CompKeybind(75, kSetting);
									
									keybind.setX(this.getX() + offsetX + 122);
									keybind.setY(this.getY() + offsetY + 7);
									
									comps.add(new ModuleSetting(s, keybind));
								}
								
								if(s instanceof TextSetting) {
									
									TextSetting tSetting = (TextSetting) s;
									
									CompModTextBox textBox = new CompModTextBox(tSetting);
									
									textBox.setX(this.getX() + offsetX + 122);
									textBox.setY(this.getY() + offsetY + 7);
									textBox.setWidth(75);
									textBox.setHeight(16);
									
									comps.add(new ModuleSetting(s, textBox));
								}
								
								if(s instanceof ColorSetting) {
									
									ColorSetting cSetting = (ColorSetting) s;
									CompColorPicker picker = new CompColorPicker(cSetting);
									
									picker.setX(this.getX() + offsetX + 98);
									picker.setY(this.getY() + offsetY + 8.5F);
									picker.setScale(0.8F);
									
									comps.add(new ModuleSetting(s, picker));
								}
								
								offsetX+=194;
								setIndex++;
								
								if(setIndex % 2 == 0) {
									offsetY+=29;
									offsetX = 0;
								}
							}
							
							settingScroll.resetAll();
							currentMod = m;
							openSetting = true;
							this.setCanClose(false);
							
							// The settings layout reuses offsetY; stop walking the list so the
							// same click cannot also toggle whatever row follows in the loop.
							return;
						}
					}
				}
				
				offsetY+=50;
			}
		}
		
		if(openSetting && settingAnimation.isDone(Direction.BACKWARDS)) {
			if(MouseUtils.isInside(mouseX, mouseY, this.getX() + 22, this.getY() + 20,  18,18)  && mouseButton == 0) {
				openSetting = false;
			}
			int x = getX() - 32, y = getY() - 31, width = getWidth() + 32, height = getHeight() + 31;
			if(!MouseUtils.isInside(mouseX, mouseY,  x - 5, y - 5, width + 10, height + 10) && mouseButton == 0 ) {
				openSetting = false;
			}
			
			for(ModuleSetting s: comps) {
				
				if(MouseUtils.isInside(mouseX, mouseY, this.getX(), this.getY(), this.getWidth(), this.getHeight()) && mouseButton == 0) {
					
					s.comp.mouseClicked(mouseX, (int) (mouseY - settingScroll.getValue()), mouseButton);
					
					if(s.comp instanceof CompColorPicker) {
						
						CompColorPicker picker = (CompColorPicker) s.comp;
						int openIndex = 1;
						
						if(!picker.isInsideOpen(mouseX, (int) (mouseY - settingScroll.getValue()))) {
							continue;
						}
						
						for(int i = 0; i < comps.size(); i++) {
							
							if((openIndex * 2) + (comps.indexOf(s)) < comps.size()) {
								
								ModuleSetting s2 = comps.get((openIndex * 2) + (comps.indexOf(s)));
								int add = picker.isShowAlpha() ? 100 : 85;
								
								s2.openY+= picker.isOpen() ? add : -add;
							}
							
							openIndex++;
						}
					}
				}
			}
			
			if(MouseUtils.isInside(mouseX, mouseY, this.getX() + this.getWidth() - 41, this.getY() + 15 + 6F, 16, 16) && mouseButton == 0) {
				
				for(ModuleSetting s : comps) {
					s.setting.reset();
				}
			}
		}

		if(openSetting && mouseButton == 3) {
			openSetting = false;
		}
	}
	
	@Override
	public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
		
		pressedMod = null;
		
		for(ModuleSetting s : comps) {
			
			if(MouseUtils.isInside(mouseX, mouseY, this.getX(), this.getY(), this.getWidth(), this.getHeight()) && mouseButton == 0) {
				s.comp.mouseReleased(mouseX, mouseY, mouseButton);
			}
		}
	}
	
	@Override
	public void keyTyped(char typedChar, int keyCode) {
		
		boolean binding = false;
		
		for(ModuleSetting s : comps) {
			
			if(s.comp instanceof CompKeybind) {
				
				CompKeybind keybind = (CompKeybind) s.comp;
				
				if(keybind.isBinding()) {
					binding = true;
				}
			}
			
			s.comp.keyTyped(typedChar, keyCode);
		}
		
		if(binding) {
			return;
		}

		if(openSetting && keyCode == Keyboard.KEY_ESCAPE) {
			openSetting = false;
		}
		if(!openSetting) {
			
			if(keyCode == Keyboard.KEY_DOWN || keyCode == Keyboard.KEY_UP) {
				moveKeyboardSelection(keyCode == Keyboard.KEY_DOWN ? 1 : -1);
				return;
			}
			
			if(keyCode == Keyboard.KEY_RETURN && keyboardMod != null && orderedMods.contains(keyboardMod)) {
				
				boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
				
				if(shift) {
					openSettings(keyboardMod);
				} else {
					toggleRow(keyboardMod);
				}
				
				return;
			}
			
			scroll.onKey(keyCode);
			if(keyCode != 0xD0 && keyCode != 0xC8 && keyCode != Keyboard.KEY_ESCAPE) this.getSearchBox().setFocused(true);
		}
	}
	
	private void moveKeyboardSelection(int direction) {
		
		if(orderedMods.isEmpty()) {
			keyboardMod = null;
			return;
		}
		
		int index = keyboardMod == null ? -1 : orderedMods.indexOf(keyboardMod);
		int next;
		
		if(index < 0) {
			next = direction > 0 ? 0 : orderedMods.size() - 1;
		} else {
			next = Math.max(0, Math.min(orderedMods.size() - 1, index + direction));
		}
		
		keyboardMod = orderedMods.get(next);
		ensureVisible(next);
	}
	
	private void ensureVisible(int index) {
		
		float offset = scroll.getValue();
		float rowTop = LIST_TOP + (index * ROW_PITCH);
		float visibleTop = LIST_TOP - offset;
		float visibleBottom = this.getHeight() - offset - 4.0F;
		
		if(rowTop < visibleTop) {
			scroll.manualScroll(Math.round(visibleTop - rowTop));
		} else if(rowTop + ROW_PITCH > visibleBottom) {
			scroll.manualScroll(Math.round(visibleBottom - (rowTop + ROW_PITCH)));
		}
	}
	
	/**
	 * Announces the bounds of a settings component to the cursor overlay. Components are laid out
	 * inside a translated NanoVG context, so the scroll offset has to be added back here.
	 */
	private void hoverComp(Comp comp) {
		
		MorphCursor cursor = MorphCursor.getInstance();
		
		float x = comp.getX();
		float y = comp.getY() + settingScroll.getValue();
		
		if(comp instanceof CompToggleButton) {
			
			float scale = ((CompToggleButton) comp).getScale();
			cursor.hover(x, y, 34 * scale, 16 * scale, 7 * scale);
		} else if(comp instanceof CompSlider) {
			cursor.hover(x - 4, y - 4, (float) ((CompSlider) comp).getWidth() + 8, 12, 4);
		} else if(comp instanceof CompColorPicker) {
			
			float scale = ((CompColorPicker) comp).getScale();
			float size = 16 * scale;
			
			cursor.hover(x + (106 * scale), y, size, size, 4);
		} else if(comp instanceof CompComboBox || comp instanceof CompKeybind || comp instanceof CompModTextBox) {
			cursor.hover(x, y, 75, 16, 4);
		} else if(comp instanceof CompImageSelect || comp instanceof CompSoundSelect) {
			cursor.hover(x, y, 16, 16, 4);
		}
	}
	
	/**
	 * @return a description of the first other setting using the same key, or null
	 */
	private String findKeybindConflict(KeybindSetting setting) {
		
		int keyCode = setting.getKeyCode();
		
		if(keyCode == 0) {
			return null;
		}
		
		ModManager modManager = Glide.getInstance().getModManager();
		
		for(Mod mod : modManager.getMods()) {
			
			ArrayList<Setting> list = modManager.getSettingsByMod(mod);
			
			if(list == null) {
				continue;
			}
			
			for(Setting other : list) {
				
				if(other == setting || !(other instanceof KeybindSetting)) {
					continue;
				}
				
				if(((KeybindSetting) other).getKeyCode() == keyCode) {
					return mod.getName() + " / " + other.getName();
				}
			}
		}
		
		return null;
	}
	
	/**
	 * Opens the settings panel for a module, as if its gear icon had been clicked. Reusing the
	 * click path keeps the palette, the list and the panel from drifting apart.
	 */
	public void openSettings(Mod mod) {
		
		if(mod == null) {
			return;
		}
		
		openSetting = false;
		currentCategory = ModCategory.ALL;
		this.getSearchBox().setText("");
		this.getSearchBox().setFocused(false);
		this.scroll.resetAll();
		rebuildOrderedMods();
		updateScrollBounds();
		int index = orderedMods.indexOf(mod);
		
		if(index < 0) {
			return;
		}
		
		// Bring the row into view before synthesising the gear click. Without this, opening a
		// module from the command palette silently failed whenever the row was below the fold.
		float rowTop = LIST_TOP + (index * ROW_PITCH);
		float targetScroll = Math.max(-scroll.getMaxScroll(), Math.min(0.0F, -(rowTop - LIST_TOP)));
		scroll.setScrollPosition(targetScroll);
		
		int clickY = (int) (this.getY() + rowTop + targetScroll + 20.0F);
		mouseClicked(this.getX() + this.getWidth() - 33, clickY, 0);
	}
	
	private float clamp01(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
	}
	
	private void drawScrollbar(NanoVGManager nvg, AccentColor accentColor) {
		
		float max = scroll.getMaxScroll();
		
		scrollbarAnimation.setAnimation(max > 0.0F ? 1.0F : 0.0F, 18);
		
		float alpha = scrollbarAnimation.getValue();
		
		if(alpha < 0.02F) {
			return;
		}
		
		float trackX = this.getX() + this.getWidth() - 9.0F;
		float trackY = this.getY() + LIST_TOP;
		float trackHeight = this.getHeight() - LIST_TOP - 6.0F;
		float content = trackHeight + max;
		float thumbHeight = Math.max(24.0F, trackHeight * (trackHeight / content));
		float progress = Math.max(0.0F, Math.min(1.0F, -scroll.getValue() / max));
		float thumbY = trackY + (progress * (trackHeight - thumbHeight));
		
		nvg.drawRoundedRect(trackX, trackY, 2.5F, trackHeight, 1.25F, new Color(255, 255, 255, (int) (18 * alpha)));
		nvg.drawGradientRoundedRect(trackX, thumbY, 2.5F, thumbHeight, 1.25F,
				ColorUtils.applyAlpha(accentColor.getColor1(), (int) (170 * alpha)),
				ColorUtils.applyAlpha(accentColor.getColor2(), (int) (170 * alpha)));
	}
	
	private String getCategoryIcon(ModCategory category) {
		
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
	
	private float contextMenuX() {
		return Math.max(this.getX() + 8, Math.min(contextX, this.getX() + this.getWidth() - CTX_WIDTH - 8));
	}
	
	private float contextMenuY() {
		return Math.max(this.getY() + 8, Math.min(contextY, this.getY() + this.getHeight() - CTX_HEIGHT - 8));
	}
	
	private void drawContextMenu(NanoVGManager nvg, ColorPalette palette, AccentColor accentColor, int mouseX, int mouseY) {
		
		if(contextMod == null) {
			return;
		}
		
		float menuX = contextMenuX();
		float menuY = contextMenuY();
		
		nvg.drawSoftShadow(menuX, menuY, CTX_WIDTH, CTX_HEIGHT, 8, 150);
		nvg.drawRoundedRect(menuX, menuY, CTX_WIDTH, CTX_HEIGHT, 8, palette.getBackgroundColor(ColorType.DARK));
		
		String[] labels = new String[] {
				contextMod.isToggled() ? "Disable" : "Enable",
				"Settings",
				Favourites.isPinned(contextMod) ? "Unpin" : "Pin",
				"Reset settings",
				"Copy name"
		};
		
		for(int i = 0; i < CTX_ITEMS; i++) {
			
			float itemY = menuY + CTX_PAD + (i * CTX_ITEM);
			boolean hovered = MouseUtils.isInside(mouseX, mouseY, menuX + 3, itemY, CTX_WIDTH - 6, CTX_ITEM);
			
			if(hovered) {
				nvg.drawRoundedRect(menuX + 3, itemY, CTX_WIDTH - 6, CTX_ITEM, 5,
						ColorUtils.applyAlpha(accentColor.getColor1(), 60));
			}
			
			boolean enabled = i != 1 || Glide.getInstance().getModManager().getSettingsByMod(contextMod) != null;
			Color textColor = !enabled ? ColorUtils.applyAlpha(palette.getFontColor(ColorType.NORMAL), 110)
					: (hovered ? Color.WHITE : palette.getFontColor(ColorType.DARK));
			
			nvg.drawText(labels[i], menuX + 12, itemY + 6, textColor, 8.5F, Fonts.MEDIUM);
			MorphCursor.getInstance().hover(menuX + 3, itemY, CTX_WIDTH - 6, CTX_ITEM, 5);
		}
	}
	
	private void handleContextAction(int item) {
		
		if(contextMod == null) {
			return;
		}
		
		Mod target = contextMod;
		
		switch(item) {
		case 0:
			target.toggle();
			Recents.push(target);
			break;
		case 1:
			contextMod = null;
			openSettings(target);
			return;
		case 2:
			Favourites.toggle(target);
			break;
		case 3:
			ArrayList<Setting> settings = Glide.getInstance().getModManager().getSettingsByMod(target);
			if(settings != null) {
				for(Setting setting : settings) {
					setting.reset();
				}
			}
			break;
		case 4:
			net.minecraft.client.gui.GuiScreen.setClipboardString(target.getName());
			break;
		}
		
		contextMod = null;
	}
	
	/**
	 * One ordered list feeds both rendering and hit testing, so a row can never be drawn at one
	 * position and clicked at another.
	 */
	private void rebuildOrderedMods() {
		
		ModManager modManager = Glide.getInstance().getModManager();
		String query = this.getSearchBox().getText().trim();
		
		orderedMods.clear();
		
		for(Mod mod : modManager.getMods()) {
			
			if(filterMod(mod)) {
				continue;
			}
			
			if(pinnedOnly && !Favourites.isPinned(mod)) {
				continue;
			}
			
			if(!query.isEmpty() && scoreMod(modManager, mod, query) < 0) {
				continue;
			}
			
			orderedMods.add(mod);
		}
		
		final String sortQuery = query;
		final ModManager sortManager = modManager;
		
		orderedMods.sort((a, b) -> {
			
			boolean pinnedA = Favourites.isPinned(a);
			boolean pinnedB = Favourites.isPinned(b);
			
			if(pinnedA != pinnedB) {
				return pinnedA ? -1 : 1;
			}
			
			if(!sortQuery.isEmpty()) {
				return Integer.compare(scoreMod(sortManager, b, sortQuery), scoreMod(sortManager, a, sortQuery));
			}
			
			boolean recentA = Recents.contains(a);
			boolean recentB = Recents.contains(b);
			
			if(recentA != recentB) {
				return recentA ? -1 : 1;
			}
			
			return 0;
		});
	}
	
	private int scoreMod(ModManager modManager, Mod mod, String query) {
		
		int nameScore = SearchUtils.score(mod.getName(), query);
		
		if(nameScore >= 0) {
			return nameScore + 40;
		}
		
		return SearchUtils.score(mod.getDescription() + " " + modManager.getWords(mod), query);
	}
	
	/**
	 * Shift-click toggles the whole range since the last clicked row.
	 */
	private void toggleRow(Mod mod) {
		
		boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
		
		if(shift && lastClickedMod != null) {
			
			int from = orderedMods.indexOf(lastClickedMod);
			int to = orderedMods.indexOf(mod);
			
			if(from >= 0 && to >= 0) {
				
				for(int i = Math.min(from, to); i <= Math.max(from, to); i++) {
					
					Mod target = orderedMods.get(i);
					target.setToggled(!target.isToggled());
				}
			}
		} else {
			mod.toggle();
		}
		
		Recents.push(mod);
		lastClickedMod = mod;
	}
	
	private void drawEmptyState(NanoVGManager nvg, ColorPalette palette, float drawY, float scrollValue) {
		
		String query = this.getSearchBox().getText().trim();
		
		String message = query.isEmpty()
				? "No modules in this category"
				: "Nothing matches " + nvg.getLimitText(query, 9, Fonts.MEDIUM, 180);
		
		nvg.drawCenteredText(message, this.getX() + (this.getWidth() / 2.0F), drawY + 40, palette.getFontColor(ColorType.NORMAL), 9, Fonts.MEDIUM);
		
		if(query.isEmpty()) {
			return;
		}
		
		float width = 86;
		float height = 18;
		float x = this.getX() + ((this.getWidth() - width) / 2.0F);
		float y = drawY + 58;
		
		nvg.drawRoundedRect(x, y, width, height, 6, palette.getBackgroundColor(ColorType.DARK));
		nvg.drawCenteredText("Clear search", x + (width / 2.0F), y + 5, palette.getFontColor(ColorType.DARK), 8, Fonts.MEDIUM);
		
		clearSearchX = x;
		clearSearchY = y + scrollValue;
		clearSearchWidth = width;
		clearSearchHeight = height;
		emptyStateVisible = true;
		
		if(!openSetting) {
			MorphCursor.getInstance().hover(x, y + scrollValue, width, height, 6, "Clear the search");
		}
	}
	
	private boolean filterMod(Mod m) {
		
		if(m.isHide()) {
			return true;
		}

		if (!m.getAllowed()) {
			return true;
		}
		
		if(!currentCategory.equals(ModCategory.ALL) && !m.getCategory().equals(currentCategory)) {
			return true;
		}
		
		return false;
	}
	
	private int getModuleSettingHeight() {
		
	    int oddOutput = 0;
	    int evenOutput = 0;
	    int oddTotal = 0;
	    int evenTotal = 0;

	    for (int i = 0; i < comps.size(); i++) {
	    	
	        if (MathUtils.isOdd(i + 1)) {
	            oddOutput += 29;
	        } else {
	            evenOutput += 29;
	        }

	        ModuleSetting s = comps.get(i);
	        if (s.comp instanceof CompColorPicker) {
	            CompColorPicker picker = (CompColorPicker) s.comp;
	            if (picker.isOpen()) {
	                int add = picker.isShowAlpha() ? 100 : 85;
	                if (MathUtils.isOdd(i + 1)) {
	                    oddTotal += add;
	                } else {
	                    evenTotal += add;
	                }
	            }
	        }
	    }

	    int output = Math.max(oddOutput, evenOutput) + Math.max(oddTotal, evenTotal);
	    
	    return Math.max(0, output - (this.getHeight() - 72));
	}
	
	private class ModuleSetting {
		
		private SimpleAnimation openAnimation = new SimpleAnimation();
		
		private Setting setting;
		private Comp comp;
		private float openY;
		
		public ModuleSetting(Setting setting, Comp comp) {
			this.setting = setting;
			this.comp = comp;
			this.openY = 0;
		}
	}
}
