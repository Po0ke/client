package me.eldodebug.soar.gui.modmenu;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

import eu.shoroa.contrib.render.ShBlur;
import me.eldodebug.soar.gui.modmenu.category.impl.*;
import me.eldodebug.soar.management.file.FileManager;
import me.eldodebug.soar.management.language.TranslateText;
import me.eldodebug.soar.management.misc.ClickTripler;
import me.eldodebug.soar.management.mods.impl.InternalSettingsMod;
import me.eldodebug.soar.management.nanovg.font.FontManager;
import me.eldodebug.soar.management.nanovg.font.Icons;
import me.eldodebug.soar.utils.file.FileUtils;
import org.lwjgl.input.Keyboard;

import me.eldodebug.soar.Glide;
import me.eldodebug.soar.gui.GuiEditHUD;
import me.eldodebug.soar.gui.modmenu.category.Category;
import me.eldodebug.soar.management.color.AccentColor;
import me.eldodebug.soar.management.color.ColorManager;
import me.eldodebug.soar.management.color.palette.ColorPalette;
import me.eldodebug.soar.management.color.palette.ColorType;
import me.eldodebug.soar.management.event.impl.EventRenderNotification;
import me.eldodebug.soar.management.nanovg.NanoVGManager;
import me.eldodebug.soar.management.nanovg.font.Fonts;
import me.eldodebug.soar.management.nanovg.font.LegacyIcon;
import me.eldodebug.soar.ui.CommandPalette;
import me.eldodebug.soar.ui.NotificationCenter;
import me.eldodebug.soar.ui.ambient.AmbientBackdrop;
import me.eldodebug.soar.ui.comp.impl.field.CompSearchBox;
import me.eldodebug.soar.utils.MathUtils;
import me.eldodebug.soar.utils.animation.simple.SimpleAnimation;
import me.eldodebug.soar.utils.animation.spring.Spring;
import me.eldodebug.soar.utils.buffer.ScreenAnimation;
import me.eldodebug.soar.utils.mouse.MorphCursor;
import me.eldodebug.soar.utils.mouse.MouseUtils;
import me.eldodebug.soar.utils.mouse.Scroll;
import me.eldodebug.soar.utils.render.BlurUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

public class GuiModMenu extends GuiScreen {

	private final Spring introSpring = new Spring(0.0D, 24.0D, 1.0D);
	private boolean closing;
	private long introLastFrameNanos;
	private int x, y, width, height;
	
	private ArrayList<Category> categories = new ArrayList<Category>();
	private Category currentCategory;
	
	private SimpleAnimation moveAnimation = new SimpleAnimation();
	
	private ScreenAnimation screenAnimation = new ScreenAnimation();
	
	private Scroll scroll = new Scroll();
	
	private boolean toEditHUD, canClose;
	
	private int categoryTransitionDirection = 1;
	
	private CompSearchBox searchBox = new CompSearchBox();
	
	private final AmbientBackdrop ambient = new AmbientBackdrop();
	
	private CommandPalette commandPalette = new CommandPalette();
	
	private NotificationCenter notificationCenter = new NotificationCenter();
	
	public GuiModMenu() {
		
		categories.add(new HomeCategory(this));
		categories.add(new ModuleCategory(this));
		categories.add(new CosmeticsCategory(this));
		categories.add(new GamesCategory(this));
		categories.add(new ProfileCategory(this));
		categories.add(new ScreenshotCategory(this));
		categories.add(new SettingCategory(this));
		
		currentCategory = getCategoryByClass(HomeCategory.class);
	}
	
