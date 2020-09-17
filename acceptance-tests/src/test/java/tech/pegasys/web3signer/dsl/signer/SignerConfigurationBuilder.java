/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.web3signer.dsl.signer;

import static java.util.Collections.emptyList;

import tech.pegasys.web3signer.core.config.AzureKeyVaultParameters;
import tech.pegasys.web3signer.core.config.TlsOptions;
import tech.pegasys.web3signer.dsl.tls.TlsCertificateDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class SignerConfigurationBuilder {

  private static final String LOCALHOST = "127.0.0.1";

  private int httpRpcPort = 0;
  private int metricsPort = 0;
  private Path keyStoreDirectory = Path.of("./");
  private boolean metricsEnabled;
  private List<String> metricsHostAllowList = emptyList();
  private List<String> httpHostAllowList = emptyList();
  private TlsOptions serverTlsOptions;
  private TlsCertificateDefinition overriddenCaTrustStore;
  private String slashingProtectionDbUrl = "";
  private String slashingProtectionDbUsername = "";
  private String slashingProtectionDbPassword = "";
  private String mode;
  private AzureKeyVaultParameters azureKeyVaultParameters;

  public SignerConfigurationBuilder withHttpPort(final int port) {
    httpRpcPort = port;
    return this;
  }

  public SignerConfigurationBuilder withHttpAllowHostList(final List<String> allowHostList) {
    this.httpHostAllowList = allowHostList;
    return this;
  }

  public SignerConfigurationBuilder withKeyStoreDirectory(final Path multiKeySignerDirectory) {
    this.keyStoreDirectory = multiKeySignerDirectory;
    return this;
  }

  public SignerConfigurationBuilder withMetricsPort(final int port) {
    metricsPort = port;
    return this;
  }

  public SignerConfigurationBuilder withMetricsHostAllowList(final List<String> allowHostList) {
    this.metricsHostAllowList = allowHostList;
    return this;
  }

  public SignerConfigurationBuilder withMetricsEnabled(final boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
    return this;
  }

  public SignerConfigurationBuilder withServerTlsOptions(final TlsOptions serverTlsOptions) {
    this.serverTlsOptions = serverTlsOptions;
    return this;
  }

  public SignerConfigurationBuilder withOverriddenCA(final TlsCertificateDefinition keystore) {
    this.overriddenCaTrustStore = keystore;
    return this;
  }

  public SignerConfigurationBuilder withMode(final String mode) {
    this.mode = mode;
    return this;
  }

  public SignerConfigurationBuilder withAzureKeyVaultParameters(
      final AzureKeyVaultParameters azureKeyVaultParameters) {
    this.azureKeyVaultParameters = azureKeyVaultParameters;
    return this;
  }

  public SignerConfigurationBuilder withSlashingProtectionDbUrl(
      final String slashingProtectionDbUrl) {
    this.slashingProtectionDbUrl = slashingProtectionDbUrl;
    return this;
  }

  public SignerConfigurationBuilder withSlashingProtectionDbUsername(
      final String slashingProtectionDbUsername) {
    this.slashingProtectionDbUsername = slashingProtectionDbUsername;
    return this;
  }

  public SignerConfigurationBuilder withSlashingProtectionDbPassword(
      final String slashingProtectionDbPassword) {
    this.slashingProtectionDbPassword = slashingProtectionDbPassword;
    return this;
  }

  public SignerConfiguration build() {
    if (mode == null) {
      throw new IllegalArgumentException("Mode cannot be null");
    }
    return new SignerConfiguration(
        LOCALHOST,
        httpRpcPort,
        httpHostAllowList,
        keyStoreDirectory,
        metricsPort,
        metricsHostAllowList,
        metricsEnabled,
        Optional.ofNullable(azureKeyVaultParameters),
        Optional.ofNullable(serverTlsOptions),
        Optional.ofNullable(overriddenCaTrustStore),
        slashingProtectionDbUrl,
        slashingProtectionDbUsername,
        slashingProtectionDbPassword,
        mode);
  }
}
