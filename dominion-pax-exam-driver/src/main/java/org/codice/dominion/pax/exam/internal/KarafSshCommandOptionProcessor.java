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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.utils.properties.Properties;
import org.apache.karaf.client.Main;
import org.apache.karaf.util.config.PropertiesLoader;
import org.apache.sshd.agent.SshAgent;
import org.apache.sshd.agent.local.AgentImpl;
import org.apache.sshd.agent.local.LocalAgentFactory;
import org.apache.sshd.client.ClientBuilder;
import org.apache.sshd.client.ClientFactoryManager;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.RuntimeSshException;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.util.io.EmptyInputStream;
import org.apache.sshd.common.util.io.NoCloseOutputStream;
import org.codice.dominion.options.OptionException;
import org.codice.dominion.pax.exam.internal.DominionConfigurationFactory.AnnotationOptions;
import org.codice.dominion.pax.exam.options.KarafSshCommandOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class used to process configured {@link KarafSshCommandOption}s. */
public class KarafSshCommandOptionProcessor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(KarafDistributionConfigurationFileRetractOptionProcessor.class);

  private static final String ROLE_DELIMITER = ",";
  private static final String GROUP_PREFIX = "_g_:";

  private static final int DEFAULT_SSH_PORT = 8101;
  private static final long DEFAULT_IDLE_TIMEOUT = TimeUnit.MINUTES.toMillis(30L);
  private static final long DEFAULT_RETRY_DELAY = TimeUnit.SECONDS.toMillis(2L);
  private static final String DEFAULT_HEARTBEAT_INTERVAL =
      String.valueOf(TimeUnit.SECONDS.toMillis(60L));
  private static final int DEFAULT_RETRY_ATTEMPTS = 2;

  private final AnnotationOptions options;
  private final PaxExamDriverInterpolator interpolator;
  private final List<KarafSshCommandOption> commands;

  private final String host;
  private final int port;
  private final long idleTimeout;
  private final String userId;
  private final String password;

  public KarafSshCommandOptionProcessor(AnnotationOptions options) throws IOException {
    this.options = options;
    this.interpolator = options.getInterpolator();
    this.commands = options.options(KarafSshCommandOption.class).collect(Collectors.toList());
    if (commands.isEmpty()) {
      this.host = null;
      this.port = -1;
      this.idleTimeout = 0L;
      this.userId = null;
      this.password = null;
      return; // no commands to execute so bail
    }
    final Properties shellCfg;
    final Properties users;

    try {
      shellCfg =
          PropertiesLoader.loadPropertiesFile(
              interpolator.getKarafEtc().resolve("org.apache.karaf.shell.cfg").toUri().toURL(),
              true);
      users =
          PropertiesLoader.loadPropertiesFile(
              interpolator.getKarafEtc().resolve("users.properties").toUri().toURL(), true);
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
    String sshHost = KarafSshCommandOptionProcessor.getString(shellCfg, "sshHost", "localhost");

    if (sshHost.contains("0.0.0.0")) {
      sshHost = "localhost";
    }
    this.host = sshHost;
    this.port =
        KarafSshCommandOptionProcessor.getInt(
            shellCfg, "sshPort", KarafSshCommandOptionProcessor.DEFAULT_SSH_PORT);
    this.idleTimeout =
        KarafSshCommandOptionProcessor.getLong(
            shellCfg, "sshIdleTimeout", KarafSshCommandOptionProcessor.DEFAULT_IDLE_TIMEOUT);
    final Map.Entry<String, String> user =
        users
            .entrySet()
            .stream()
            .filter(KarafSshCommandOptionProcessor::isUser)
            .filter(KarafSshCommandOptionProcessor::hasSshRole)
            .findAny()
            .orElse(null);

    if (user == null) {
      throw new OptionException("no local users configured for ssh");
    }
    this.userId = user.getKey();
    this.password =
        StringUtils.substringBefore(user.getValue(), KarafSshCommandOptionProcessor.ROLE_DELIMITER);
  }

  /**
   * Processes ssh command options.
   *
   * @throws IOException if an I/O error occurs while processing the options
   */
  public void process() throws IOException {
    LOGGER.debug("{}::process()", this);
    if (commands.isEmpty()) {
      return; // no commands to execute so bail
    }
    try (final SshClient client = ClientBuilder.builder().build()) {
      setupAgent(client, null, null);
      // define hearbeat (for the keep alive) and timeouts
      client
          .getProperties()
          .put(
              ClientFactoryManager.HEARTBEAT_INTERVAL,
              KarafSshCommandOptionProcessor.DEFAULT_HEARTBEAT_INTERVAL);
      client.getProperties().put(ClientFactoryManager.IDLE_TIMEOUT, String.valueOf(idleTimeout));
      client
          .getProperties()
          .put(ClientFactoryManager.NIO2_READ_TIMEOUT, String.valueOf(idleTimeout));
      client.start();
      try (final ClientSession session = connectWithRetries(client)) {
        session.addPasswordIdentity(password);
        session.auth().verify();
        for (final KarafSshCommandOption command : commands) {
          execute(command, session);
        }
      } finally {
        client.stop();
      }
    }
  }

  @SuppressWarnings(
      "squid:S106" /* purposely tighing the SSH session to the current output streams*/)
  private void execute(KarafSshCommandOption command, ClientSession session) throws IOException {
    LOGGER.debug("SSH: executing [{}] ...", command.getCommand());
    try (final ChannelExec shell =
        session.createExecChannel(StringUtils.appendIfMissing(command.getCommand(), "\n"))) {
      shell.setIn(new EmptyInputStream());
      shell.setAgentForwarding(true);
      shell.setOut(new NoCloseOutputStream(System.out));
      shell.setErr(new NoCloseOutputStream(System.err));
      shell.open().verify(5L, TimeUnit.SECONDS);
      shell.waitFor(
          EnumSet.of(ClientChannelEvent.CLOSED), command.getTimeout() + 5000L); // padd 5 seconds
      final Integer status = shell.getExitStatus();

      if (status == null) {
        throw new IOException("command [" + command.getCommand() + "] failure; no exit status");
      } else if (status.intValue() != 0) {
        throw new IOException(
            "command [" + command.getCommand() + "] failure; exit status: " + status);
      }
      LOGGER.debug("SSH: [{}] successful", command.getCommand());
    }
  }

  private ClientSession connectWithRetries(SshClient client) throws IOException {
    int retries = 0;

    LOGGER.info(
        "Establishing an SSH connection to {} container on {}:{} as '{}' ...",
        interpolator.getContainer(),
        host,
        port,
        userId);
    while (true) {
      final ConnectFuture future = client.connect(userId, host, port);

      future.await();
      try {
        return future.getSession();
      } catch (RuntimeSshException e) {
        if (retries++ >= KarafSshCommandOptionProcessor.DEFAULT_RETRY_ATTEMPTS) {
          throw e;
        }
        try {
          Thread.sleep(KarafSshCommandOptionProcessor.DEFAULT_RETRY_DELAY);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
        LOGGER.info(
            "Retrying to establish an SSH connection to {} container ...",
            interpolator.getContainer());
      }
    }
  }

  private void setupAgent(
      SshClient client, @Nullable String keyFile, @Nullable FilePasswordProvider passwordProvider)
      throws IOException {
    final URL builtInPrivateKey = Main.class.getClassLoader().getResource("karaf.key");
    final SshAgent agent = startAgent(builtInPrivateKey, keyFile, passwordProvider);

    client.setAgentFactory(new LocalAgentFactory(agent));
    client.getProperties().put(SshAgent.SSH_AUTHSOCKET_ENV_NAME, "local");
  }

  @SuppressWarnings({
    "squid:CallToDeprecatedMethod" /* in error conditions, we don't care if the agent cannot be closed */,
    "squid:S2095" /* agent is being returned out so we don't want to close it */,
    "squid:S2093" /* we want the control to not close in successful cpaths */
  })
  private SshAgent startAgent(
      URL privateKeyUrl, @Nullable String keyFile, @Nullable FilePasswordProvider passwordProvider)
      throws IOException {
    InputStream is = null;
    SshAgent agent = null;

    try {
      agent = new AgentImpl();
      is = privateKeyUrl.openStream();
      final ObjectInputStream r = new ObjectInputStream(is);
      final KeyPair keyPair = (KeyPair) r.readObject();

      is.close();
      is = null;
      agent.addIdentity(keyPair, userId);
      if (keyFile != null) {
        final FileKeyPairProvider fileKeyPairProvider = new FileKeyPairProvider(Paths.get(keyFile));

        fileKeyPairProvider.setPasswordFinder(passwordProvider);
        for (final KeyPair key : fileKeyPairProvider.loadKeys()) {
          agent.addIdentity(key, userId);
        }
      }
      return agent;
    } catch (ClassNotFoundException e) {
      LOGGER.error("Error starting ssh agent for: {}", e.getMessage());
      IOUtils.closeQuietly(agent);
      throw new IOException(e);
    } catch (IOException e) {
      LOGGER.error("Error starting ssh agent for: {}", e.getMessage());
      IOUtils.closeQuietly(agent);
      throw e;
    } finally {
      IOUtils.closeQuietly(is);
    }
  }

  private static boolean isUser(Map.Entry<String, String> e) {
    return !e.getKey().startsWith(KarafSshCommandOptionProcessor.GROUP_PREFIX);
  }

  private static boolean hasSshRole(Map.Entry<String, String> e) {
    final String value = e.getValue();
    int i = 0;

    while (true) {
      final int j = value.indexOf(KarafSshCommandOptionProcessor.ROLE_DELIMITER, i);

      if (j == -1) {
        return "ssh".equals(value.substring(i));
      }
      if ("ssh".equals(value.substring(i, j))) {
        return true;
      }
      i = j + 1;
    }
  }

  private static String getString(Properties properties, String key, String dflt) {
    return Objects.toString(properties.get(key), dflt);
  }

  private static int getInt(Properties properties, String key, int dflt) {
    final Object val = properties.get(key);

    if (val instanceof Number) {
      return ((Number) val).intValue();
    } else if (val != null) {
      return Integer.parseInt(val.toString());
    }
    return dflt;
  }

  private static long getLong(Properties properties, String key, long dflt) {
    final Object val = properties.get(key);

    if (val instanceof Number) {
      return ((Number) val).longValue();
    } else if (val != null) {
      return Long.parseLong(val.toString());
    }
    return dflt;
  }
}
