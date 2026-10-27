# Glide Client — Modernization Plan

A working plan for pushing Glide's UX further, grounded in what the codebase actually is.
Everything below references real files and describes changes in terms of the existing
architecture, so each item can be picked up and implemented without an archaeology phase.

---

## 1. What we are building on

| Area | Reality in this repo |
|---|---|
| Platform | Minecraft 1.8.9, Forge + Mixin (`mixins.soar.json`), ForgeGradle 2.1-SNAPSHOT, Gradle 4.10.3 |
| Size | 607 Java files, 130 modules in `management/mods/impl` |
| Renderer | NanoVG (LWJGL2 backend) via `NanoVGManager`, immediate-mode, one frame per screen draw |
| Mod menu | `gui/modmenu/GuiModMenu.java` — 450x280 panel, 32px icon rail, pluggable `Category` scenes (Home, Module, Cosmetics, Games, Profile, Screenshot, Setting) |
| Component model | `ui/comp/Comp.java` + subclasses (toggle, slider, combo, color picker, keybind, text fields). A component knows `x/y`, not width/height in the base class |
| Animation | `utils/animation/simple/SimpleAnimation` — frame-based, speed capped at 28, no delta time. Easing curves in `utils/animation/normal/easing` |
| Theming | `ColorManager` → accent gradient (`AccentColor.color1/color2`) + `ColorPalette` surface/font tokens (`ColorType.NORMAL/DARK/...`) |
| Interaction | `MouseUtils.isInside(...)` scattered at click sites; no hover registry, no focus model, no keyboard nav |
| Effects | `ui/ClickEffects` (expanding arc on click), `ui/particle/ParticleEngine`, blur via `eu.shoroa.contrib.render.ShBlur` |
| Tests | None. There is no test source set — this is a real constraint for the phases below |

Two consequences worth stating up front:

1. **The UI is immediate-mode, so "state" has to be explicit.** A morphing cursor cannot
   ask the renderer "what is under the mouse" — elements must announce themselves each
   frame. That is the single design decision the whole interaction layer hangs off.
2. **`SimpleAnimation` is not good enough for "extremely smooth."** It steps by a
   frame-based heuristic and caps speed at 28, so on a 240 Hz display it moves at a
   different physical speed than at 60 Hz. Any "premium feel" work starts by adding a
   delta-time based spring and migrating high-visibility elements to it.

---

## 2. Building it (toolchain reality)

The build is the sharp edge: **Gradle 4.10.3 + ForgeGradle 2.1 cannot run on a modern JDK.**
They need JDK 8. This machine only ships JDK 25, so a project-local JDK 8 was installed.

```bash
export JAVA_HOME="$PWD/.jdk8"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew build
# artifact: build/libs/GlideClient-Release.jar
```

Notes and guardrails:

- `.jdk8/` and `.jdk8dl/` are build tooling, not sources — keep them out of git.
- `./gradlew --no-daemon build` is the safe form; the old Gradle daemon can outlive a
  crashed build and hold file locks.
- First build does a full `deobfMcMCP` pass (a few minutes, ~1 GB of caches). Subsequent
  builds are incremental.
- Do not "upgrade Gradle" casually. ForgeGradle 2.1 is tied to Gradle 4.x. If the build
  ever needs to be modernized, the only realistic path for 1.8.9 is the Essential Loom
  plugin (a legacy-capable fork of Fabric Loom); that is a separate, isolated project, not
  a bump of a version number.
- Optional quality-of-life, no behavior change: add `gradle.properties` with
  `org.gradle.jvmargs=-Xmx2G` to stop ForgeGradle OOMing on fresh checkouts, and pin the
  JDK in an `org.gradle.java.home`-based local file rather than relying on shell exports.

---

## 3. Headline feature: the morphing cursor

### The behaviour

- Right shift opens the mod menu; the native OS arrow is hidden inside the menu.
- A ring follows the pointer with a soft, physical lag (not a fixed-pixel trail).
- A precise 2px dot sits exactly on the pointer so aim is never lost.
- Hovering an interactive element: the ring fluidly morphs into an outline that hugs that
  element — matching its corner radius, inflated a few px — in the live accent gradient.
- Pressing: the outline contracts slightly and brightens; releasing eases back.
- Nothing snaps. Every transition is continuous, including leaving one hover target and
  entering another.

