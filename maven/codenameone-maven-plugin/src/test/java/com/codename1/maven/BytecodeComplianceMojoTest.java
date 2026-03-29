package com.codename1.maven;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
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
        Path runtimeDir = tempDir.resolve("runtime");
        Files.createDirectories(runtimeDir);
        writeJavaLangObject(runtimeDir);

        BytecodeComplianceMojo mojo = new BytecodeComplianceMojo();
        Map<String, ?> runtimeIndex = buildClassIndex(mojo, Collections.singletonList(runtimeDir.toFile()));
        List<?> violations = scanProjectClasses(mojo, outputDir, runtimeIndex, Collections.<String, Object>emptyMap());

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
        writeJavaLangObject(allowedDir);

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
        writeJavaLangObject(dependencyDir);

        BytecodeComplianceMojo mojo = new BytecodeComplianceMojo();
        Map<String, ?> dependencyIndex = buildClassIndex(mojo, Collections.singletonList(dependencyDir.toFile()));

        List<?> violations = scanProjectClasses(mojo, outputDir, Collections.<String, Object>emptyMap(), dependencyIndex);
        assertTrue(violations.isEmpty(), "Expected no violations when method exists in project/dependency index");
    }


    @Test
    void allowsInheritedMethodAcrossProjectAndAllowedIndexes(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("classes");
        Path allowedDir = tempDir.resolve("allowed");
        Path dependencyDir = tempDir.resolve("dependency");
        Files.createDirectories(outputDir);
        Files.createDirectories(allowedDir);
        Files.createDirectories(dependencyDir);

        writeClass(outputDir, "app/Caller", "dep/Sub", "inherited", "()V");
        writeApiClass(allowedDir, "allowed/Base", "inherited", "()V");
        writeJavaLangObject(allowedDir);
        writeSubclass(dependencyDir, "dep/Sub", "allowed/Base");

        BytecodeComplianceMojo mojo = new BytecodeComplianceMojo();
        Map<String, ?> allowedIndex = buildClassIndex(mojo, Collections.singletonList(allowedDir.toFile()));
        Map<String, ?> dependencyIndex = buildClassIndex(mojo, Collections.singletonList(dependencyDir.toFile()));

        List<?> violations = scanProjectClasses(mojo, outputDir, allowedIndex, dependencyIndex);
        assertTrue(violations.isEmpty(), "Expected no violations when owner inherits allowed member through superclass in allowed index");
    }


    @Test
    void rewritesClassMajorVersionAboveJava17(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("classes");
        Files.createDirectories(outputDir);
        Path classFile = writeClassWithVersion(outputDir, "app/TooNew", Opcodes.V17 + 1);

        BytecodeComplianceMojo mojo = new BytecodeComplianceMojo();
        int rewritten = enforceMaxClassVersion(mojo, outputDir.toFile(), Opcodes.V17);

        assertEquals(1, rewritten, "Expected one class to be rewritten");
        assertEquals(Opcodes.V17, readMajorVersion(classFile), "Expected rewritten class major version to be Java 17 (61)");
    }

    @Test
    void rewritesStringSplitAndFormatInvocations(@TempDir Path tempDir) throws Exception {
        Path outputDir = tempDir.resolve("classes");
        Files.createDirectories(outputDir);
        Path classFile = writeStringApiUsageClass(outputDir, "app/StringApiUser");

        BytecodeComplianceMojo mojo = new BytecodeComplianceMojo();
        applyInvocationRewrites(mojo, outputDir.toFile());

        byte[] rewritten = Files.readAllBytes(classFile);
        assertTrue(containsMethodInsn(rewritten, "com/codename1/impl/JdkApiRewriteHelper", "split", "(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/String;", Opcodes.INVOKESTATIC));
        assertTrue(containsMethodInsn(rewritten, "com/codename1/impl/JdkApiRewriteHelper", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", Opcodes.INVOKESTATIC));
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


    private int enforceMaxClassVersion(BytecodeComplianceMojo mojo, java.io.File outputDir, int maxVersion) throws Exception {
        Method method = BytecodeComplianceMojo.class.getDeclaredMethod("enforceMaxClassVersion", java.io.File.class, int.class);
        method.setAccessible(true);
        return ((Integer) method.invoke(mojo, outputDir, maxVersion)).intValue();
    }

    private void applyInvocationRewrites(BytecodeComplianceMojo mojo, java.io.File outputDir) throws Exception {
        Method method = BytecodeComplianceMojo.class.getDeclaredMethod("applyInvocationRewrites", java.io.File.class);
        method.setAccessible(true);
        method.invoke(mojo, outputDir);
    }

    private int readMajorVersion(Path classFile) throws Exception {
        byte[] bytes = Files.readAllBytes(classFile);
        return ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
    }

    private Path writeClassWithVersion(Path root, String className, int version) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(version, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        writer.visitEnd();
        Path classFile = root.resolve(className + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, writer.toByteArray());
        return classFile;
    }

    private Path writeStringApiUsageClass(Path root, String className) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

        MethodVisitor run = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "run", "()V", null, null);
        run.visitCode();
        run.visitLdcInsn("a,b");
        run.visitLdcInsn(",");
        run.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "split", "(Ljava/lang/String;)[Ljava/lang/String;", false);
        run.visitInsn(Opcodes.POP);
        run.visitLdcInsn("Hi %s");
        run.visitInsn(Opcodes.ICONST_1);
        run.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        run.visitInsn(Opcodes.DUP);
        run.visitInsn(Opcodes.ICONST_0);
        run.visitLdcInsn("CN1");
        run.visitInsn(Opcodes.AASTORE);
        run.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "format", "(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;", false);
        run.visitInsn(Opcodes.POP);
        run.visitInsn(Opcodes.RETURN);
        run.visitMaxs(5, 0);
        run.visitEnd();

        writer.visitEnd();
        Path classFile = root.resolve(className + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, writer.toByteArray());
        return classFile;
    }

    private boolean containsMethodInsn(byte[] classBytes, final String owner, final String name, final String descriptor, final int opcode) {
        final boolean[] found = new boolean[]{false};
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String methodDescriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int seenOpcode, String seenOwner, String seenName, String seenDescriptor, boolean isInterface) {
                        if (seenOpcode == opcode && owner.equals(seenOwner) && name.equals(seenName) && descriptor.equals(seenDescriptor)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        return found[0];
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


    private void writeSubclass(Path root, String className, String superName) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, className, null, superName, null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(1, 1);
        init.visitEnd();

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


    private void writeJavaLangObject(Path root) throws Exception {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "java/lang/Object", null, null, null);

        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 1);
        init.visitEnd();

        writer.visitEnd();
        writeBytes(root, "java/lang/Object", writer.toByteArray());
    }

    private void writeBytes(Path root, String className, byte[] bytes) throws Exception {
        Path classFile = root.resolve(className + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, bytes);
    }
}
