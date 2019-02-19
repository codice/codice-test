package org.codice.dominion.pax.exam.internal;

import java.io.File;
import java.nio.file.Paths;
import javax.annotation.Nullable;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;

/** Collection of utilities for implementing distribution configuration options. */
class DistributionConfigurationUtils {
  public static final ToStringStyle ENHANCED_STYLE = new EnhancedToStringStyle();

  /**
   * Resolves the test specific location against the specified directory.
   *
   * @param interpolator the interpolator from which to retrieve the test id and container name
   * @param dir the directory from which to resolve
   * @return the absolute location of the resolved directory
   */
  public static File resolve(PaxExamInterpolator interpolator, @Nullable File dir) {
    if (dir != null) {
      return new File(
          new File(dir.getAbsoluteFile(), interpolator.getId()), interpolator.getContainer());
    }
    // this is the standard default typically used by PaxExam
    // see DefaultExamSystem() and KarafTestContainer.retrieveFinalTargetFolder()
    return Paths.get(
            System.getProperty("user.home"),
            ".pax",
            "exam",
            interpolator.getId(),
            interpolator.getContainer())
        .toAbsolutePath()
        .toFile();
  }

  /**
   * Builds a generic <code>toString()</code> representation of the specified distribution
   * configuration option which proxies a given one using reflection.
   *
   * @param interpolator the interpolator currently being used
   * @param thisOption the current distribution config option
   * @param config the proxied distribution config option
   * @return the corresponding generic string representation
   */
  @SuppressWarnings("squid:S1181" /* catching VirtualMachineError first */)
  public static String toString(
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
                  DistributionConfigurationUtils.ENHANCED_STYLE,
                  null,
                  null,
                  true,
                  false)
              .setExcludeFieldNames("interpolator", "config")
              .toString()
          + ",id="
          + interpolator.getId()
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
          + interpolator.getId()
          + ",container="
          + interpolator.getContainer()
          + ",config="
          + configString
          + ']';
    }
  }

  /** Special version of a to-string style which doesn't output the end separator. */
  static class EnhancedToStringStyle extends ToStringStyle {

    EnhancedToStringStyle() {
      setContentEnd("");
    }

    protected Object readResolve() {
      return DistributionConfigurationUtils.ENHANCED_STYLE;
    }
  }

  private DistributionConfigurationUtils() {}
}