	@Override
	public void initGui() {
		
		ScaledResolution sr = new ScaledResolution(mc);
		
		int addX = 225;
		int addY = 140;
		
		x = (sr.getScaledWidth() / 2) - addX;
		y = (sr.getScaledHeight() / 2) - addY;
		width = addX * 2;
		height = addY * 2;
		
		introSpring.setValue(0.0D);
		closing = false;
		introLastFrameNanos = 0L;
		ambient.reset();
		
		for(Category c : categories) {
			c.initGui();
		}
		
		scroll.resetAll();
		toEditHUD = false;
		canClose = true;
		
		commandPalette.close();
		notificationCenter.close();
		
		ArrayList<CommandPalette.Action> paletteActions = new ArrayList<CommandPalette.Action>();
		
		paletteActions.add(new CommandPalette.Action("Edit HUD", "Open the HUD editor", this::openHudEditor));
		paletteActions.add(new CommandPalette.Action("Notification History", "Open the notification centre",
				() -> notificationCenter.open(x, y, width, height)));
		paletteActions.add(new CommandPalette.Action("Open Profiles", "Switch to the profile scene",
				() -> openCategory(ProfileCategory.class)));
		paletteActions.add(new CommandPalette.Action("Open Cosmetics", "Switch to the cosmetics scene",
				() -> openCategory(CosmeticsCategory.class)));
		paletteActions.add(new CommandPalette.Action("Open Screenshots", "Switch to the screenshot scene",
				() -> openCategory(ScreenshotCategory.class)));
		paletteActions.add(new CommandPalette.Action("Open Settings", "Switch to the settings scene",
				() -> openCategory(SettingCategory.class)));
		paletteActions.add(new CommandPalette.Action("Open Modules", "Switch to the module list",
				() -> openCategory(ModuleCategory.class)));
		paletteActions.add(new CommandPalette.Action("Toggle UI Blur", "Background blur on or off",
				() -> InternalSettingsMod.getInstance().getBlurSetting().setToggled(!InternalSettingsMod.getInstance().getBlurSetting().isToggled())));
		
		commandPalette.setActions(paletteActions);
		commandPalette.setSettingsListener(mod -> {
			
			ModuleCategory moduleCategory = (ModuleCategory) getCategoryByClass(ModuleCategory.class);
			
			if(moduleCategory != null) {
				moduleCategory.openSettings(mod);
			}
		});
		
		MorphCursor.getInstance().setEnabled(InternalSettingsMod.getInstance().getMorphCursorSetting().isToggled());
		MorphCursor.getInstance().refreshNativeCursor();
	}
	
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		
		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		
		boolean morphCursorEnabled = InternalSettingsMod.getInstance().getMorphCursorSetting().isToggled();
		MorphCursor morphCursor = MorphCursor.getInstance();
		
		long introNow = System.nanoTime();
		float introDelta = introLastFrameNanos == 0L ? 0.0F : (introNow - introLastFrameNanos) / 1_000_000_000F;
		introLastFrameNanos = introNow;
		introSpring.update(closing ? 0.0D : 1.0D, introDelta);
		float intro = Math.max(0.0F, Math.min(introSpring.get(), 1.0F));
		
		if(morphCursor.isEnabled() != morphCursorEnabled) {
			morphCursor.setEnabled(morphCursorEnabled);
		}
		
		if(morphCursorEnabled) {
			morphCursor.beginFrame(mouseX, mouseY);
			morphCursor.setGlobalAlpha(intro);
		}
		
		if(InternalSettingsMod.getInstance().getBlurSetting().isToggled()) BlurUtils.drawBlurScreen(intro * 20 + 1F);
		screenAnimation.wrap(() -> {
			ambient.draw(nvg, mouseX, mouseY);
			nvg.drawShadow(x, y, width, height, 12);
		}, 0.94F + 0.06F * intro, intro);
		
		screenAnimation.wrap(() -> drawNanoVG(mouseX, mouseY, partialTicks), this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0.94F + 0.06F * intro, intro, true);
		
		new EventRenderNotification().call();
		
		super.drawScreen(mouseX, mouseY, partialTicks);
		
