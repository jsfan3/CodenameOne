package com.codename1.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BytecodeComplianceMojoTest {

    @Test
    void detectsForbiddenMethodReferenceWithSourceDetails(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("classes");
        Files.createDirectories(outputDir);

        writeClass(outputDir, "app/Caller", "forbidden/Api", "m", "()V");

        BytecodeComplianceMojo mojo = new BytecodeComplianceMojo();
        List<?> violations = scanProjectClasses(mojo, outputDir, Collections.<String, Object>emptyMap(), Collections.<String, Object>emptyMap());

        assertEquals(1, violations.size());
        Object violation = violations.get(0);
        assertEquals("app/Caller", field(violation, "sourceClass"));
        assertEquals("run()V", field(violation, "sourceMethod"));
        assertEquals("forbidden/Api#m()V", field(violation, "referencedMember"));
    }

    @Test
    void allowsMethodReferenceWhenPresentInAllowedApiIndex(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("classes");
        Path allowedDir = tempDir.resolve("allowed");
        Files.createDirectories(outputDir);
        Files.createDirectories(allowedDir);

        writeClass(outputDir, "app/Caller", "allowed/Api", "m", "()V");
        writeApiClass(allowedDir, "allowed/Api", "m", "()V");

        BytecodeComplianceMojo mojo = new BytecodeComplianceMojo();
        Map<String, ?> allowedIndex = buildClassIndex(mojo, Collections.singletonList(allowedDir.toFile()));

        List<?> violations = scanProjectClasses(mojo, outputDir, allowedIndex, Collections.<String, Object>emptyMap());
        assertTrue(violations.isEmpty(), "Expected no violations when method exists in allowed API index");
    }

    @Test
    void allowsMethodReferenceWhenPresentInProjectDependencyIndex(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("classes");
        Path dependencyDir = tempDir.resolve("dependency");
        Files.createDirectories(outputDir);
        Files.createDirectories(dependencyDir);

        writeClass(outputDir, "app/Caller", "dep/Helper", "ok", "()V");
        writeApiClass(dependencyDir, "dep/Helper", "ok", "()V");

        BytecodeComplianceMojo mojo = new BytecodeComplianceMojo();
        Map<String, ?> dependencyIndex = buildClassIndex(mojo, Collections.singletonList(dependencyDir.toFile()));

        List<?> violations = scanProjectClasses(mojo, outputDir, Collections.<String, Object>emptyMap(), dependencyIndex);
        assertTrue(violations.isEmpty(), "Expected no violations when method exists in project/dependency index");
    }

    @SuppressWarnings("unchecked")
    private Map<String, ?> buildClassIndex(BytecodeComplianceMojo mojo, List<java.io.File> roots) throws Exception {
        Method method = BytecodeComplianceMojo.class.getDeclaredMethod("buildClassIndex", List.class);
        method.setAccessible(true);
        return (Map<String, ?>) method.invoke(mojo, roots);
    }

    @SuppressWarnings("unchecked")
    private List<?> scanProjectClasses(BytecodeComplianceMojo mojo, Path outputDir, Map<String, ?> allowedIndex, Map<String, ?> projectAndDependencyIndex) throws Exception {
        Method method = BytecodeComplianceMojo.class.getDeclaredMethod("scanProjectClasses", java.io.File.class, Map.class, Map.class);
        method.setAccessible(true);
        return (List<?>) method.invoke(mojo, outputDir.toFile(), allowedIndex, projectAndDependencyIndex);
    }

    private Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private void writeClass(Path root, String className, String owner, String methodName, String descriptor) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor run = writer.visitMethod(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        run.visitCode();
        run.visitMethodInsn(Opcodes.INVOKESTATIC, owner, methodName, descriptor, false);
        run.visitInsn(Opcodes.RETURN);
        run.visitMaxs(0, 1);
        run.visitEnd();

        writer.visitEnd();
        writeBytes(root, className, writer.toByteArray());
    }

    private void writeApiClass(Path root, String className, String methodName, String descriptor) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor api = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, methodName, descriptor, null, null);
        api.visitCode();
        api.visitInsn(Opcodes.RETURN);
        api.visitMaxs(0, 0);
        api.visitEnd();

        writer.visitEnd();
        writeBytes(root, className, writer.toByteArray());
    }

    private void writeBytes(Path root, String className, byte[] bytes) throws Exception {
        Path classFile = root.resolve(className + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, bytes);
    }
}
