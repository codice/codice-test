/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.dominion.pax.exam.options;

import java.lang.annotation.Annotation;
import java.util.Properties;
import org.codice.maven.MavenUrl;
import org.codice.maven.MavenUrlReference;
import org.codice.maven.ResourceLoader;
import org.codice.maven.Utilities;
import org.codice.test.commons.MavenUtils;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.options.MavenArtifactUrlReference;

/** Utility methods for defining extensions. */
public class PaxExamUtilities {
  /**
   * Gets the maven artifact url corresponding to the project associated with the specified
   * annotation and resource loader.
   *
   * <p>This method will load the dependencies.properties file that was generated and packaged by
   * the maven project where the annotation was used.
   *
   * @param annotation the annotation instance for which to retrieve the project maven url info
   * @param resourceLoader the resource loader to load the dependencies.properties file with
   * @return the corresponding maven artifact url
   */
  public static MavenArtifactUrlReference getProjectReference(
      Annotation annotation, ResourceLoader resourceLoader) {
    final Properties dependencies = Utilities.getDependencies(annotation, resourceLoader, null);

    return CoreOptions.maven()
        .groupId(
            Utilities.getProjectAttribute(
                annotation, resourceLoader, MavenUtils.GROUP_ID, dependencies))
        .artifactId(
            Utilities.getProjectAttribute(
                annotation, resourceLoader, MavenUtils.ARTIFACT_ID, dependencies))
        .version(
            Utilities.getProjectAttribute(
                annotation, resourceLoader, MavenUtils.VERSION, dependencies));
  }

  /**
   * Resolves a given maven url to a maven artifact url reference. If this one references the
   * project in anyway (i.e. {@link MavenUrl#AS_IN_PROJECT} or {@link MavenUrl#AS_PROJECT}) then the
   * information is retrieved from the specified annotation and resource loader.
   *
   * <p>This method will load the dependencies.properties file that was generated and packaged by
   * the maven project where the annotation was used.
   *
   * @param url the maven url for which to resolve the maven artifact url info
   * @param annotation the annotation instance for which to retrieve the maven url info
   * @param resourceLoader the resource loader to load the dependencies.properties file with
   * @return the corresponding maven artifact url
   */
  public static MavenArtifactUrlReference toReference(
      MavenUrl url, Annotation annotation, ResourceLoader resourceLoader) {
    final MavenUrlReference ref = MavenUrlReference.resolve(url, annotation, resourceLoader);
    final MavenArtifactUrlReference maven =
        CoreOptions.maven(ref.getGroupId(), ref.getArtifactId());

    if (ref.getVersion() != null) {
      maven.version(ref.getVersion());
    }
    if (ref.getType() != null) {
      maven.type(ref.getType());
    }
    if (ref.getClassifier() != null) {
      maven.classifier(ref.getClassifier());
    }
    return maven;
  }
}
