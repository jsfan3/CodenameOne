package com.codename1.impl.javase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LocationSimulationTest {

    @Test
    public void testIsJcefPresentFalseWhenClassMissing() {
        ClassLoader noJcefLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if ("org.cef.CefApp".equals(name)) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name);
            }
        };

        Assertions.assertFalse(LocationSimulation.isJcefPresent(noJcefLoader));
    }

    @Test
    public void testIsJcefPresentTrueWhenClassExists() {
        ClassLoader withJcefLoader = new ClassLoader(null) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if ("org.cef.CefApp".equals(name)) {
                    return Object.class;
                }
                throw new ClassNotFoundException(name);
            }
        };

        Assertions.assertTrue(LocationSimulation.isJcefPresent(withJcefLoader));
    }

    @Test
    public void testLocationSimulationHasNoJavaFxImports() throws Exception {
        File src = new File("../../Ports/JavaSE/src/com/codename1/impl/javase/LocationSimulation.java");
        Assertions.assertTrue(src.exists(), "Could not find LocationSimulation source file");

        StringBuilder sb = new StringBuilder();
        BufferedReader br = new BufferedReader(new FileReader(src));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            br.close();
        }

        Assertions.assertFalse(sb.toString().contains("javafx."));
    }
}