		if(morphCursorEnabled) {
			morphCursor.draw();
		}
	}
	
	private void drawNanoVG(int mouseX, int mouseY, float partialTicks) {
		
		Glide instance = Glide.getInstance();
		NanoVGManager nvg = instance.getNanoVGManager();
		ColorManager colorManager = instance.getColorManager();
		ColorPalette palette = colorManager.getPalette();
		AccentColor currentColor = colorManager.getCurrentColor();

		if(closing && introSpring.get() <= 0.005F) {
			mc.displayGuiScreen(toEditHUD ? new GuiEditHUD(true) : null);
		}
		
		// Keeps the external helper in step even when the toggle is flipped from the palette.
		ClickTripler.sync(InternalSettingsMod.getInstance().getClickTriplerSetting().isToggled(),
				InternalSettingsMod.getInstance().getClickTriplerMinDelay().getValueInt(),
				InternalSettingsMod.getInstance().getClickTriplerMaxDelay().getValueInt());
		nvg.drawRoundedRect(x, y, width, height, 12, palette.getBackgroundColor(ColorType.NORMAL));

		if (InternalSettingsMod.getInstance().getBlurSetting().isToggled()) {
			ShBlur.getInstance().drawBlur(() -> nvg.drawRoundedRectVarying(x, y, 32, height, 12, 0, 12, 0, palette.getBackgroundColor(ColorType.DARK)));
			Color colsidebar = palette.getBackgroundColor(ColorType.DARK);
			nvg.drawRoundedRectVarying(x, y, 32, height, 12, 0, 12, 0, new Color(colsidebar.getRed(), colsidebar.getGreen(), colsidebar.getBlue(), 210));
		} else {
			nvg.drawRoundedRectVarying(x, y, 32, height, 12, 0, 12, 0,  palette.getBackgroundColor(ColorType.DARK));
		}

		nvg.drawGradientRoundedRect(x + 5, y + 7, 22, 22, 11, currentColor.getColor1(), currentColor.getColor2());
		nvg.drawText(Icons.GLIDE, x + 8, y + 10, Color.WHITE, 16, Fonts.ICON_FILLED);
		if(currentCategory.isShowTitle()) {
			nvg.save();
			nvg.translate(currentCategory.getTextAnimation().getValue() * 15, 0);
			nvg.drawText(currentCategory.getName(), x + 32, y + 10, palette.getFontColor(ColorType.DARK, (int) (currentCategory.getTextAnimation().getValue() * 255)), 15, Fonts.SEMIBOLD);
			nvg.restore();
		}
		
		int offsetY = 0;
		
		moveAnimation.setAnimation(categories.indexOf(currentCategory) * 30, 18);
		
		nvg.save();

		nvg.drawGradientRoundedRect(x + 5.5F, y + 38.5F + moveAnimation.getValue(), 21, 21, 5, currentColor.getColor1(), currentColor.getColor2());
		
		for(Category c : categories) {
			
			Color textColor = c.getTextColorAnimation().getColor(MathUtils.isInRange(moveAnimation.getValue(), offsetY - 8, offsetY + 8) ? Color.WHITE : palette.getFontColor(ColorType.NORMAL), 18);
			
			c.getTextAnimation().setAnimation(c.equals(currentCategory) ? 1.0F : 0.0F, 14);
			
			nvg.drawText(c.getIcon(), x + 9F, y + 42 + offsetY, textColor, 14, Fonts.LEGACYICON);
			
			MorphCursor.getInstance().hover(x + 5.5F, y + 38.5F + offsetY, 21, 21, 6, c.getName());
			
			offsetY+=30;
		}
		
		nvg.restore();
		
		nvg.drawGradientRoundedRect(x + 5.5F, y + height - 30, 21, 21, 6, currentColor.getColor1(), currentColor.getColor2());
		nvg.drawText(LegacyIcon.LAYOUT, x + 9, y + height - 26.5F, Color.WHITE, 14, Fonts.LEGACYICON);
		
		MorphCursor.getInstance().hover(x + 5.5F, y + height - 30, 21, 21, 6, "Edit HUD");
		
		for(Category c : categories) {
			
			c.getCategoryAnimation().setAnimation(c.equals(currentCategory) ? 1.0F : 0.0F, 16);
			
			if(c.equals(currentCategory)) {
				
				nvg.save();
				
				if(!c.isInitialized()) {
					c.setInitialized(true);
					c.initCategory();
					searchBox.setText("");
					c.setCanClose(true);
				}
				
				int yOff = (currentCategory.isShowTitle()) ? 31 : 0;
				
				nvg.scissor(x + 32, y + yOff, width - 32, height - yOff);
				// Content enters from the side the user navigated towards, not always downwards.
				float entrance = 1.0F - c.getCategoryAnimation().getValue();
				nvg.translate(entrance * 22.0F * categoryTransitionDirection, entrance * 10.0F);
				
				// Hover targets are clipped to the content area, and never to the strip under
				// the search field and notification bell.
				MorphCursor.getInstance().pushClip(x + 32, y + Math.max(yOff, 26), width - 32, height - Math.max(yOff, 26));
				
				c.drawScreen(mouseX, mouseY, partialTicks);
				
				MorphCursor.getInstance().popClip();
				
				nvg.restore();
				
			}else if(c.isInitialized()) {
				c.setInitialized(false);
			}
		}
		
		drawHeader(nvg, palette, currentColor, mouseX, mouseY, partialTicks);
		
		if(!commandPalette.isOpen() && !notificationCenter.isOpen() && MouseUtils.isInside(mouseX, mouseY, x + 32, y + 31, width - 32, height - 31)) {
			scroll.onScroll();
		}
		
		scroll.onAnimation();
		
		if(commandPalette.isOpen()) {
			commandPalette.draw(mouseX, mouseY, partialTicks, x, y, width, height);
		}
		
		if(notificationCenter.isOpen()) {
			notificationCenter.draw(mouseX, mouseY, partialTicks, x, y, width, height);
		}

		if(currentCategory.isShowSearchBox() && Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) && Keyboard.isKeyDown(Keyboard.KEY_F)) {
			currentCategory.getSearchBox().setFocused(true);
		}
	}
	
	/**
	 * The search field, notification bell and folder shortcut are painted after the scene so
	 * no scene content can cover them. Their hover targets are registered after the scene too,
	 * which keeps the cursor from latching onto a row that happens to sit underneath.
	 */
	private void drawHeader(NanoVGManager nvg, ColorPalette palette, AccentColor currentColor,
			int mouseX, int mouseY, float partialTicks) {

		Glide instance = Glide.getInstance();

		if(currentCategory.isShowSearchBox()) {
			searchBox.setPosition(x + width - 175, y + 6.5F, 160, 18);
			searchBox.draw(mouseX, mouseY, partialTicks);
		}

		float bellX = x + width - (currentCategory.isShowSearchBox() ? 199 : 26);
		float bellY = y + 6.5F;
		int unread = instance.getNotificationManager().getUnread();
		boolean bellHovered = MouseUtils.isInside(mouseX, mouseY, bellX, bellY, 18, 18);

		nvg.drawRoundedRect(bellX, bellY, 18, 18, 6,
				bellHovered ? palette.getBackgroundColor(ColorType.NORMAL) : palette.getBackgroundColor(ColorType.DARK));
		nvg.drawCenteredText(LegacyIcon.BELL, bellX + 9.0F, bellY + 4.0F,
				unread > 0 ? currentColor.getInterpolateColor() : palette.getFontColor(ColorType.NORMAL), 10, Fonts.LEGACYICON);

		if(unread > 0) {
			float badge = 8.0F;
			nvg.drawGradientRoundedRect(bellX + 12, bellY - 2, badge, badge, badge / 2.0F, currentColor.getColor1(), currentColor.getColor2());
			nvg.drawCenteredText(unread > 9 ? "9" : String.valueOf(unread), bellX + 12 + (badge / 2.0F), bellY - 1,
					Color.WHITE, 6, Fonts.MEDIUM);
		}

		if(Objects.equals(currentCategory.getNameKey(), TranslateText.COSMETICS.getKey())) {
			float folderButtonX = x + width - 221;
			float folderButtonY = y + 6.5F;
			nvg.drawRoundedRect(folderButtonX, folderButtonY, 18, 18, 6, palette.getBackgroundColor(ColorType.DARK));
			nvg.drawCenteredText(LegacyIcon.FOLDER, folderButtonX + 9.0F, folderButtonY + 4.5F,
					palette.getFontColor(ColorType.NORMAL), 9, Fonts.LEGACYICON);
			MorphCursor.getInstance().hover(folderButtonX, folderButtonY, 18, 18, 6, "Open cape folder");
		}

		if(currentCategory.isShowSearchBox()) {
			MorphCursor.getInstance().hover(searchBox.getX(), searchBox.getY(), searchBox.getWidth(), searchBox.getHeight(), 6);
		}

		MorphCursor.getInstance().hover(bellX, bellY, 18, 18, 6, "Notifications (Ctrl+N)");
	}

	@Override
	public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
		
		MorphCursor.getInstance().onMouseDown();
		
		if(commandPalette.isOpen()) {
			commandPalette.mouseClicked(mouseX, mouseY, mouseButton);
			return;
		}
		
		if(notificationCenter.isOpen()) {
			notificationCenter.mouseClicked(mouseX, mouseY, mouseButton);
			return;
		}
		
		int offsetY = 0;

		// exit gui if not clicked in the gui area
		if(!MouseUtils.isInside(mouseX, mouseY, x - 5, y - 5, width + 10, height + 10) && mouseButton == 0 && canClose) {
			beginClose();
		}

		for(Category c : categories) {
			
			if(MouseUtils.isInside(mouseX, mouseY, x + 5.5F, y + 38.5F + offsetY, 21, 21) && mouseButton == 0) {
				categoryTransitionDirection = categories.indexOf(c) >= categories.indexOf(currentCategory) ? 1 : -1;
				currentCategory = c;
			}
			
			offsetY+=30;
		}
		
		if(MouseUtils.isInside(mouseX, mouseY, x + 5.5F, y + height - 30, 21, 21) && mouseButton == 0) {
			toEditHUD = true;
			beginClose();
		}
		
		if(MouseUtils.isInside(mouseX, mouseY, x + width - (currentCategory.isShowSearchBox() ? 199 : 26), y + 6.5F, 18, 18) && mouseButton == 0) {
			commandPalette.close();
			notificationCenter.open(x, y, width, height);
			return;
		}
		
		currentCategory.mouseClicked(mouseX, mouseY, mouseButton);
		searchBox.mouseClicked(mouseX, mouseY, mouseButton);

		if(Objects.equals(currentCategory.getNameKey(), TranslateText.COSMETICS.getKey())){
			float folderButtonX = x + width - 221;
			float folderButtonY = y + 6.5F;
			if (MouseUtils.isInside(mouseX, mouseY, folderButtonX, folderButtonY, 18, 18)){
				FileUtils.openFolderAtPath(Glide.getInstance().getFileManager().getCustomCapeDir());
			}
		}
		
		try {
			super.mouseClicked(mouseX, mouseY, mouseButton);
		} catch (IOException e) {}
	}
	
	@Override
	public void mouseReleased(int mouseX, int mouseY, int mouseButton) {
		
		MorphCursor.getInstance().onMouseUp();
		
		if(commandPalette.isOpen()) {
			return;
		}
		
		currentCategory.mouseReleased(mouseX, mouseY, mouseButton);
	}
	
	@Override
	public void keyTyped(char typedChar, int keyCode) {
		
		if(commandPalette.isOpen()) {
			commandPalette.keyTyped(typedChar, keyCode);
			return;
		}
		
		if(notificationCenter.isOpen()) {
			notificationCenter.keyTyped(typedChar, keyCode);
			return;
		}
		
		if(keyCode == Keyboard.KEY_N && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
			commandPalette.close();
			notificationCenter.open(x, y, width, height);
			return;
		}
		
		if(keyCode == Keyboard.KEY_K && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL))) {
			notificationCenter.close();
			commandPalette.open(x, y, width, height);
			return;
		}
		
		currentCategory.keyTyped(typedChar, keyCode);
		searchBox.keyTyped(typedChar, keyCode);
		
		if(currentCategory.isShowSearchBox() && canClose) {
			
			if(Keyboard.isKeyDown(Keyboard.KEY_ESCAPE)) {
				
				if(!searchBox.getText().isEmpty()) {
					searchBox.setText("");
					searchBox.setFocused(false);
					return;
				}
				
				if(searchBox.isFocused()) {
					searchBox.setFocused(false);
					return;
				}
			}
		}
		
		if(keyCode == Keyboard.KEY_ESCAPE && canClose) {
			beginClose();
		}
	}
	
	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
	
	@Override
	public void onGuiClosed() {
		commandPalette.close();
		notificationCenter.close();
		MorphCursor.getInstance().onScreenClosed();
		Glide.getInstance().getProfileManager().save();
	}

	public void openHudEditor() {
		toEditHUD = true;
		canClose = false;
		beginClose();
	}
	
	private void openCategory(Class<?> clazz) {
		
		Category category = getCategoryByClass(clazz);
		
		if(category == null || category == currentCategory) {
			return;
		}
		
		categoryTransitionDirection = categories.indexOf(category) >= categories.indexOf(currentCategory) ? 1 : -1;
		currentCategory = category;
	}
	
	private void beginClose() {
		closing = true;
	}
	
	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public ArrayList<Category> getCategories() {
		return categories;
	}
	
	public Category getCategoryByClass(Class<?> clazz) {
		
		for(Category c : categories) {
			if(c.getClass().equals(clazz)) {
				return c;
			}
		}
		
		return null;
	}

	public Scroll getScroll() {
		return scroll;
	}

	public CompSearchBox getSearchBox() {
		return searchBox;
	}

	public boolean isCanClose() {
		return canClose;
	}

	public void setCanClose(boolean canClose) {
		this.canClose = canClose;
	}
}
