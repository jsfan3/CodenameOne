package com.codenameone.examples.hellocodenameone.tests;

import com.codename1.ui.Form;
import com.codename1.ui.PickerComponent;
import com.codename1.ui.layouts.BoxLayout;
import com.codename1.ui.spinner.Picker;
import com.codename1.ui.util.UITimer;
import com.codename1.ui.validation.Constraint;
import com.codename1.ui.validation.Validator;

import java.util.Date;

public class ValidatorLightweightPickerScreenshotTest extends BaseTest {
    private Picker picker;

    @Override
    public boolean runTest() {
        Form form = createForm("Validator + Picker", BoxLayout.y(), "ValidatorLightweightPicker");

        PickerComponent birthDate = PickerComponent.createDate(new Date()).label("Birthdate");
        picker = birthDate.getPicker();
        picker.setUseLightweightPopup(true);

        Validator validator = new Validator();
        validator.setValidationFailureHighlightMode(Validator.HighlightMode.EMBLEM);
        validator.setValidationEmblemPositionX(0f);
        validator.addConstraint(birthDate, new Constraint() {
            @Override
            public boolean isValid(Object value) {
                return false;
            }

            @Override
            public String getDefaultFailMessage() {
                return "Validation marker should not overlap spinner";
            }
        });

        form.add(birthDate);
        form.show();
        return true;
    }

    @Override
    protected void registerReadyCallback(Form parent, Runnable run) {
        picker.startEditingAsync();
        UITimer.timer(1000, false, parent, run);
    }
}
