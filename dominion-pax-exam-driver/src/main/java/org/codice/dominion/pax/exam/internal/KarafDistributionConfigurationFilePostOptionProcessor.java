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
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.codice.dominion.pax.exam.internal.DominionConfigurationFactory.AnnotationOptions;
import org.codice.dominion.pax.exam.options.KarafDistributionConfigurationFilePostOption;
import org.codice.dominion.pax.exam.options.KarafDistributionConfigurationFileRemoveOption;
import org.codice.dominion.pax.exam.options.KarafDistributionConfigurationFileRetractOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class used to process configured {@link KarafDistributionConfigurationFileRetractOption}s. */
public class KarafDistributionConfigurationFilePostOptionProcessor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(KarafDistributionConfigurationFilePostOptionProcessor.class);

  private final AnnotationOptions options;

  private final KarafDistributionBaseConfigurationOption distribution;

  private final PaxExamDriverInterpolator interpolator;

  public KarafDistributionConfigurationFilePostOptionProcessor(AnnotationOptions options) {
    this.options = options;
    this.distribution = options.getDistribution();
    this.interpolator = options.getInterpolator();
  }

  /**
   * Processes config file retract options.
   *
   * @throws IOException if an I/O error occurs while processing the options
   */
  public void process() throws IOException {
    LOGGER.debug("{}::process()", this);
    final Map<String, List<KarafDistributionConfigurationFilePostOption>> options =
        this.options
            .options(KarafDistributionConfigurationFilePostOption.class)
            .collect(
                Collectors.groupingBy(
                    KarafDistributionConfigurationFilePostOption::getConfigurationFilePath));
    // see KarafTestContainer.updateUserSetProperties() for logic on how to find the location of a
    // config file
    final File karafHome = interpolator.getKarafHome().toFile();
    final String karafData = distribution.getKarafData();
    final String karafEtc = distribution.getKarafEtc();

    for (final Map.Entry<String, List<KarafDistributionConfigurationFilePostOption>> e :
        options.entrySet()) {
      final String configFile = e.getKey();
      final List<KarafDistributionConfigurationFilePostOption> optionsToApply = e.getValue();
      final DominionKarafConfigurationFile karafConfigFile =
          KarafDistributionConfigurationFilePostOptionProcessor.getConfigFile(
              configFile, karafHome, karafData, karafEtc);
      boolean store = false;

      karafConfigFile.load();
      for (final KarafDistributionConfigurationFilePostOption optionToApply : optionsToApply) {
        if (optionToApply instanceof KarafDistributionConfigurationFileRetractOption) {
          if (karafConfigFile.retract(
              optionToApply.getKey(),
              ((KarafDistributionConfigurationFileRetractOption) optionToApply).getValue())) {
            store = true;
          }
        } else if (optionToApply instanceof KarafDistributionConfigurationFileRemoveOption) {
          if (karafConfigFile.remove(optionToApply.getKey())) {
            store = true;
          }
        }
      }
      if (store) {
        karafConfigFile.store();
      }
    }
  }

  private static DominionKarafConfigurationFile getConfigFile(
      String configFile, File karafHome, String karafData, String karafEtc) {
    final DominionKarafConfigurationFile karafConfigFile =
        DominionKarafConfigurationFileFactory.create(karafHome, configFile);

    if (!karafConfigFile.exists()) {
      // some property options will come from Pax-Exam and use the default data/etc locations,
      // in those cases when the property file doesn't exist and we have custom data/etc paths
      // we need to consider the custom location and use that - but only if it matches+exists
      DominionKarafConfigurationFile customConfigFile = null;

      if (configFile.startsWith("data/") && !configFile.startsWith(karafData)) {
        customConfigFile =
            DominionKarafConfigurationFileFactory.create(
                karafHome, karafData + configFile.substring(4));
      } else if (configFile.startsWith("etc/") && !configFile.startsWith(karafEtc)) {
        customConfigFile =
            DominionKarafConfigurationFileFactory.create(
                karafHome, karafEtc + configFile.substring(3));
      }
      if ((customConfigFile != null) && customConfigFile.exists()) {
        return customConfigFile;
      }
    }
    return karafConfigFile;
  }
}
