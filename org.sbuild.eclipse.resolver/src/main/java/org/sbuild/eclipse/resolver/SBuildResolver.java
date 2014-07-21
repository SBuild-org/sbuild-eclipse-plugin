package org.sbuild.eclipse.resolver;

import java.io.File;
import java.util.List;

/**
 * Abtract over some SBuild functionality.
 * <p>
 * This is a Java interface to be able to load it from multiple binary
 * incompatible Scala versions.
 *
 */
public interface SBuildResolver {

	Either<Throwable, List<String>> exportedDependencies(File projectFile,
			String exportName);

	Either<Throwable, List<File>> resolve(File projectFile, String dependency);

}
