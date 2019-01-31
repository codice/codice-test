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
package org.codice.dominion.pax.exam.options.extensions;

import org.codice.dominion.options.Options;
import org.codice.dominion.options.Options.Install;
import org.codice.dominion.options.Options.MavenUrl;
import org.codice.dominion.options.Options.UpdateConfigFile;
import org.codice.dominion.options.karaf.KarafOptions;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link Install} option annotation. */
// Required so pax-exam can include it's own pax-exam related artifacts during test runtime
@UpdateConfigFile(
  target = KarafOptions.PAX_URL_MVN_CFG,
  key = "org.ops4j.pax.url.mvn.repositories",
  value =
      "http://repo1.maven.org/maven2@id=central,"
          + "http://oss.sonatype.org/content/repositories/snapshots@snapshots@noreleases@id=sonatype-snapshot,"
          + "http://oss.sonatype.org/content/repositories/ops4j-snapshots@snapshots@noreleases@id=ops4j-snapshot,"
          + "http://repository.apache.org/content/groups/snapshots-group@snapshots@noreleases@id=apache,"
          + "http://svn.apache.org/repos/asf/servicemix/m2-repo@id=servicemix,"
          + "http://repository.springsource.com/maven/bundles/release@id=springsource,"
          + "http://repository.springsource.com/maven/bundles/external@id=springsourceext,"
          + "http://oss.sonatype.org/content/repositories/releases/@id=sonatype"
)
@KarafOptions.PropagateOverriddenMavenLocalRepo
@KarafOptions.Feature(
  repository =
      @MavenUrl(
        groupId = Options.MavenUrl.AS_PROJECT,
        artifactId = "dominion-pax-exam-feature",
        version = Options.MavenUrl.AS_PROJECT,
        type = "xml",
        classifier = "features"
      ),
  names = "dominion-pax-exam"
)
@Options.SetSystemProperty(key = "pax.exam.invoker", value = "junit")
public class InstallExtension implements Extension<Install> {
  @Override
  public Option[] options(Install annotation, Class<?> testClass, ResourceLoader resourceLoader) {
    return new Option[0];
  }
}
