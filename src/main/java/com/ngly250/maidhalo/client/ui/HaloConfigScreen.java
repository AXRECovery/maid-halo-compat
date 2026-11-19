package com.ngly250.maidhalo.client.ui;

import com.ngly250.maidhalo.config.MaidHaloConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * 可视化配置页：把 {@link MaidHaloConfig#guiEntries()} 里的所有配置项生成控件，
 * 另外提供「按模型单独调整终末之环高度」的列表（带三级分组的模型选择器）。
 *
 * <p>内容整体可滚动；底部「完成」写入并落盘，「取消」丢弃。</p>
 */
public class HaloConfigScreen extends Screen {
    private static final int MARGIN = 12;
    private static final int ROW = 22;
    private static final int HEADER = 24;
    private static final int SQUARE = 18;
    private static final int EDIT_WIDTH = 70;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int PICKER_ROW = 18;

    private enum Kind {
        SECTION, BOOL, DOUBLE, MODEL
    }

    private static final class Row {
        Kind kind;
        String path;
        String modelId;
        String label;
        Button toggle;
        EditBox box;
        Button remove;
        int y;
    }

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    /** 选择器打开时被临时摘除的控件（渲染与点击都完全断开）。 */
    private final List<net.minecraft.client.gui.components.Renderable> detachedRenderables = new ArrayList<>();
    private final List<net.minecraft.client.gui.components.events.GuiEventListener> detachedChildren = new ArrayList<>();
    private final Map<String, Boolean> boolValues = new LinkedHashMap<>();

    private Button doneButton;
    private Button cancelButton;
    private Button addModelButton;

    private int listTop;
    private int listBottom;
    private int scroll;

    // ── 模型选择器 ──
    private record PickRow(String groupKey, Component title, MaidModelCatalog.Entry entry, int count) {
        boolean header() {
            return this.entry == null;
        }
    }

    private final List<PickRow> pickRows = new ArrayList<>();
    private final Set<String> collapsed = new LinkedHashSet<>();
    private boolean collapseInit;
    private boolean pickerOpen;
    private int pickerScroll;
    private int pickerTop;
    private int pickerBottom;
    private EditBox searchBox;
    private String searchQuery = "";
    private boolean draggingScrollbar;
    private int dragGrabOffset;
    private String hoveredId;
    private String pickerHoveredId;

    public HaloConfigScreen(Screen parent) {
        super(Component.translatable("gui.maid_halo_compat.config.title"));
        this.parent = parent;
    }

    // ───────────────────────────── 初始化 ─────────────────────────────

    @Override
    protected void init() {
        this.rows.clear();
        this.boolValues.clear();
        this.scroll = 0;

        this.doneButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.maid_halo_compat.config.done"), b -> this.saveAndClose())
                .bounds(this.left(), this.height - 28, 100, 20).build());
        this.cancelButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.maid_halo_compat.config.cancel"), b -> this.onClose())
                .bounds(this.left() + 108, this.height - 28, 100, 20).build());

        this.listTop = 26;
        this.listBottom = this.height - 34;

        String section = null;
        for (MaidHaloConfig.GuiEntry entry : MaidHaloConfig.guiEntries()) {
            if (entry.kind() == MaidHaloConfig.Kind.LIST) {
                continue;   // endHaloLiftByModel 用下面的模型列表 UI 编辑
            }
            String prefix = entry.path().substring(0, entry.path().indexOf('.'));
            if (!prefix.equals(section)) {
                section = prefix;
                this.rows.add(this.sectionRow(prefix));
            }
            this.rows.add(entry.kind() == MaidHaloConfig.Kind.BOOL
                    ? this.boolRow(entry)
                    : this.numberRow(entry));
        }

        Row modelSection = new Row();
        modelSection.kind = Kind.SECTION;
        modelSection.label = Component.translatable("gui.maid_halo_compat.config.end_halo_section").getString();
        this.rows.add(modelSection);
        for (Map.Entry<String, Float> e : new LinkedHashMap<>(MaidHaloConfig.endHaloLiftOverrides()).entrySet()) {
            this.rows.add(this.modelRow(e.getKey(), e.getValue()));
        }

        this.addModelButton = this.addRenderableWidget(Button.builder(Component.literal("+"), b -> this.togglePicker())
                .bounds(0, 0, 20, 20).build());

        this.layoutRows();
    }

    private Row sectionRow(String prefix) {
        Row row = new Row();
        row.kind = Kind.SECTION;
        row.label = Component.translatable("gui.maid_halo_compat.section." + prefix).getString();
        return row;
    }

    private Row boolRow(MaidHaloConfig.GuiEntry entry) {
        Row row = new Row();
        row.kind = Kind.BOOL;
        row.path = entry.path();
        row.label = Component.translatable("gui.maid_halo_compat.opt." + entry.path()).getString();
        Object value = MaidHaloConfig.guiGet(entry);
        boolean on = !(value instanceof Boolean b) || b;
        this.boolValues.put(entry.path(), on);
        row.toggle = this.addRenderableWidget(Button.builder(this.boolLabel(on), b -> {
            boolean next = !this.boolValues.getOrDefault(entry.path(), true);
            this.boolValues.put(entry.path(), next);
            row.toggle.setMessage(this.boolLabel(next));
        }).bounds(0, 0, 56, 20).build());
        return row;
    }

    private Component boolLabel(boolean on) {
        return Component.translatable(on
                ? "gui.maid_halo_compat.config.on"
                : "gui.maid_halo_compat.config.off");
    }

    private Row numberRow(MaidHaloConfig.GuiEntry entry) {
        Row row = new Row();
        row.kind = Kind.DOUBLE;
        row.path = entry.path();
        row.label = Component.translatable("gui.maid_halo_compat.opt." + entry.path()).getString();
        Object value = MaidHaloConfig.guiGet(entry);
        row.box = this.addRenderableWidget(new EditBox(this.font, 0, 0, EDIT_WIDTH, 20, Component.literal(entry.path())));
        row.box.setMaxLength(12);
        row.box.setValue(formatNumber(value instanceof Number n ? n.doubleValue() : 0.0D));
        return row;
    }

    private Row modelRow(String modelId, float value) {
        Row row = new Row();
        row.kind = Kind.MODEL;
        row.modelId = modelId;
        row.label = MaidModelCatalog.displayName(modelId);
        row.box = this.addRenderableWidget(new EditBox(this.font, 0, 0, EDIT_WIDTH, 20, Component.literal(modelId)));
        row.box.setMaxLength(12);
        row.box.setValue(formatNumber(value));
        row.remove = this.addRenderableWidget(Button.builder(Component.literal("×"), b -> {
            this.rows.remove(row);
            this.removeWidget(row.box);
            this.removeWidget(row.remove);
            this.layoutRows();
        }).bounds(0, 0, SQUARE, 18).build());
        return row;
    }

    private static String formatNumber(double value) {
        return Math.abs(value - Math.rint(value)) < 1.0E-6D
                ? String.valueOf((long) Math.rint(value))
                : String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    // ───────────────────────────── 布局 / 滚动 ─────────────────────────────

    private int left() {
        return MARGIN;
    }

    private int right() {
        return this.width - MARGIN;
    }

    private int viewHeight() {
        return this.listBottom - this.listTop - HEADER;
    }

    private int contentHeight() {
        int h = 0;
        for (Row row : this.rows) {
            h += row.kind == Kind.SECTION ? HEADER : ROW;
        }
        return h + ROW;
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight() - this.viewHeight());
    }

    private void layoutRows() {
        int y = this.listTop + HEADER - this.scroll;
        int controlRight = this.right() - 4 - SCROLLBAR_WIDTH;
        for (Row row : this.rows) {
            row.y = y;
            if (row.kind == Kind.SECTION) {
                y += HEADER;
                continue;
            }
            if (row.toggle != null) {
                row.toggle.setX(controlRight - 56);
                row.toggle.setY(y);
            }
            if (row.box != null) {
                row.box.setX(controlRight - EDIT_WIDTH - (row.remove != null ? SQUARE + 4 : 0));
                row.box.setY(y);
            }
            if (row.remove != null) {
                row.remove.setX(controlRight - SQUARE);
                row.remove.setY(y + 1);
            }
            y += ROW;
        }
        if (this.addModelButton != null) {
            this.addModelButton.setX(controlRight - 20);
            this.addModelButton.setY(y + 2);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.pickerOpen) {
            this.pickerScroll = this.clampPicker(this.pickerScroll - (int) (delta * PICKER_ROW));
            return true;
        }
        this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int) (delta * ROW)));
        this.layoutRows();
        return true;
    }

    // ───────────────────────────── 绘制 ─────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        this.hoveredId = null;

        graphics.fill(this.left() - 2, this.listTop - 6, this.right() + 2, this.listBottom + 2, 0xC0101014);
        graphics.fill(this.left() - 2, this.listTop - 6, this.right() + 2, this.listTop - 5, 0xFF3C3C46);
        graphics.fill(this.left() - 2, this.listBottom + 1, this.right() + 2, this.listBottom + 2, 0xFF3C3C46);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);

        this.enableScissor(graphics, this.left(), this.listTop + HEADER - 4, this.right(), this.listBottom - 2);
        int labelX = this.left() + 2;
        int controlLeft = this.right() - 4 - SCROLLBAR_WIDTH - EDIT_WIDTH - SQUARE;
        for (Row row : this.rows) {
            if (!this.visible(row.y)) {
                continue;
            }
            if (row.kind == Kind.SECTION) {
                graphics.drawString(this.font, row.label, labelX, row.y + 6, 0xFFFFE080, false);
                graphics.fill(labelX, row.y + HEADER - 6, this.right() - 2, row.y + HEADER - 5, 0x30FFFFFF);
                continue;
            }
            graphics.drawString(this.font, row.label, labelX, row.y + 6, 0xFFE0E0E0, false);
            if (row.kind == Kind.MODEL && mouseX >= labelX && mouseX < controlLeft
                    && mouseY >= row.y && mouseY < row.y + ROW) {
                this.hoveredId = row.modelId;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();

        // 底部「完成 / 取消」在内容裁剪区之外，super.render 里会被 scissor 裁掉，这里补画一次
        if (!this.pickerOpen) {
            this.doneButton.render(graphics, mouseX, mouseY, partialTick);
            this.cancelButton.render(graphics, mouseX, mouseY, partialTick);
        }

        this.drawScrollbar(graphics, mouseX, mouseY);

        this.pickerHoveredId = null;
        if (this.pickerOpen) {
            graphics.flush();
            this.renderPicker(graphics, mouseX, mouseY);
            graphics.flush();
            // 选择器里的条目悬停提示（面板之后画，保证在列表之上）
            if (this.pickerHoveredId != null) {
                graphics.renderTooltip(this.font, Component.literal(this.pickerHoveredId), mouseX, mouseY);
            }
        } else if (this.hoveredId != null) {
            graphics.renderTooltip(this.font, Component.literal(this.hoveredId), mouseX, mouseY);
        }
    }

    private boolean visible(int y) {
        return y + ROW > this.listTop + HEADER - 4 && y < this.listBottom - 2;
    }

    private void drawScrollbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.right() - SCROLLBAR_WIDTH;
        int top = this.listTop + HEADER;
        int height = this.viewHeight();
        graphics.fill(x, top, x + SCROLLBAR_WIDTH, top + height, 0x60000000);
        if (this.contentHeight() <= height) {
            return;
        }
        int thumb = Math.max(14, (int) ((long) height * height / Math.max(1, this.contentHeight())));
        int ty = top + (int) ((long) (height - thumb) * this.scroll / Math.max(1, this.maxScroll()));
        boolean hover = mouseX >= x && mouseX < x + SCROLLBAR_WIDTH && mouseY >= ty && mouseY < ty + thumb;
        graphics.fill(x, ty, x + SCROLLBAR_WIDTH, ty + thumb,
                this.draggingScrollbar || hover ? 0xFFA8C8F0 : 0xFF7A96B8);
    }

    private void enableScissor(GuiGraphics graphics, int x0, int y0, int x1, int y1) {
        int left = Math.min(x0, x1);
        int top = Math.min(y0, y1);
        int right = Math.max(x0, x1);
        int bottom = Math.max(y0, y1);
        if (right > left && bottom > top) {
            graphics.enableScissor(left, top, right, bottom);
        }
    }

    // ───────────────────────────── 输入 ─────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.pickerOpen) {
            int x0 = this.left();
            int x1 = this.right();
            if (mouseX >= x0 && mouseX < x1 && mouseY >= this.pickerTop && mouseY < this.pickerBottom) {
                if (this.searchBox != null && this.searchBox.mouseClicked(mouseX, mouseY, button)) {
                    return true;
                }
                if (button != 0) {
                    return true;
                }
                // 点搜索框区域（空白处）不当作列表点击
                if (this.searchBox != null && mouseY < this.pickerTrackTop() - 2) {
                    return true;
                }
                int trackX = this.pickerTrackX();
                int trackTop = this.pickerTrackTop();
                int trackHeight = this.pickerTrackHeight();
                if (mouseX >= trackX - 2 && mouseX < trackX + SCROLLBAR_WIDTH + 2
                        && mouseY >= trackTop && mouseY < trackTop + trackHeight) {
                    int thumb = this.pickerThumb();
                    int thumbY = this.pickerThumbY();
                    this.draggingScrollbar = true;
                    this.dragGrabOffset = (mouseY >= thumbY && mouseY < thumbY + thumb)
                            ? (int) (mouseY - thumbY) : thumb / 2;
                    this.scrollPickerFromMouse((int) mouseY);
                    return true;
                }
                if (mouseX < trackX - 4) {
                    int index = (int) ((mouseY - trackTop - 2 + this.pickerScroll) / PICKER_ROW);
                    if (mouseY >= trackTop && index >= 0 && index < this.pickRows.size()) {
                        PickRow row = this.pickRows.get(index);
                        if (row.header()) {
                            this.toggleGroup(row.groupKey());
                        } else {
                            this.closePicker();
                            this.addModelRow(row.entry().id());
                        }
                    }
                }
                return true;
            }
            this.closePicker();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.pickerOpen && this.draggingScrollbar) {
            this.scrollPickerFromMouse((int) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingScrollbar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.pickerOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.closePicker();
                return true;
            }
            if (this.searchBox != null) {
                this.searchBox.keyPressed(keyCode, scanCode, modifiers);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    // ───────────────────────────── 保存 ─────────────────────────────

    private void saveAndClose() {
        for (Row row : this.rows) {
            if (row.kind == Kind.BOOL && row.path != null) {
                MaidHaloConfig.guiSet(this.entryFor(row.path), this.boolValues.getOrDefault(row.path, true));
            } else if (row.kind == Kind.DOUBLE && row.path != null) {
                try {
                    MaidHaloConfig.guiSet(this.entryFor(row.path),
                            Double.parseDouble(row.box.getValue().trim().replace(',', '.')));
                } catch (NumberFormatException ignored) {
                    // 非法输入：保持原值
                }
            }
        }
        Map<String, Float> lifts = new LinkedHashMap<>();
        for (Row row : this.rows) {
            if (row.kind == Kind.MODEL && row.modelId != null) {
                try {
                    lifts.put(row.modelId, Float.parseFloat(row.box.getValue().trim().replace(',', '.')));
                } catch (NumberFormatException ignored) {
                    // 非法输入：跳过该行
                }
            }
        }
        MaidHaloConfig.replaceEndHaloLifts(lifts);
        MaidHaloConfig.saveGui();
        this.onClose();
    }

    private MaidHaloConfig.GuiEntry entryFor(String path) {
        for (MaidHaloConfig.GuiEntry entry : MaidHaloConfig.guiEntries()) {
            if (entry.path().equals(path)) {
                return entry;
            }
        }
        throw new IllegalStateException("unknown config entry: " + path);
    }

    // ───────────────────────────── 模型选择器 ─────────────────────────────

    private void addModelRow(String modelId) {
        if (this.rows.stream().anyMatch(row -> row.kind == Kind.MODEL && modelId.equals(row.modelId))) {
            return;
        }
        this.rows.add(this.modelRow(modelId,
                com.ngly250.maidhalo.client.halo.EndHaloRenderer.defaultEndHaloLift(modelId)));
        this.layoutRows();
    }

    /** 打开选择器：把底层控件从 children / renderables 摘掉，彻底避免文字叠在列表上、以及点击穿透。 */
    private void detachBaseWidgets() {
        if (!this.detachedChildren.isEmpty()) {
            return;
        }
        this.detachedChildren.addAll(this.children());
        this.children().clear();
        this.detachedRenderables.addAll(this.renderables);
        this.renderables.clear();
        this.setFocused(null);
    }

    /** 关闭选择器：把控件原样挂回（输入框内容不会丢）。 */
    @SuppressWarnings("unchecked")
    private void attachBaseWidgets() {
        if (this.detachedChildren.isEmpty()) {
            return;
        }
        java.util.List<net.minecraft.client.gui.components.events.GuiEventListener> children =
                (java.util.List<net.minecraft.client.gui.components.events.GuiEventListener>) (java.util.List<?>) this.children();
        children.addAll(this.detachedChildren);
        this.detachedChildren.clear();
        this.renderables.addAll(this.detachedRenderables);
        this.detachedRenderables.clear();
    }

    private void togglePicker() {
        this.pickerOpen = !this.pickerOpen;
        if (this.pickerOpen) {
            this.detachBaseWidgets();
            this.createSearchBox();
            this.refreshPicker();
        } else {
            this.closePicker();
        }
    }

    /** 选择器里的搜索框（按名字或 id 过滤）。 */
    private void createSearchBox() {
        this.searchQuery = "";
        int pickerTop = this.listTop + 4;
        this.searchBox = new EditBox(this.font, this.left() + 8, pickerTop + 18, this.right() - this.left() - 60, 16,
                Component.translatable("gui.maid_halo_compat.config.search"));
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("gui.maid_halo_compat.config.search_hint"));
        this.searchBox.setResponder(query -> {
            this.searchQuery = query == null ? "" : query.trim();
            this.refreshPicker();
        });
        this.addRenderableWidget(this.searchBox);
        this.setFocused(this.searchBox);
    }

    private void closePicker() {
        this.pickerOpen = false;
        if (this.searchBox != null) {
            this.removeWidget(this.searchBox);
            this.searchBox = null;
        }
        this.attachBaseWidgets();
    }

    private void refreshPicker() {
        List<MaidModelCatalog.Group> groups = MaidModelCatalog.groups();
        if (!this.collapseInit) {
            for (MaidModelCatalog.Group group : groups) {
                this.collapsed.add(group.key());
            }
            this.collapseInit = true;
        }
        String query = this.searchQuery == null ? "" : this.searchQuery.toLowerCase(java.util.Locale.ROOT);
        boolean searching = !query.isEmpty();
        this.pickRows.clear();
        for (MaidModelCatalog.Group group : groups) {
            List<MaidModelCatalog.Entry> visible = new ArrayList<>();
            for (MaidModelCatalog.Entry entry : group.entries()) {
                boolean already = this.rows.stream()
                        .anyMatch(row -> row.kind == Kind.MODEL && entry.id().equals(row.modelId));
                if (already) {
                    continue;
                }
                if (searching) {
                    String name = entry.display() == null ? "" : entry.display().toLowerCase(java.util.Locale.ROOT);
                    String id = entry.id() == null ? "" : entry.id().toLowerCase(java.util.Locale.ROOT);
                    if (!name.contains(query) && !id.contains(query)) {
                        continue;
                    }
                }
                visible.add(entry);
            }
            if (searching && visible.isEmpty()) {
                continue;   // 搜索时不显示空分组
            }
            this.pickRows.add(new PickRow(group.key(), group.title(), null, visible.size()));
            if (searching || !this.collapsed.contains(group.key())) {
                for (MaidModelCatalog.Entry entry : visible) {
                    this.pickRows.add(new PickRow(group.key(), null, entry, 0));
                }
            }
        }
        this.pickerScroll = this.clampPicker(this.pickerScroll);
    }

    private void toggleGroup(String key) {
        if (!this.collapsed.remove(key)) {
            this.collapsed.add(key);
        }
        this.refreshPicker();
    }

    private int pickerTrackX() {
        return this.right() - 4 - SCROLLBAR_WIDTH;
    }

    private int pickerTrackTop() {
        return this.pickerTop + 42;
    }

    private int pickerTrackHeight() {
        return Math.max(1, this.pickerBottom - 4 - this.pickerTrackTop());
    }

    private int pickerContentHeight() {
        return this.pickRows.size() * PICKER_ROW;
    }

    private int pickerMaxScroll() {
        return Math.max(0, this.pickerContentHeight() - (this.pickerTrackHeight() - 4));
    }

    private int clampPicker(int value) {
        return Math.max(0, Math.min(value, this.pickerMaxScroll()));
    }

    private int pickerThumb() {
        int track = this.pickerTrackHeight();
        int content = Math.max(1, this.pickerContentHeight());
        return Math.max(14, (int) ((long) track * track / content));
    }

    private int pickerThumbY() {
        int track = this.pickerTrackHeight();
        int thumb = this.pickerThumb();
        int max = this.pickerMaxScroll();
        return this.pickerTrackTop() + (max <= 0 ? 0 : (int) ((long) (track - thumb) * this.pickerScroll / max));
    }

    private void scrollPickerFromMouse(int mouseY) {
        int track = this.pickerTrackHeight();
        int thumb = this.pickerThumb();
        int span = Math.max(1, track - thumb);
        int pos = Math.max(0, Math.min(span, mouseY - this.pickerTrackTop() - this.dragGrabOffset));
        this.pickerScroll = this.clampPicker((int) ((long) pos * this.pickerMaxScroll() / span));
    }

    private void renderPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        int x0 = this.left();
        int x1 = this.right();
        this.pickerTop = this.listTop + 4;
        this.pickerBottom = this.listBottom - 4;

        graphics.fill(x0 - 1, this.pickerTop - 1, x1 + 1, this.pickerBottom + 1, 0xFF000000);
        graphics.fill(x0, this.pickerTop, x1, this.pickerBottom, 0xFF12161C);
        graphics.fill(x0, this.pickerTop, x1, this.pickerTop + 1, 0xFF5A8AD6);
        graphics.fill(x0, this.pickerBottom - 1, x1, this.pickerBottom, 0xFF5A8AD6);
        graphics.drawString(this.font, Component.translatable("gui.maid_halo_compat.config.pick_model"),
                x0 + 8, this.pickerTop + 6, 0xFF9CC8FF, false);

        // 搜索框也是 Screen 的控件，会被 super.render 画在面板之下，这里在面板之上再画一次
        if (this.searchBox != null) {
            this.searchBox.render(graphics, mouseX, mouseY,
                    this.minecraft == null ? 0.0F : this.minecraft.getFrameTime());
        }

        int listRight = this.pickerTrackX() - 4;
        int clipTop = this.pickerTrackTop();
        int clipBottom = this.pickerBottom - 2;
        this.enableScissor(graphics, x0 + 2, clipTop, listRight, clipBottom);
        int y = clipTop + 2 - this.pickerScroll;
        for (PickRow row : this.pickRows) {
            if (y + PICKER_ROW > clipTop && y < clipBottom) {
                boolean hovered = mouseX >= x0 + 4 && mouseX < listRight
                        && mouseY >= y && mouseY < y + PICKER_ROW;
                if (row.header()) {
                    if (hovered) {
                        graphics.fill(x0 + 2, y, listRight, y + PICKER_ROW - 1, 0xFF1C2A38);
                    }
                    graphics.drawString(this.font, this.collapsed.contains(row.groupKey()) ? "▶" : "▼",
                            x0 + 6, y + 4, hovered ? 0xFFFFFF80 : 0xFF9CC8FF, false);
                    graphics.drawString(this.font, row.title(), x0 + 20, y + 4,
                            hovered ? 0xFFFFFF80 : 0xFFBFD8F0, false);
                    String count = "(" + row.count() + ")";
                    graphics.drawString(this.font, count, listRight - 6 - this.font.width(count), y + 4,
                            0xFF808A96, false);
                } else {
                    if (hovered) {
                        graphics.fill(x0 + 2, y, listRight, y + PICKER_ROW - 1, 0xFF243445);
                        this.pickerHoveredId = row.entry().id();
                    }
                    graphics.drawString(this.font, row.entry().display(), x0 + 26, y + 4,
                            hovered ? 0xFFFFFF80 : 0xFFDDDDDD, false);
                }
            }
            y += PICKER_ROW;
        }
        graphics.disableScissor();
        if (this.pickRows.isEmpty()) {
            graphics.drawString(this.font, Component.translatable("gui.maid_halo_compat.config.no_models"),
                    x0 + 10, this.pickerTrackTop() + 4, 0xFF808080, false);
        }

        int trackX = this.pickerTrackX();
        int trackTop = this.pickerTrackTop();
        int trackHeight = this.pickerTrackHeight();
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + trackHeight, 0x60000000);
        if (this.pickerContentHeight() > trackHeight - 4) {
            int thumb = this.pickerThumb();
            int thumbY = this.pickerThumbY();
            boolean thumbHovered = mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                    && mouseY >= thumbY && mouseY < thumbY + thumb;
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumb,
                    this.draggingScrollbar || thumbHovered ? 0xFFA8C8F0 : 0xFF7A96B8);
        }
    }
}
