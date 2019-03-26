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

import java.io.File;
import org.apache.commons.io.FilenameUtils;
import org.codice.dominion.options.Options.UpdateConfigProperty;
import org.codice.dominion.pax.exam.interpolate.PaxExamInterpolator;
import org.codice.dominion.pax.exam.options.KarafDistributionConfigurationFileRetractOption;
import org.codice.dominion.pax.exam.options.PaxExamOption.Extension;
import org.codice.dominion.resources.ResourceLoader;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;

/** Extension point for the {@link UpdateConfigProperty} option annotation. */
public class UpdateConfigPropertyExtension implements Extension<UpdateConfigProperty> {
  @Override
  public Option[] options(
      UpdateConfigProperty annotation,
      PaxExamInterpolator interpolator,
      ResourceLoader resourceLoader) {
    final Option option;

    switch (annotation.operation()) {
      case ADD:
        option = addOption(annotation.target(), annotation.key(), annotation.value());
        break;
      case SET_ABSOLUTE_PATH:
        option = setAbsolutePathOption(annotation.target(), annotation.key(), annotation.value());
        break;
      case REMOVE:
        option = removeOption(annotation.target(), annotation.key(), annotation.value());
        break;
      case SET:
      default:
        option = setOption(annotation.target(), annotation.key(), annotation.value());
    }
    return new Option[] {option};
  }

  protected Option setOption(String target, String key, String value) {
    return KarafDistributionOption.editConfigurationFilePut(
        // separators to Unix is on purpose as PaxExam will analyze the target based on it
        // containing / and not \ and then convert it properly
        FilenameUtils.separatorsToUnix(target), key, value);
  }

  protected Option setAbsolutePathOption(String target, String key, String path) {
    return setOption(
        target, key, new File(FilenameUtils.separatorsToSystem(path)).getAbsolutePath());
  }

  protected Option addOption(String target, String key, String value) {
    return KarafDistributionOption.editConfigurationFileExtend(
        // separators to Unix is on purpose as PaxExam will analyze the target based on it
        // containing / and not \ and then convert it properly
        FilenameUtils.separatorsToUnix(target), key, value);
  }

  protected Option removeOption(String target, String key, String value) {
    return new KarafDistributionConfigurationFileRetractOption(
        // separators to Unix is on purpose as our extensions (and then PaxExam) behaves the same as
        // PaxExam which analyzes the target based on it containing / and not \ and then convert it
        // properly
        FilenameUtils.separatorsToUnix(target), key, value);
  }
}
