package com.codenameone.examples.hellocodenameone.tests;

import com.codename1.ui.Button;
import com.codename1.ui.Container;
import com.codename1.ui.FontImage;
import com.codename1.ui.Form;
import com.codename1.ui.Tabs;
import com.codename1.ui.layouts.BorderLayout;
import com.codename1.ui.plaf.Style;
import com.codename1.ui.plaf.UIManager;

public class TabsScreenshotTest extends BaseTest {
    @Override
    public boolean runTest() {
        applyTabStyles();

        Form form = createForm("Tabs", new BorderLayout(), "TabsBehavior");
        Container content = new Container(new BorderLayout());
        Tabs tabs = new Tabs();

        tabs.addTab("MatIcn", FontImage.MATERIAL_10MP, 8, new Button("Tab with material icon"));
        tabs.addTab("ImgIcn", FontImage.MATERIAL_HOME, 8, new Button("Tab with explicit selected icon"));
        tabs.addTab("Txt", new Button("Tab without icon"));
        tabs.setTabSelectedIcon(1, FontImage.createMaterial(FontImage.MATERIAL_HOME, tabs.getTabsContainer().getComponentAt(1).getPressedStyle(), 8));
        tabs.setSelectedIndex(1);

        content.add(BorderLayout.CENTER, tabs);
        form.add(BorderLayout.CENTER, content);
        form.show();
        return true;
    }

    private void applyTabStyles() {
        UIManager manager = UIManager.getInstance();
        Style tab = new Style(manager.getComponentStyle("Tab"));
        tab.setFgColor(0xff0000);
        tab.setFgAlpha(255);
        manager.setComponentStyle("Tab", tab);

        Style pressed = new Style(tab);
        pressed.setFgColor(0x00aa00);
        manager.setComponentStyle("Tab", pressed, "press");
    }
}