### Why a rounded rect, not a circle

Model the cursor as a **rounded rect** `(cx, cy, w, h, r)`. A circle is just the case
`w == h, r == w/2`. Then "morph circle → button outline" is a single interpolation of five
numbers, with no special-casing, no path switching, and no visible seam. This is the key
trick: one shape function, two states.

### Architecture

```
utils/mouse/MorphCursor.java        // state + springs + draw
utils/mouse/HoverTarget.java        // immutable rect+radius+flags
utils/animation/spring/Spring.java  // dt-based critically-damped spring (shared)
```

1. **Blank native cursor.** LWJGL2 has no `glfwSetInputMode`. Use
   `Mouse.setNativeCursor(new Cursor(1, 1, 0, 0, 1, transparentIntBuffer, null))` when the
   menu opens, and `Mouse.setNativeCursor(null)` on close (also on `onGuiClosed`, on world
   change, and in a shutdown hook — never leave the user without a pointer).
2. **Per-frame hover registry.** `MorphCursor.beginFrame()` at the top of
   `GuiModMenu.drawNanoVG`, `MorphCursor.hover(x, y, w, h, radius)` from drawables.
   Because NanoVG paints in order, "last hovered wins" naturally resolves overlap, and
   scrolled/clipped rows report their translated coordinates. No retained tree, no stale
   targets, no layout pass.
3. **Idle target** is the pointer position with a 16px diameter; hover target is the
   announced rect inflated 4px, radius `min(rectRadius + 4, h/2)`.
4. **Springs.** Position: critically damped (no overshoot — overshoot on a pointer feels
   broken). Size/radius: slightly underdamped (ζ ≈ 0.85) so the outline has a tiny organic
   settle that reads as "premium" instead of "CSS transition".
   Integrate with real delta time, clamped to ~32ms to survive a stall:
   `x += v * dt; v += (-k * (x - target) - c * v) * dt`.
5. **Draw.** `drawGradientOutlineRoundedRect` (already exists in `NanoVGManager`) with
   `accentColor.getInterpolateColor()`; a low-alpha fill when hovering; the 2px precise dot
   on top. Stroke width eases 1.6 → 1.1 as the shape grows, so large targets do not look
   heavy.
6. **Chrome changes must be suppressed.** The menu currently draws its own hover states in
   many places; a morphing outline plus per-row highlight double-encodes the hover. Pick
   one: keep row background hover, drop per-row border, and let the ring be the focus.

### Where targets get registered (minimal touch surface)

| Place | What to announce |
|---|---|
| `Category` rail icons in `GuiModMenu` | 21x21 icon buttons, r=6 |
| `ModuleCategory` mod rows | row rect 40px, r=8 (and the settings glyph separately) |
| `ModuleCategory` category chips | chip rect, r=6 |
| `CompToggleButton`, `CompSlider`, `CompComboBox`, `CompKeybind`, `CompColorPicker`, `CompImageSelect`, `CompSoundSelect` | own bounds, r from their draw radius |
| `CompSearchBox` + text fields | field rect, r=6 (ring sits outside the caret, do not hide the caret) |
| Sidebar/home/games/profile cards | card rect, r=10 |

Give `Comp` a `getBounds()` + `getRadius()` default and register once in the settings
render loop instead of at 9 call sites.

### Edge cases that will otherwise bite

- **GUI scale changes / window resize**: reset springs to the current pointer, else the
  ring flies across the screen.
- **Screen open/close**: `initGui` resets to pointer position; `onGuiClosed` restores the
  native cursor.
- **Scrolling under a stationary pointer**: target rect moves, ring follows — this is
  correct and looks intentional, but it must not fight the scroll animation (both are
  spring-driven, so it settles).
- **Drag** (slider, color picker): pin the ring to the drag handle, do not re-target every
  frame from the pointer.
- **Text fields**: the ring plus I-beam is noisy; swap the ring for a thin vertical bar
  while focused.
- **Screenshots / recording**: gate the whole thing behind a setting
  (`InternalSettingsMod.getMorphCursorSetting()`), default on, so a broken state is one
  toggle away.
- **Framerate**: the overlay is 2–3 NanoVG paths. If a spring is fed a 0 dt, freeze rather
  than divide by zero; if a frame takes >100ms, snap instead of lerping.

