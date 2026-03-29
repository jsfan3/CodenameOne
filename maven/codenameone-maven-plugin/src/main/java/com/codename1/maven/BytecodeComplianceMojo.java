package com.codename1.maven;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.codename1.maven.PathUtil.path;

/**
 * Performs bytecode-level API compliance checks by scanning compiled classes.
 */
@Mojo(name = "bytecode-compliance", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.TEST)
public class BytecodeComplianceMojo extends AbstractCN1Mojo {

    private static final Map<String, String> SUGGESTED_REPLACEMENTS;

    static {
        Map<String, String> m = new HashMap<String, String>();
        m.put("java/lang/System#exit(I)V", "Use com.codename1.ui.CN.exitApplication() to close apps on supported targets.");
        m.put("java/lang/Thread#sleep(J)V", "Use com.codename1.ui.util.UITimer or Display.callSerially() instead of blocking sleeps.");
        m.put("java/lang/Thread#sleep(JI)V", "Use com.codename1.ui.util.UITimer or Display.callSerially() instead of blocking sleeps.");
        m.put("java/lang/Runtime#getRuntime()Ljava/lang/Runtime;", "Use Codename One platform services instead of raw java.lang.Runtime access.");
        SUGGESTED_REPLACEMENTS = Collections.unmodifiableMap(m);
    }

    private File complianceOutputFile;

    @Override
    protected void executeImpl() throws MojoExecutionException, MojoFailureException {
        if (shouldSkipComplianceCheck() || !isCN1ProjectDir()) {
            return;
        }

        complianceOutputFile = new File(path(project.getBuild().getDirectory(), "codenameone", "compliance_check.txt"));
        getLog().info("Running bytecode compliance check against Codename One Java Runtime API");
        getLog().info("See https://www.codenameone.com/javadoc/ for supported Classes and Methods");

        if (!hasChangedSinceLastCheck()) {
            getLog().info("Sources haven't changed since the last compliance check. Skipping check");
            return;
        }

        copyKotlinIncrementalCompileOutputToOutputDir();

        File outputDir = new File(project.getBuild().getOutputDirectory());
        if (!outputDir.isDirectory()) {
            writeComplianceSuccess("No output classes found for compliance check in " + outputDir.getAbsolutePath());
            return;
        }

        List<File> dependencyJars = getDependencyJarsForScanning();
        Map<String, ClassMetadata> allowedIndex = buildClassIndex(Arrays.asList(getJavaRuntimeJar(), getCodenameOneJar()));
        Map<String, ClassMetadata> projectAndDependencyIndex = buildClassIndexWithOutput(outputDir, dependencyJars);

        List<Violation> violations = scanProjectClasses(outputDir, allowedIndex, projectAndDependencyIndex);
        if (!violations.isEmpty()) {
            writeComplianceReport(violations, outputDir, dependencyJars);
            throw new MojoExecutionException(buildFailureSummary(violations));
        }

        writeComplianceSuccess("Completed compliance check on " + project.getName());
    }

    private boolean shouldSkipComplianceCheck() {
        if ("true".equals(System.getProperty("skipComplianceCheck", "false"))) {
            return true;
        }
        if ("true".equals(project.getProperties().getProperty("skipComplianceCheck", "false"))) {
            return true;
        }
        if ("true".equals(System.getProperty("reloadClasses", "false"))) {
            return true;
        }
        return "true".equals(project.getProperties().getProperty("reloadClasses", "false"));
    }

    private boolean hasChangedSinceLastCheck() {
        if (!complianceOutputFile.exists()) {
            return true;
        }
        try {
            return getSourcesModificationTime(true) > complianceOutputFile.lastModified();
        } catch (IOException ex) {
            getLog().error("Failed to check sources modification time for compliance check", ex);
            return true;
        }
    }

    private void writeComplianceSuccess(String message) throws MojoExecutionException {
        complianceOutputFile.getParentFile().mkdirs();
        try {
            FileUtils.writeStringToFile(complianceOutputFile, message, "UTF-8");
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to write compliance file", ex);
        }
    }

