package com.idyl.snailman;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.timetracking.Tab;
import net.runelite.client.plugins.timetracking.TabContentPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

@Singleton
public class SnailManModePanel extends PluginPanel
{
    private final ItemManager itemManager;
    private final SnailManModePlugin plugin;

    /* This is the panel the tabs' respective panels will be displayed on. */
    private final JPanel display = new JPanel();
    private final Map<Tab, MaterialTab> uiTabs = new HashMap<>();
    private final MaterialTabGroup tabGroup = new MaterialTabGroup(display);

    private boolean active;

    @Nullable
    private TabContentPanel activeTabPanel = null;

    @Inject
    public SnailManModePanel(ItemManager itemManager, SnailManModePlugin plugin)
    {
        super(false);
        this.itemManager = itemManager;
        this.plugin = plugin;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        display.setBorder(new EmptyBorder(10, 10, 8, 10));

        JButton resetButton = new JButton("Reset Snail Data");
        resetButton.addActionListener(l -> plugin.reset());

        display.add(resetButton);

        add(display, BorderLayout.NORTH);
    }
}