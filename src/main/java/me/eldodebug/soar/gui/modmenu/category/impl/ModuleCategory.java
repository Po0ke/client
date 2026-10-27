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
	
	private Mod pressedMod;
	private Mod lastClickedMod;
	private long categoryOpenedAt;
	
	private boolean emptyStateVisible;
	private float clearSearchX, clearSearchY, clearSearchWidth, clearSearchHeight;
	
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
			
			MorphCursor.getInstance().hover(this.getX() + 15 + offsetX, this.getY() + offsetY - 3 + scrollValue, textWidth + 20, 16, 6);
			
			offsetX+=textWidth + 28;
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
				
				if(modManager.getSettingsByMod(m) != null) {
					nvg.drawText(LegacyIcon.SETTINGS, right - 39, this.getY() + offsetY + 13.5F, palette.getFontColor(ColorType.NORMAL), 13, Fonts.LEGACYICON);
				}
				
				nvg.restore();
				
				MorphCursor.getInstance().hover(rowX, this.getY() + offsetY + scrollValue, rowWidth, 40, 8, descriptionCut ? fullDescription : null);
				MorphCursor.getInstance().hover(right - 70, this.getY() + offsetY + 11 + scrollValue, 18, 18, 4, pinned ? "Unpin module" : "Pin module");
				
				if(modManager.getSettingsByMod(m) != null) {
					MorphCursor.getInstance().hover(right - 44, this.getY() + offsetY + 9 + scrollValue, 22, 22, 6, "Module settings");
				}
			}
		}
		
		if(rowCount == 0) {
			drawEmptyState(nvg, palette, this.getY() + LIST_TOP, scrollValue);
		}
		
		nvg.restore();
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
						MorphCursor.getInstance().hover(iconX - 2, iconY - 4, 11, 12, 3, "Also bound to " + conflict);
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
			
			nvg.restore();
			
			settingScroll.setMaxScroll(this.getModuleSettingHeight());
		}
		
		nvg.restore();
		
		int visibleCount = orderedMods.size();
		scroll.setMaxScroll((visibleCount - (visibleCount > 5 ? 5.18F : visibleCount)) * 50);
	}
	
	@Override
	public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		
		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		ModManager modManager = instance.getModManager();
		
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
			
			offsetY = offsetY + 23;
			
			rebuildOrderedMods();
			
			if(emptyStateVisible && mouseButton == 0 && MouseUtils.isInside(mouseX, mouseY, clearSearchX, clearSearchY, clearSearchWidth, clearSearchHeight)) {
				this.getSearchBox().setText("");
				this.getSearchBox().setFocused(false);
				scroll.resetAll();
				return;
			}
			
			for(Mod m : orderedMods) {
				
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
			scroll.onKey(keyCode);
			if(keyCode != 0xD0 && keyCode != 0xC8 && keyCode != Keyboard.KEY_ESCAPE) this.getSearchBox().setFocused(true);
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
		
		int index = orderedMods.indexOf(mod);
		
		if(index < 0) {
			return;
		}
		
		mouseClicked(this.getX() + this.getWidth() - 33, (int) (this.getY() + LIST_TOP + (index * ROW_PITCH) + 20), 0);
	}
	
	private float clamp01(float value) {
		return Math.max(0.0F, Math.min(1.0F, value));
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
		
		MorphCursor.getInstance().hover(x, y + scrollValue, width, height, 6, "Clear the search");
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