    private void writeComplianceReport(List<Violation> violations, File outputDir, List<File> dependencyJars) throws MojoExecutionException {
        StringBuilder report = new StringBuilder();
        report.append("Codename One compliance check failed.\n");
        report.append("Project: ").append(project.getName()).append("\n");
        report.append("Output classes: ").append(outputDir.getAbsolutePath()).append("\n");
        report.append("Dependency jars scanned: ").append(dependencyJars.size()).append("\n\n");
        report.append("Violations (").append(violations.size()).append(")\n");
        report.append("========================================\n");
        int i = 1;
        for (Violation violation : violations) {
            report.append(i++).append(") ").append(violation.render()).append("\n\n");
        }

        complianceOutputFile.getParentFile().mkdirs();
        try {
            FileUtils.writeStringToFile(complianceOutputFile, report.toString(), "UTF-8");
        } catch (IOException ex) {
            throw new MojoExecutionException("Failed to write compliance report", ex);
        }
    }

    private String buildFailureSummary(List<Violation> violations) {
        int maxInMessage = Math.min(5, violations.size());
        StringBuilder sb = new StringBuilder();
        sb.append("Compliance check failed with ").append(violations.size()).append(" forbidden API reference");
        if (violations.size() != 1) {
            sb.append("s");
        }
        sb.append(". See ").append(complianceOutputFile.getAbsolutePath()).append(" for full report.");
        sb.append(" First ").append(maxInMessage).append(" violation(s): ");
        for (int i = 0; i < maxInMessage; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            Violation v = violations.get(i);
            sb.append(v.sourceClass).append("#").append(v.sourceMethod).append(" -> ").append(v.referencedMember);
        }
        return sb.toString();
    }

    private List<Violation> scanProjectClasses(File outputDir, final Map<String, ClassMetadata> allowedIndex, final Map<String, ClassMetadata> projectAndDependencyIndex) throws MojoExecutionException {
        List<File> classFiles = new ArrayList<File>();
        collectClassFiles(outputDir, classFiles);
        List<Violation> violations = new ArrayList<Violation>();
        for (File classFile : classFiles) {
            try {
                InputStream inputStream = new BufferedInputStream(new FileInputStream(classFile));
                try {
                    ClassReader reader = new ClassReader(inputStream);
                    reader.accept(new ComplianceScanner(classFile, outputDir, allowedIndex, projectAndDependencyIndex, violations), ClassReader.SKIP_FRAMES);
                } finally {
                    inputStream.close();
                }
            } catch (IOException ex) {
                throw new MojoExecutionException("Failed to scan class " + classFile, ex);
            }
        }
        return violations;
    }

