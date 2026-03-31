package com.codenameone.examples.hellocodenameone.tests;

import com.codename1.ui.Component;
import com.codename1.ui.Container;
import com.codename1.ui.Font;
import com.codename1.ui.Form;
import com.codename1.ui.Label;
import com.codename1.ui.TextArea;
import com.codename1.ui.TextField;
import com.codename1.ui.layouts.BorderLayout;
import com.codename1.ui.layouts.GridLayout;

public class TextAreaAlignmentScreenshotTest extends BaseTest {
    private Font smallFont;

    private Font getSmallFont() {
        if (smallFont == null) {
            Font base = Font.createTrueTypeFont(Font.NATIVE_MAIN_REGULAR, Font.NATIVE_MAIN_REGULAR);
            smallFont = base.derive(base.getPixelSize() * 0.45f, Font.STYLE_PLAIN);
        }
        return smallFont;
    }

    @Override
    public boolean runTest() {
        Form form = createForm("Text Alignment", new BorderLayout(), "TextAreaAlignmentStates");
        form.add(BorderLayout.CENTER, createAlignmentGrid());
        form.show();
        return true;
    }

    private Container createAlignmentGrid() {
        Container grid = new Container(new GridLayout(4, 2));
        grid.getAllStyles().setPadding(4, 4, 4, 4);
        grid.getAllStyles().setBgColor(0xf5f5f5);
        grid.getAllStyles().setBgTransparency(255);

        grid.add(createSample("Field Top", createField(Component.TOP)));
        grid.add(createSample("Area Top", createArea(Component.TOP, false)));
        grid.add(createSample("Field Center", createField(Component.CENTER)));
        grid.add(createSample("Area Center", createArea(Component.CENTER, false)));
        grid.add(createSample("Field Bottom", createField(Component.BOTTOM)));
        grid.add(createSample("Area Bottom", createArea(Component.BOTTOM, false)));
        grid.add(createSample("Area Bottom Overflow", createArea(Component.BOTTOM, true)));
        grid.add(createSample("Area Center Overflow", createArea(Component.CENTER, true)));
        return grid;
    }

    private Container createSample(String title, TextArea input) {
        Container wrapper = new Container(new BorderLayout());
        wrapper.getAllStyles().setPadding(2, 2, 2, 2);
        Label titleLabel = new Label(title);
        titleLabel.getAllStyles().setFont(getSmallFont());
        wrapper.add(BorderLayout.NORTH, titleLabel);
        wrapper.add(BorderLayout.CENTER, input);
        return wrapper;
    }

    private TextField createField(int valign) {
        TextField field = new TextField("Single line");
        field.getAllStyles().setFont(getSmallFont());
        field.setVerticalAlignment(valign);
        field.setEditable(false);
        field.setPreferredH(field.getPreferredH() * 3);
        return field;
    }

    private TextArea createArea(int valign, boolean overflow) {
        TextArea area = overflow
                ? new TextArea("Line 1\nLine 2\nLine 3\nLine 4\nLine 5")
                : new TextArea("Line 1\nLine 2");
        area.setSingleLineTextArea(false);
        area.getAllStyles().setFont(getSmallFont());
        area.setEditable(false);
        area.setVerticalAlignment(valign);
        area.setRows(overflow ? 3 : 4);
        return area;
    }
}
