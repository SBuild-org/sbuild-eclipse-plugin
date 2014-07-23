package org.sbuild.eclipse.resolver;

import java.io.File;

/**
 * Abtract over some SBuild functionality.
 * <p>
 * This is a Java interface to be able to load it from multiple binary
 * incompatible Scala versions.
 *
 */
public interface SBuildResolver {

	Either<Throwable, String[]> exportedDependencies(File projectFile,
			String exportName);

	Either<Throwable, File[]> resolve(File projectFile, String dependency);

	Optional<Throwable> prepareProject(File projectFile, boolean keepFailed);

	void releaseProject(File projectFile);

}