    private void collectClassFiles(File file, List<File> out) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isFile() && file.getName().endsWith(".class")) {
            out.add(file);
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                return;
            }
            for (File child : children) {
                collectClassFiles(child, out);
            }
        }
    }

    private Map<String, ClassMetadata> buildClassIndexWithOutput(File outputDir, List<File> dependencyJars) throws MojoExecutionException {
        Map<String, ClassMetadata> index = buildClassIndex(Collections.singletonList(outputDir));
        Map<String, ClassMetadata> dependencyIndex = buildClassIndex(dependencyJars);
        index.putAll(dependencyIndex);
        return index;
    }

    private Map<String, ClassMetadata> buildClassIndex(List<File> roots) throws MojoExecutionException {
        Map<String, ClassMetadata> index = new HashMap<String, ClassMetadata>();
        for (File root : roots) {
            if (root == null || !root.exists()) {
                continue;
            }
            if (root.isDirectory()) {
                List<File> classFiles = new ArrayList<File>();
                collectClassFiles(root, classFiles);
                for (File classFile : classFiles) {
                    try {
                        InputStream inputStream = new BufferedInputStream(new FileInputStream(classFile));
                        try {
                            ClassMetadata metadata = readClassMetadata(inputStream);
                            index.put(metadata.name, metadata);
                        } finally {
                            inputStream.close();
                        }
                    } catch (IOException ex) {
                        throw new MojoExecutionException("Failed reading class metadata from " + classFile, ex);
                    }
                }
            } else if (root.getName().endsWith(".jar")) {
                JarFile jarFile = null;
                try {
                    jarFile = new JarFile(root);
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                            continue;
                        }
                        InputStream inputStream = jarFile.getInputStream(entry);
                        try {
                            ClassMetadata metadata = readClassMetadata(inputStream);
                            index.put(metadata.name, metadata);
                        } finally {
                            inputStream.close();
                        }
                    }
                } catch (IOException ex) {
                    throw new MojoExecutionException("Failed reading jar metadata from " + root, ex);
                } finally {
                    if (jarFile != null) {
                        try {
                            jarFile.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }
        return index;
    }

    private ClassMetadata readClassMetadata(InputStream inputStream) throws IOException {
        final ClassMetadata metadata = new ClassMetadata();
        ClassReader reader = new ClassReader(inputStream);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                metadata.name = name;
                metadata.superName = superName;
                metadata.interfaces = interfaces == null ? Collections.<String>emptyList() : Arrays.asList(interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                metadata.fields.add(memberKey(name, descriptor));
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                metadata.methods.add(memberKey(name, descriptor));
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return metadata;
    }

    private List<File> getDependencyJarsForScanning() {
        List<File> jars = new ArrayList<File>();
        for (Artifact artifact : project.getArtifacts()) {
            if (artifact == null || artifact.getScope() == null) {
                continue;
            }
            if (artifact.getGroupId().equals("com.codenameone") && artifact.getArtifactId().equals("codenameone-core")) {
                continue;
            }
            if (artifact.getGroupId().equals("com.codenameone") && artifact.getArtifactId().equals("java-runtime")) {
                continue;
            }
            if ("compile".equals(artifact.getScope()) || "system".equals(artifact.getScope()) || "test".equals(artifact.getScope())) {
                File jar = getJar(artifact);
                if (jar != null && jar.exists() && jar.getName().endsWith(".jar")) {
                    jars.add(jar);
                }
            }
        }
        return jars;
    }

    private File getJavaRuntimeJar() {
        for (Artifact artifact : project.getArtifacts()) {
            if (JAVA_RUNTIME_ARTIFACT_ID.equals(artifact.getArtifactId()) && GROUP_ID.equals(artifact.getGroupId())) {
                return getJar(artifact);
            }
        }
        for (Artifact artifact : pluginArtifacts) {
            if (JAVA_RUNTIME_ARTIFACT_ID.equals(artifact.getArtifactId()) && GROUP_ID.equals(artifact.getGroupId())) {
                return getJar(artifact);
            }
        }
        throw new RuntimeException(JAVA_RUNTIME_ARTIFACT_ID + " not found in dependencies");
    }

    private File getCodenameOneJar() {
        String codenameOneCoreId = "codenameone-core";
        for (Artifact artifact : project.getArtifacts()) {
            if (codenameOneCoreId.equals(artifact.getArtifactId()) && GROUP_ID.equals(artifact.getGroupId())) {
                return getJar(artifact);
            }
        }
        for (Artifact artifact : pluginArtifacts) {
            if (codenameOneCoreId.equals(artifact.getArtifactId()) && GROUP_ID.equals(artifact.getGroupId())) {
                return getJar(artifact);
            }
        }
        throw new RuntimeException(codenameOneCoreId + " not found in dependencies");
    }

    private static String memberKey(String name, String descriptor) {
        return name + descriptor;
    }

    private static final class ClassMetadata {
        String name;
        String superName;
        List<String> interfaces = Collections.emptyList();
        Set<String> methods = new HashSet<String>();
        Set<String> fields = new HashSet<String>();
    }

    private final class ComplianceScanner extends ClassVisitor {
        private final File classFile;
        private final File outputDir;
        private final Map<String, ClassMetadata> allowedIndex;
        private final Map<String, ClassMetadata> projectAndDependencyIndex;
        private final List<Violation> violations;
        private String className;

        private ComplianceScanner(File classFile,
                                  File outputDir,
                                  Map<String, ClassMetadata> allowedIndex,
                                  Map<String, ClassMetadata> projectAndDependencyIndex,
                                  List<Violation> violations) {
            super(Opcodes.ASM9);
            this.classFile = classFile;
            this.outputDir = outputDir;
            this.allowedIndex = allowedIndex;
            this.projectAndDependencyIndex = projectAndDependencyIndex;
            this.violations = violations;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            className = name;
        }

        @Override
        public MethodVisitor visitMethod(int access, final String name, final String descriptor, String signature, String[] exceptions) {
            final String sourceMethod = name + descriptor;
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String memberName, String memberDescriptor, boolean isInterface) {
                    checkMethodReference(className, sourceMethod, owner, memberName, memberDescriptor);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String memberName, String memberDescriptor) {
                    checkFieldReference(className, sourceMethod, owner, memberName, memberDescriptor);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    checkTypeReference(className, sourceMethod, type);
                }
            };
        }

        private void checkMethodReference(String sourceClass, String sourceMethod, String owner, String memberName, String memberDescriptor) {
            if (shouldAllowMethod(owner, memberName, memberDescriptor)) {
                return;
            }
            addViolation(sourceClass, sourceMethod, owner + "#" + memberName + memberDescriptor);
        }

        private void checkFieldReference(String sourceClass, String sourceMethod, String owner, String memberName, String memberDescriptor) {
            if (shouldAllowField(owner, memberName, memberDescriptor)) {
                return;
            }
            addViolation(sourceClass, sourceMethod, owner + "#" + memberName + ":" + memberDescriptor);
        }

        private void checkTypeReference(String sourceClass, String sourceMethod, String owner) {
            if (isArrayDescriptor(owner)) {
                return;
            }
            if (projectAndDependencyIndex.containsKey(owner) || allowedIndex.containsKey(owner)) {
                return;
            }
            addViolation(sourceClass, sourceMethod, owner + " (type)");
        }

        private void addViolation(String sourceClass, String sourceMethod, String referencedMember) {
            String relativePath = classFile.getAbsolutePath().replace(outputDir.getAbsolutePath(), "");
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }
            violations.add(new Violation(sourceClass, sourceMethod, referencedMember, replacementFor(referencedMember), relativePath));
        }

        private boolean shouldAllowMethod(String owner, String name, String descriptor) {
            if (isArrayDescriptor(owner)) {
                return true;
            }
            if (resolveMember(owner, memberKey(name, descriptor), true, projectAndDependencyIndex)) {
                return true;
            }
            return resolveMember(owner, memberKey(name, descriptor), true, allowedIndex);
        }

        private boolean shouldAllowField(String owner, String name, String descriptor) {
            if (isArrayDescriptor(owner)) {
                return true;
            }
            if (resolveMember(owner, memberKey(name, descriptor), false, projectAndDependencyIndex)) {
                return true;
            }
            return resolveMember(owner, memberKey(name, descriptor), false, allowedIndex);
        }

        private boolean resolveMember(String owner, String member, boolean method, Map<String, ClassMetadata> index) {
            if (owner == null || owner.isEmpty()) {
                return false;
            }
            Deque<String> queue = new ArrayDeque<String>();
            Set<String> seen = new HashSet<String>();
            queue.add(owner);
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                if (!seen.add(current)) {
                    continue;
                }
                ClassMetadata metadata = index.get(current);
                if (metadata == null) {
                    continue;
                }
                Set<String> members = method ? metadata.methods : metadata.fields;
                if (members.contains(member)) {
                    return true;
                }
                if (metadata.superName != null) {
                    queue.add(metadata.superName);
                }
                for (String iface : metadata.interfaces) {
                    queue.add(iface);
                }
            }
            return false;
        }
    }

    private static boolean isArrayDescriptor(String type) {
        return type != null && type.startsWith("[");
    }

    private static String replacementFor(String referencedMember) {
        String direct = SUGGESTED_REPLACEMENTS.get(referencedMember);
        if (direct != null) {
            return direct;
        }
        int hashPos = referencedMember.indexOf('#');
        if (hashPos > 0) {
            String ownerOnly = referencedMember.substring(0, hashPos);
            if (ownerOnly.startsWith("java/awt/") || ownerOnly.startsWith("javax/swing/")) {
                return "Codename One does not support AWT/Swing APIs. Use com.codename1.ui components for UI logic.";
            }
        }
        return null;
    }

    private static final class Violation {
        private final String sourceClass;
        private final String sourceMethod;
        private final String referencedMember;
        private final String suggestion;
        private final String sourcePath;

        private Violation(String sourceClass, String sourceMethod, String referencedMember, String suggestion, String sourcePath) {
            this.sourceClass = sourceClass;
            this.sourceMethod = sourceMethod;
            this.referencedMember = referencedMember;
            this.suggestion = suggestion;
            this.sourcePath = sourcePath;
        }

        private String render() {
            StringBuilder sb = new StringBuilder();
            sb.append("Source class: ").append(sourceClass).append("\n");
            sb.append("Source method: ").append(sourceMethod).append("\n");
            sb.append("Source bytecode file: ").append(sourcePath).append("\n");
            sb.append("Forbidden reference: ").append(referencedMember);
            if (suggestion != null && !suggestion.isEmpty()) {
                sb.append("\nSuggested replacement: ").append(suggestion);
            }
            return sb.toString();
        }
    }
}
