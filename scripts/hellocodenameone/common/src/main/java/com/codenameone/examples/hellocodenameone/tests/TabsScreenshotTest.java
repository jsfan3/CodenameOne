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
        tabs.setTabUIID("TabsColorSync");

        tabs.addTab("MatIcn", FontImage.MATERIAL_10MP, 8, new Button("Tab with material icon"));
        tabs.addTab("MatHome", FontImage.MATERIAL_HOME, 8, new Button("Second tab with material icon"));
        tabs.addTab("Txt", new Button("Tab without icon"));
        tabs.setSelectedIndex(1);

        content.add(BorderLayout.CENTER, tabs);
        form.add(BorderLayout.CENTER, content);
        form.show();
        return true;
    }

    private void applyTabStyles() {
        UIManager manager = UIManager.getInstance();
        Style tab = new Style(manager.getComponentStyle("TabsColorSync"));
        tab.setFgColor(0xff0000);
        tab.setFgAlpha(255);
        manager.setComponentStyle("TabsColorSync", tab);

        Style selected = new Style(tab);
        selected.setFgColor(0x00aa00);
        manager.setComponentSelectedStyle("TabsColorSync", selected);
        manager.setComponentStyle("TabsColorSync", selected, "press");
    }
}
