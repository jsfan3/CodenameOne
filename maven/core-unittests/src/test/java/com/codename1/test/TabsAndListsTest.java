package com.codename1.test;

import com.codename1.junit.FormTest;
import com.codename1.junit.UITestBase;
import com.codename1.ui.ComboBox;
import com.codename1.ui.Component;
import com.codename1.ui.Image;
import com.codename1.ui.Label;
import com.codename1.ui.List;
import com.codename1.ui.Tabs;
import com.codename1.ui.FontImage;
import com.codename1.ui.plaf.Style;
import com.codename1.ui.plaf.UIManager;
import com.codename1.ui.list.DefaultListModel;
import com.codename1.ui.list.ListModel;

import org.junit.jupiter.api.Test;

import java.util.Vector;

import static org.junit.jupiter.api.Assertions.*;

public class TabsAndListsTest extends UITestBase {
    @FormTest
    void tabsSelectionTracksComponents() {
        Tabs tabs = new Tabs();
        Component first = new Label("First");
        Component second = new Label("Second");
        tabs.addTab("One", first);
        tabs.addTab("Two", second);

        assertEquals(2, tabs.getTabCount());
        assertSame(first, tabs.getTabComponentAt(0));
        assertSame(second, tabs.getTabComponentAt(1));

        tabs.setSelectedIndex(1);
        assertEquals(1, tabs.getSelectedIndex());
        assertSame(second, tabs.getSelectedComponent());
    }

    @FormTest
    void materialTabIconUsesTabPressedStyle() {
        Style tabStyle = new Style(UIManager.getInstance().getComponentStyle("Tab"));
        tabStyle.setFgColor(0xff0000);
        tabStyle.setFgAlpha(255);
        UIManager.getInstance().setComponentStyle("Tab", tabStyle);

        Style pressedStyle = new Style(tabStyle);
        pressedStyle.setFgColor(0x00aa00);
        UIManager.getInstance().setComponentStyle("Tab", pressedStyle, "press");

        Tabs tabs = new Tabs();
        tabs.addTab("MatIcn", FontImage.MATERIAL_10MP, 8, new Label("material"));

        Image icon = tabs.getTabIcon(0);
        Image pressedIcon = tabs.getTabSelectedIcon(0);

        assertNotNull(icon, "Material tabs should create an icon");
        assertNotNull(pressedIcon, "Material tabs should create a pressed icon when Tab.press style differs");
        assertNotEquals(firstOpaqueRgb(icon), firstOpaqueRgb(pressedIcon), "Pressed tab icon color should differ from unselected");
    }

    private int firstOpaqueRgb(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] rgb = new int[width * height];
        image.getRGB(rgb, 0, 0, 0, width, height);
        for (int pixel : rgb) {
            if (((pixel >>> 24) & 0xff) > 0) {
                return pixel & 0x00ffffff;
            }
        }
        fail("Expected at least one opaque pixel in icon");
        return -1;
    }

    @Test
    void listSelectionReflectsModel() {
        DefaultListModel<String> model = new DefaultListModel<>("Alpha", "Beta", "Gamma");
        List<String> list = new List<>(model);
        list.setSelectedIndex(2);

        assertEquals(3, model.getSize());
        assertEquals(2, list.getSelectedIndex());
        assertEquals("Gamma", list.getSelectedItem());

        model.removeItem(1);
        assertEquals(2, model.getSize());
        assertEquals("Gamma", model.getItemAt(1));

        list.setSelectedIndex(1);
        assertEquals("Gamma", list.getSelectedItem());
    }

    @Test
    void comboBoxModelOperations() {
        Vector<String> values = new Vector<>();
        values.add("Red");
        values.add("Green");
        ComboBox<String> comboBox = new ComboBox<>(values);
        comboBox.addItem("Blue");

        ListModel<String> model = comboBox.getModel();
        assertEquals(3, model.getSize());
        assertEquals("Red", model.getItemAt(0));
        assertEquals("Blue", model.getItemAt(2));

        if (model instanceof DefaultListModel) {
            DefaultListModel<String> defaultModel = (DefaultListModel<String>) model;
            defaultModel.removeItem(1);
            assertEquals(2, defaultModel.getSize());
            assertEquals("Blue", defaultModel.getItemAt(1));
        } else {
            fail("ComboBox should expose a DefaultListModel by default");
        }
    }
}
