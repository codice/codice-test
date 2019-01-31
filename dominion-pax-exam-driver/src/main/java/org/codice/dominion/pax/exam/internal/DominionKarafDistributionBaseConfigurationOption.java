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
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;

/**
 * Extension to the standard Karaf distribution base configuration which injects the container id
 * and container name as part of the unpack directory.
 */
public class DominionKarafDistributionBaseConfigurationOption
    extends KarafDistributionBaseConfigurationOption {
  private final PaxExamInterpolator interpolator;
  private final KarafDistributionBaseConfigurationOption config;

  DominionKarafDistributionBaseConfigurationOption(
      PaxExamInterpolator interpolator, KarafDistributionBaseConfigurationOption config) {
    // make sure to set all super attributes in case this option is later cloned as the base class
    // copy constructor access those directly
    super(config);
    this.interpolator = interpolator;
    this.config = config;
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

  static File resolve(PaxExamInterpolator interpolator, @Nullable File dir) {
    if (dir != null) {
      return new File(
          new File(dir.getAbsoluteFile(), interpolator.getUUID()), interpolator.getContainer());
    }
    // this is the standard default typically used by PaxExam
    // see DefaultExamSystem() and KarafTestContainer.retrieveFinalTargetFolder()
    return Paths.get(
            System.getProperty("user.home"),
            ".pax",
            "exam",
            interpolator.getUUID(),
            interpolator.getContainer())
        .toAbsolutePath()
        .toFile();
  }

  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  static String toString(
      PaxExamInterpolator interpolator,
      KarafDistributionBaseConfigurationOption thisOption,
      KarafDistributionBaseConfigurationOption config) {
    String configString;

    try {
      configString = ReflectionToStringBuilder.toString(config, null, true);
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) {
      configString = config.toString();
    }
    try {
      return new ReflectionToStringBuilder(
                  thisOption,
                  DominionKarafDistributionBaseConfigurationOption.ENHANCED_STYLE,
                  null,
                  null,
                  true,
                  false)
              .setExcludeFieldNames("interpolator", "config")
              .toString()
          + ",id="
          + interpolator.getUUID()
          + ",container="
          + interpolator.getContainer()
          + ",config="
          + configString
          + ']';
    } catch (VirtualMachineError e) {
      throw e;
    } catch (Throwable t) {
      return thisOption.getClass().getSimpleName()
          + "[id="
          + interpolator.getUUID()
          + ",container="
          + interpolator.getContainer()
          + ",config="
          + configString
          + ']';
    }
  }

  static final ToStringStyle ENHANCED_STYLE = new EnhancedToStringStyle();

  static class EnhancedToStringStyle extends ToStringStyle {

    EnhancedToStringStyle() {
      setContentEnd("");
    }

    protected Object readResolve() {
      return DominionKarafDistributionBaseConfigurationOption.ENHANCED_STYLE;
    }
  }
}