**Effort**: ~250 lines new code, ~15 registration sites, 1 new setting + translation key.
**Payoff**: this is the single most visible "this client is not 2015" signal in the whole
app, and the spring utility it introduces is reused by every later phase.

---

## 4. Feature backlog

Effort: S ≤ half a day, M ≤ 2 days, L > 2 days. Impact is about perceived modernity and
retention, not lines of code.

### A. Feel and interaction (highest impact per hour)

| # | Feature | Effort | Why it belongs here |
|---|---|---|---|
| A1 | Delta-time spring system; migrate mod rows, toggles, panel slide | M | `SimpleAnimation` is frame-rate dependent and speed-capped. This is the root fix |
| A2 | Morph cursor (§3) | M | Headline; needs the registry anyway |
| A3 | Momentum scroll + rubber-band overscroll + fading scrollbar | S | `Scroll.java` currently teleports by wheel steps; one class, huge feel gain |
| A4 | Press states everywhere (0.97 scale spring on mousedown) | S | Currently only toggles animate; buttons feel dead |
| A5 | Command palette (Ctrl+K): fuzzy search any module **and** setting, Enter toggles | M | `SearchUtils` + `ModManager.getWords` already exist; turns 130 mods into a launcher |
| A6 | Keyboard navigation: Tab focus order, arrows to adjust sliders, Enter to toggle, visible focus ring | M | No focus model exists at all; also an accessibility win |
| A7 | Shared-element transition: module icon flies from its row into the settings header | M | The settings panel already slides 600px; this makes it feel authored |
| A8 | Tooltip layer with hover delay + fade, driven by the same hover registry | S | Mod descriptions exist but are unreadable in-line |
| A9 | Pointer spotlight: very low-alpha accent radial glow tracking the cursor in the menu background | S | Cheap atmosphere; ties the accent color into the canvas |
| A10 | Drag-to-reorder pinned rows / HUD elements with spring gap insertion | L | Direct manipulation is the modern idiom |

### B. Mod menu information architecture

| # | Feature | Effort | Notes |
|---|---|---|---|
| B1 | Setting-level search ("reach" finds the setting inside Reach Display) | S | Extend `SearchUtils` over `getSettingsByMod` |
| B2 | Favourites / pins section + recents row | S | Persist alongside profile JSON |
| B3 | Mod detail "page" — header with icon, description, keybind, toggle, then settings | L | Replaces the slide-over panel; bigger visual upgrade than it sounds |
| B4 | Per-server automatic profiles (match by server IP, toggle on join) | M | `ServerIPDisplayMod` already resolves the server; `ProfileManager` already switches profiles |
| B5 | Config export/import as a shareable string with version + unknown-mod mapping | M | Needs a small schema + migration, no backend |
| B6 | Import preview: show a diff of what changes before applying | M | Prevents the classic "config nuked my keybinds" |
| B7 | Keybind conflict detector + "used by" listing | S | Cheap, removes a real annoyance |
| B8 | Multi-select + batch toggle (shift-click ranges) | S | 130 modules make one-by-one painful |
| B9 | Global "reset all settings" with undo toast | S | Undo needs a settings snapshot buffer |

### C. Visual identity and HUD

| # | Feature | Effort | Notes |
|---|---|---|---|
| C1 | HUD editor: snap guides, grid, multi-select, alignment, undo | L | `GuiEditHUD` is freeform drag today |
| C2 | Design tokens class (spacing scale, radii, elevation, durations) | S | Kills the magic numbers spread across scenes; makes C3 possible |
| C3 | Theme editor: 2-color accent builder + extract palette from a screenshot | M | `AccentColor` is already a 1→2 gradient; the picker UI is the work |
| C4 | ArrayList entry animations (stagger, slide+blur in/out) | S | `ArrayListMod` already animates width |
| C5 | Notification centre with history + grouping | M | Toasts are currently fire-and-forget |
| C6 | Batched shadow/glow rendering | S | `drawShadow` issues 7 stroked paths per call, per element, per frame |
| C7 | Ambient particle layer behind the menu, accent-tinted | S | `ParticleEngine` exists, just needs scoping |

### D. Performance (1.8.9 users feel this more than any animation)

