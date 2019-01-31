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

import java.io.File;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionKitConfigurationOption;

/**
 * Extension to the standard Karaf distribution kit configuration which injects the container id and
 * container name as part of the unpack directory.
 */
public class DominionKarafDistributionKitConfigurationOption
    extends KarafDistributionKitConfigurationOption {
  private final PaxExamDriverInterpolator interpolator;
  private final KarafDistributionKitConfigurationOption config;

  DominionKarafDistributionKitConfigurationOption(
      PaxExamDriverInterpolator interpolator, KarafDistributionKitConfigurationOption config) {
    // make sure to set all super attributes in case this option is later cloned as the base class
    // copy constructor access those directly
    super(config, config.getPlatform());
    this.interpolator = interpolator;
    this.config = config;
    super.filesToMakeExecutable(config.getMakeExec().stream().toArray(String[]::new));
    super.executable(config.getExec());
    // make sure to resolve the unpack directory
    unpackDirectory(config.getUnpackDirectory());
  }

  @Override
  public KarafDistributionBaseConfigurationOption unpackDirectory(File unpackDirectory) {
    return super.unpackDirectory(
        DominionKarafDistributionBaseConfigurationOption.resolve(interpolator, unpackDirectory));
  }

  @Override
  public String toString() {
    return DominionKarafDistributionBaseConfigurationOption.toString(interpolator, this, config);
  }
}
