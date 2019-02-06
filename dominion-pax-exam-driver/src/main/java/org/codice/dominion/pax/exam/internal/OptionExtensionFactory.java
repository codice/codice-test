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
package org.codice.dominion.pax.exam.internal;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.codice.dominion.options.Option;
import org.codice.dominion.options.Options;
import org.codice.dominion.options.Options.DeleteRuntimeFolder;
import org.codice.dominion.options.Options.EnableRemoteDebugging;
import org.codice.dominion.options.Options.KeepRuntimeFolder;
import org.codice.dominion.options.Options.ReplaceFile;
import org.codice.dominion.options.Options.UpdateConfigFile;
import org.codice.dominion.options.Options.UpdateFile;
import org.codice.dominion.options.karaf.KarafOptions;
import org.codice.dominion.pax.exam.options.extensions.CleanCachesExtension;
import org.codice.dominion.pax.exam.options.extensions.DeleteRuntimeFolderExtension;
import org.codice.dominion.pax.exam.options.extensions.EnableRemoteDebuggingExtension;
import org.codice.dominion.pax.exam.options.extensions.EnvironmentExtension;
import org.codice.dominion.pax.exam.options.extensions.InstallExtension;
import org.codice.dominion.pax.exam.options.extensions.KeepCachesExtension;
import org.codice.dominion.pax.exam.options.extensions.KeepRuntimeFolderExtension;
import org.codice.dominion.pax.exam.options.extensions.PropagateSystemPropertyExtension;
import org.codice.dominion.pax.exam.options.extensions.ReplaceFileExtension;
import org.codice.dominion.pax.exam.options.extensions.SetFrameworkPropertyExtension;
import org.codice.dominion.pax.exam.options.extensions.SetLogLevelExtension;
import org.codice.dominion.pax.exam.options.extensions.SetLogLevelsExtension;
import org.codice.dominion.pax.exam.options.extensions.SetRootLogLevelExtension;
import org.codice.dominion.pax.exam.options.extensions.SetSystemPropertyExtension;
import org.codice.dominion.pax.exam.options.extensions.SetSystemTimeoutExtension;
import org.codice.dominion.pax.exam.options.extensions.UpdateConfigFileExtension;
import org.codice.dominion.pax.exam.options.extensions.UpdateFileExtension;
import org.codice.dominion.pax.exam.options.extensions.VMOptionExtension;
import org.codice.dominion.pax.exam.options.karaf.extensions.DistributionConfigurationExtension;
import org.codice.dominion.pax.exam.options.karaf.extensions.FeatureExtension;

/** Factory implementation for PaxExam option extensions. */
public class OptionExtensionFactory implements Option.Factory {
  private static final Map<Class<? extends Annotation>, Option.Extension> EXTENSIONS;

  static {
    final Map<Class<? extends Annotation>, Option.Extension> map = new HashMap<>(32);

    map.put(Options.CleanCaches.class, new CleanCachesExtension());
    map.put(Options.Environment.class, new EnvironmentExtension());
    map.put(Options.Install.class, new InstallExtension());
    map.put(Options.KeepCaches.class, new KeepCachesExtension());
    map.put(Options.PropagateSystemProperty.class, new PropagateSystemPropertyExtension());
    map.put(Options.SetFrameworkProperty.class, new SetFrameworkPropertyExtension());
    map.put(Options.SetLogLevel.class, new SetLogLevelExtension());
    map.put(Options.SetLogLevels.class, new SetLogLevelsExtension());
    map.put(Options.SetRootLogLevel.class, new SetRootLogLevelExtension());
    map.put(Options.SetSystemProperty.class, new SetSystemPropertyExtension());
    map.put(Options.SetSystemTimeout.class, new SetSystemTimeoutExtension());
    map.put(Options.VMOption.class, new VMOptionExtension());
    map.put(DeleteRuntimeFolder.class, new DeleteRuntimeFolderExtension());
    map.put(KarafOptions.DistributionConfiguration.class, new DistributionConfigurationExtension());
    map.put(KarafOptions.Feature.class, new FeatureExtension());
    map.put(KeepRuntimeFolder.class, new KeepRuntimeFolderExtension());
    map.put(ReplaceFile.class, new ReplaceFileExtension());
    map.put(UpdateFile.class, new UpdateFileExtension());
    map.put(
        KarafOptions.SetLogLevel.class,
        new org.codice.dominion.pax.exam.options.karaf.extensions.SetLogLevelExtension());
    map.put(UpdateConfigFile.class, new UpdateConfigFileExtension());
    map.put(EnableRemoteDebugging.class, new EnableRemoteDebuggingExtension());
    EXTENSIONS = Collections.unmodifiableMap(map);
  }

  @Nullable
  @Override
  public Option.Extension getExtension(java.lang.annotation.Annotation annotation) {
    return OptionExtensionFactory.EXTENSIONS.get(annotation.annotationType());
  }
}