| # | Feature | Effort | Notes |
|---|---|---|---|
| D1 | Cache `getTextWidth` per (text, size, font); it runs `nvgTextBounds` and is called several times per row | S | Biggest single win in the mod list; use a bounded LRU |
| D2 | Pool `NVGPaint` allocations in image draws instead of `calloc`/`free` per call | S | Removes per-frame native allocation, reduces GC hitches |
| D3 | Skip fully-clipped rows before layout/draw, not just before text | S | Partial culling exists in `ModuleCategory` |
| D4 | Reuse `Color` objects / avoid per-frame container allocation in draw loops | S | Alloc-heavy immediate-mode code |

### E. Later / needs infrastructure

- Cloud config sync (needs an account + backend; out of scope until B5 lands).
- Waypoint sharing via JSON link (`WaypointMod` exists).
- Screenshot manager with tags, search and a detail view (`ScreenshotCategory` exists).
- Settings "time machine": snapshot on every change, scrub back through the session.

---

## 5. Phased roadmap

### Phase 0 — Make the build boring (done, keep it that way)
JDK 8 pinned locally, documented build command, artifact path confirmed. Add
`gradle.properties` memory settings. Acceptance: `./gradlew build` succeeds from a clean
clone on a machine with only a modern JDK, following the README.

### Phase 1 — "It feels alive" (≈1 week)
A1 spring system → A2 morph cursor → A3 momentum scroll → A4 press states → A5 command
palette.
Acceptance: on 60 Hz and 240 Hz the cursor settles in the same wall-clock time; no visible
cursor when the menu closes; 130-mod list scrolls with overscroll and no clipping
artifacts; Ctrl+K finds a setting inside a mod and toggles it.

### Phase 2 — "It has structure" (≈1–2 weeks)
B1 setting search → B7 keybind conflicts → B2 favourites → B4 per-server profiles → B5
config export/import.
Acceptance: join a known server, the matching profile applies within one tick; an exported
string imported on a fresh install restores toggles, keybinds and numbers, with unknown
mods reported rather than silently dropped.

### Phase 3 — "It looks designed" (≈2 weeks)
C2 design tokens → B3 mod detail pages → A7 shared-element transition → C1 HUD editor
snapping → C3 theme editor.
Acceptance: no magic radius/spacing numbers left in the mod menu scenes; HUD elements snap
to guides and to each other; a custom theme round-trips through save/load.

### Phase 4 — "It is fast" (ongoing)
D1 text cache → D2 paint pooling → C6 shadow batching → D3/D4 draw-loop allocation cleanup.
Acceptance: measured, not claimed — record frame time in the mod menu with F3-style
instrumentation before and after each item, on the same world and GUI scale.

---

## 6. Risks and verification

- **No test suite.** Add a plain JUnit source set for the pure logic that is easy to get
  wrong: spring integration (frame-rate independence, no NaN at dt=0), rect inflation and
  radius clamping, config schema round-trip, `SearchUtils` matching. Everything visual stays
  a manual checklist — do not pretend otherwise in PRs.
- **Mixin fragility.** Anything touching `GuiScreen`, `MouseHelper` or `Minecraft` needs a
  matching AT entry (`soar_at.cfg`) or a mixin in `mixins.soar.json`. Cursor hiding via
  `Mouse.setNativeCursor` is plain LWJGL and needs neither — prefer that.
- **Restricted-server optics.** UI work is safe; features that change gameplay packets
  (auto-config on join is fine, anything sending chat automatically is not) must stay
  behind the existing `isRestricted()` flag.
- **Regression checklist per phase:** mod menu opens/closes with right shift; HUD editor
  reachable; settings persist after restart; fullscreen toggle; GUI scale 1/2/3; laptop
  trackpad and high-DPI; alt-tab while the menu is open restores the pointer.
- **Ship in slices.** Each backlog row is independently revertable; the cursor overlay and
  the spring migration are the only two with wide blast radius, so land them first and
  alone.

---

## 7. Decisions I would want from you

1. **Scope of the cursor:** mod menu only, or every Glide screen (main menu, HUD editor,
   inventory overlays)? Everywhere is more consistent but multiplies registration sites.
2. **Mod detail pages vs. the slide-over panel:** B3 is the largest UI change in the plan.
   Do we go for it, or keep the current panel and spend that time on feel + performance?
3. **Config sharing transport:** clipboard string is trivial and offline; a share code with
   a backend is a product decision, not an engineering one.
4. **Design tokens:** do you want Glide to converge on one spacing/radius scale (a small
   visual change everywhere), or keep each scene's current numbers?
