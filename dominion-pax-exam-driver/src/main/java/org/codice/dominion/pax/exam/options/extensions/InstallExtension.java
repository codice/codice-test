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

import java.io.FilePermission;
import java.nio.file.Paths;
import org.codice.dominion.options.Options;
import org.codice.dominion.options.Options.Install;
import org.codice.dominion.options.Permission;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.codice.maven.MavenUrl;
import org.ops4j.pax.exam.CoreOptions;
import org.ops4j.pax.exam.Option;

/** Extension point for the {@link Install} option annotation. */
// Required so pax-exam can include it's own pax-exam related artifacts during test runtime
@Options.UpdateConfigProperty(
  target = InstallExtension.PAX_URL_MVN_CFG,
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
@Options.PropagateOverriddenMavenLocalRepository
@Options.SetSystemProperty(key = "pax.exam.invoker", value = "junit")
@Options.GrantPermission(
  artifact = {
    @MavenUrl(
      groupId = MavenUrl.AS_PROJECT,
      artifactId = "dominion-pax-exam-invokers",
      version = MavenUrl.AS_PROJECT
    ),
    @MavenUrl(
      groupId = MavenUrl.AS_PROJECT,
      artifactId = "maven-extensions",
      version = MavenUrl.AS_PROJECT
    ),
    @MavenUrl(
      groupId = MavenUrl.AS_PROJECT,
      artifactId = "pax-exam-extensions",
      version = MavenUrl.AS_PROJECT
    )
  },
  codebase = {
    "PAXEXAM-PROBE",
    "org.ops4j.pax.exam.invoker.junit",
    "org.ops4j.pax.swissbox.core",
    "org.ops4j.pax.exam.rbc",
    "org.ops4j.pax.tipi.junit"
  },
  permission = {
    @Permission(clazz = RuntimePermission.class, name = "createClassLoader"),
    @Permission(
      clazz = FilePermission.class,
      name = "<<ALL FILES>>",
      actions = "read,write,delete,execute"
    )
  }
)
public class InstallExtension implements Extension<Install> {
  public static final String PAX_URL_MVN_CFG = "etc/org.ops4j.pax.url.mvn.cfg";

  @Override
  public Option[] options(
      Install annotation, PaxExamInterpolator interpolator, ResourceLoader resourceLoader) {
    final String cwd = Paths.get("").toAbsolutePath().toString();

    return new Option[] {
      // this will help using the MavenUrl annotation inside a running container which uses the
      // org.codice.test.commons.ReflectionUtils.AnnotationEntry.getEnclosingResourceAsStream
      // method to find resources directly from disk if it can find them via a class'
      // code source or classloader
      CoreOptions.systemProperty("project.basedir").value(cwd)
    };
  }
}
