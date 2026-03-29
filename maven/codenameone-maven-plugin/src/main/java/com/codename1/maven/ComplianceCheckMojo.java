package com.codename1.maven;

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * @deprecated Use {@link BytecodeComplianceMojo}. This goal is kept as a backward-compatible alias.
 */
@Deprecated
@Mojo(name = "compliance-check", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.TEST)
public class ComplianceCheckMojo extends BytecodeComplianceMojo {
}
