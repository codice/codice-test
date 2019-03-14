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
package org.codice.dominion.options.karaf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;
import org.codice.dominion.conditions.Conditions;
import org.codice.dominion.interpolate.Interpolate;
import org.codice.dominion.options.Option;
import org.codice.dominion.options.Options;
import org.codice.dominion.options.Options.UpdateConfigFile;
import org.codice.maven.MavenUrl;

/**
 * This class defines annotations that can be used to configure specific Karaf distribution options
 * for containers. It is solely used for scoping.
 */
public class KarafOptions {
  /** Maven configuration file. */
  public static final String PAX_URL_MVN_CFG = "etc/org.ops4j.pax.url.mvn.cfg";

  /** Users properties file. */
  public static final String USER_PROPERTIES = "etc/users.properties";

  /** Users attributes file. */
  public static final String USER_ATTRIBUTES = "etc/users.attributes";

  /**
   * Option for installing one or more features from a Karaf features descriptor.
   *
   * <p><i>Note:</i> If none of {@link #repository} or {@link #repositoryUrl} is specified, it will
   * default to a repository url referencing the artifact defined by the maven project where this
   * annotation is used with an <code>"xml"</code> type and a <code>"features"</code> classifier
   * (i.e. <code></code>@MavenUrl(groupId=MavenUrl.AS_PROJECT, artifactId=MavenUrl.AS_PROJECT,
   * version=MavenUrl.AS_PROJECT, type="xml", classifier="features")</code>).
   */
  @Option.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.Features.class)
  public @interface Feature {
    /**
     * Specifies the maven repository url where the feature(s) is(are) defined.
     *
     * @return the maven repository url where the feature(s) is(are) defined
     */
    MavenUrl repository() default
        @MavenUrl(groupId = Options.NOT_DEFINED, artifactId = Options.NOT_DEFINED);

    /**
     * Specifies the maven repository url where the feature(s) is(are) defined.
     *
     * @return the maven repository url where the feature(s) is(are) defined
     */
    @Interpolate
    String repositoryUrl() default Options.NOT_DEFINED;

    /**
     * Specifies the name(s) of the features to install.
     *
     * @return the name(s) of the features to install
     */
    @Interpolate
    String[] names();
  }

  /** Option to configure Karaf's log level. */
  @Option.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface SetLogLevel {
    /**
     * Specifies Karaf log level (i.e. one of <code>TRACE</code>, <code>DEBUG</code>, <code>INFO
     * </code>, <code>WARN</code>, or <code>ERROR</code>.
     *
     * <p>If the level is empty, then no configuration changes will occur.
     *
     * @return the level for Karaf's logs
     */
    @Interpolate
    String level();
  }

  /**
   * Configures which distribution options to use. Relevant are the framework URL, the framework
   * name and the Karaf version since all of those params are relevant to decide which wrapper
   * configurations to use.
   *
   * <p>Only those defined with the same platform as where PaxExam is running will be used. All
   * others will be ignored.
   */
  @Option.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.DistributionConfigurations.class)
  public @interface DistributionConfiguration {
    /**
     * Specifies the maven url reference for the framework.
     *
     * <p><i>Note:</i> Either {@link #framework} or {@link #frameworkUrl} must be specified.
     *
     * @return the maven url reference for the framework
     */
    MavenUrl framework() default
        @MavenUrl(groupId = Options.NOT_DEFINED, artifactId = Options.NOT_DEFINED);

    /**
     * Specifies the maven url where the feature(s) is(are) defined.
     *
     * <p><i>Note:</i> Either of {@link #framework} or {@link #frameworkUrl} must be specified.
     *
     * @return the maven url reference for the framework
     */
    @Interpolate
    String frameworkUrl() default Options.NOT_DEFINED;

    /**
     * Specifies the optional name for the framework.
     *
     * <p>These options will be ignored if the {@link
     * org.codice.dominion.Dominion#DISTRIBUTION_PROPERTY} system property is defined and doesn't
     * match this optional name (if provided).
     *
     * @return the optional name for the framework
     */
    @Interpolate
    String name() default Options.NOT_DEFINED;

    /**
     * Specifies the version of karaf used by the framework.
     *
     * @return the version of karaf used by the framework
     */
    @Interpolate
    String version() default Options.NOT_DEFINED;

    /**
     * Specifies the main entry-point used by the framework (defaults to <code>
     * "org.apache.karaf.main.Main"</code>).
     *
     * @return the main entry point class used by the framework
     */
    @Interpolate
    String main() default Options.NOT_DEFINED;

    /**
     * Specifies the location of karaf.data relative to the installation (defaults to <code>"data"
     * </code>).
     *
     * @return the location of karaf.data relative to the installation
     */
    @Interpolate
    String data() default "data";

    /**
     * Specifies the location of karaf.etc relative to the installation (defaults to <code>"etc"
     * </code>).
     *
     * @return the location of karaf.etc relative to the installation
     */
    @Interpolate
    String etc() default "etc";

    /**
     * Specifies the unpack directory for the karaf distribution. In this directory (defaults to
     * <code>target/dominion</code>) a date-based directory will be created for each running
     * instance under which directories named for each containers will be created under which a UUID
     * named directory will be created for each environment.
     *
     * @return specifies the unpack directory for the karaf distribution
     */
    @Interpolate
    String unpack() default "target/dominion";

    /**
     * Per default the framework simply copies all referenced artifacts (via {@link
     * org.codice.dominion.options.Options} and {@link KarafOptions} to the deploy folder of the
     * karaf (base) distribution. If you don't have such a folder (for any reason) you can set this
     * option to <code>false</code>. The Dominion container framework will then try to add those
     * deployment urls directly to a features xml instead of copying those files to the deploy
     * folder.
     *
     * @return <code>false</code> to have the Dominion container framework add deployment urls
     *     directly to a features xml file; <code>true</code> to have it copy those deployment files
     *     to the deploy folder
     */
    boolean deploy() default false;

    /**
     * Per default the Dominion container framework will run Karaf as a forked Java process. This
     * can be used to run Karaf as an embedded instance instead.
     *
     * @return <code>false</code> to run Karaf as a forked Java process; <code>true</code> to run it
     *     as an embedded instance
     */
    boolean embed() default false;

    /**
     * Specifies the Karaf platform these options are for (defaults to the platform where PaxExam is
     * running). These options will be ignored if the Dominion container framework is not running on
     * the same platform.
     *
     * @return the platform these options are for
     */
    Platform platform() default Platform.DEFAULT;

    /**
     * Specifies the startup script relative to <code>{karaf.home}</code> for the framework
     * (defaults to either <code>bin\\Karaf.bat</code> or <code>bin/karaf</code> based on the
     * current OS).
     *
     * <p><i>Note:</i> This script will automatically be made executable.
     *
     * @return the relative startup script filename for the framework
     */
    @Interpolate
    String start() default Options.NOT_DEFINED;

    /**
     * Specifies file(s) relative to <code>{karaf.home}</code> to make executable before starting up
     * the framework.
     *
     * <p><i>Note:</i> The startup script and all files located under <code>{karaf.bin}</code> are
     * automatically made executable so there is no need to specify them again here.
     *
     * @return relative filename(s) to make executable
     */
    @Interpolate
    String[] executables() default {};

    /** Type of platforms the Karaf framework can run in. */
    public enum Platform {
      WINDOWS,
      UNIX,
      DEFAULT
    }
  }

  /**
   * Option that ensures that information about the overridden location of the local Maven
   * repository user using the <code>maven.repo.local</code> will be passed to the container.
   */
  @Conditions.NotBlankSystemProperty("maven.repo.local")
  @UpdateConfigFile(
    target = KarafOptions.PAX_URL_MVN_CFG,
    key = "org.ops4j.pax.url.mvn.localRepository",
    value = "{maven.repo.local:-}"
  )
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  public @interface PropagateOverriddenMavenLocalRepo {}

  /**
   * Options for adding a new local user or replacing an existing user.
   *
   * <p>This option will be updating the files <code>etc/users.properties</code> file.
   */
  @Option.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.LocalUsers.class)
  public @interface LocalUser {
    /**
     * Specifies the unique user id.
     *
     * @return the unique user id
     */
    @Interpolate
    String userId();

    /**
     * Specifies the password for the user.
     *
     * @return the password for the user
     */
    @Interpolate
    String password();

    /**
     * Specifies optional roles to be added to the user (see {@link UserRoles} for known roles).
     *
     * <p><i>Note:</i> At least one role or group must be specified.
     *
     * @return optional roles to be added to the user
     */
    @Interpolate
    String[] roles() default Options.NOT_DEFINED;

    /**
     * Specifies optional groups to be added to the user.
     *
     * <p><i>Note:</i> At least one role or group must be specified.
     *
     * @return optional groups to be added to the user
     */
    @Interpolate
    String[] groups() default Options.NOT_DEFINED;
  }

  /**
   * Options for adding a new local group or replacing an existing group.
   *
   * <p>This option will be updating the files <code>etc/users.properties</code> file.
   */
  @Option.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.LocalGroups.class)
  public @interface LocalGroup {
    /**
     * Specifies the unique group id.
     *
     * @return the unique group id
     */
    @Interpolate
    String groupId();

    /**
     * Specifies roles to be added to the group (see {@link UserRoles} for known roles).
     *
     * @return roles to be added to the group
     */
    @Interpolate
    String[] roles();
  }

  /**
   * Options for appending commands to Karaf's <code>etc/shell.init.script</code> before it is
   * started.
   *
   * <p><i>Note:</i> Commands introduced this way will not be waited upon before starting executing
   * the tests. Instead use {@link ExecuteShellCommand} in order to execute commands via Karaf's
   * remote SSH endpoint after it has started and before tests are executed.
   */
  @Option.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.UpdateShellInitScripts.class)
  public @interface UpdateShellInitScript {
    /**
     * Specifies the filename to copy its commands content to the target file.
     *
     * <p><i>Note:</i> One of {@link #file}, {@link #url}, {@link #content}, or {@link #resource}
     * must be specified.
     *
     * @return the source filename to copy
     */
    @Interpolate
    String file() default Options.NOT_DEFINED;

    /**
     * Specifies the url to copy its comamnds content to the target file.
     *
     * <p><i>Note:</i> One of {@link #file}, {@link #url}, {@link #content}, or {@link #resource}
     * must be specified.
     *
     * @return the source url to copy
     */
    @Interpolate
    String url() default Options.NOT_DEFINED;

    /**
     * Specifies the commands to copy to the target file. Each entry will represent a different line
     * in the target file.
     *
     * <p><i>Note:</i> One of {@link #file}, {@link #url}, {@link #content}, or {@link #resource}
     * must be specified.
     *
     * @return the content text to copy
     */
    @Interpolate
    String[] content() default Options.NOT_DEFINED;

    /**
     * Specifies the resource name for the commands to copy to the target file.
     *
     * <p><i>Note:</i> One of {@link #file}, {@link #url}, {@link #content}, or {@link #resource}
     * must be specified.
     *
     * @return the resource name to copy
     */
    @Interpolate
    String resource() default Options.NOT_DEFINED;
  }

  /**
   * Options for executing commands via Karaf's remote SSH endpoint after it has started and before
   * tests are executed.
   *
   * <p><i>Note:</i> Commands introduced this way will be waited upon before starting executing the
   * tests. If not required, one can use {@link UpdateShellInitScript} to simply have the commands
   * added to Karaf's startup script.
   */
  @Option.Annotation
  @Target(ElementType.TYPE)
  @Retention(RetentionPolicy.RUNTIME)
  @Inherited
  @Documented
  @Repeatable(Repeatables.ExecuteShellCommands.class)
  public @interface ExecuteShellCommand {
    /**
     * Specifies the command to be executed via an ssh session.
     *
     * @return the command to be executed
     */
    @Interpolate
    String command();

    /**
     * Specifies the units for the maximum amount of time to wait for the command to complete
     * (defaults to milliseconds).
     *
     * @return the units for the maximum amount of time to wait
     */
    TimeUnit units() default TimeUnit.MILLISECONDS;

    /**
     * Specifies the maximum amount of time time to wait for the command to complete in the provided
     * units.
     *
     * @return the maximum amount of time to wait
     */
    long timeout();
  }

  /** This interface is defined purely to provide scoping. */
  public interface Repeatables {
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link Feature} annotations. */
    public @interface Features {
      Feature[] value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link DistributionConfiguration} annotations. */
    public @interface DistributionConfigurations {
      DistributionConfiguration[] value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link UpdateShellInitScript} annotations. */
    public @interface UpdateShellInitScripts {
      UpdateShellInitScript[] value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link ExecuteShellCommand} annotations. */
    public @interface ExecuteShellCommands {
      ExecuteShellCommand[] value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link LocalUser} annotations. */
    public @interface LocalUsers {
      LocalUser[] value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @Documented
    /** Defines several {@link LocalGroup} annotations. */
    public @interface LocalGroups {
      LocalGroup[] value();
    }
  }

  private KarafOptions() {}
}
