/*
 * Copyright 2020 ConsenSys AG.
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
package tech.pegasys.web3signer.core;

import static tech.pegasys.web3signer.core.service.http.OpenApiOperationsId.UPCHECK;

import tech.pegasys.web3signer.core.config.ClientAuthConstraints;
import tech.pegasys.web3signer.core.config.Config;
import tech.pegasys.web3signer.core.config.TlsOptions;
import tech.pegasys.web3signer.core.metrics.MetricsEndpoint;
import tech.pegasys.web3signer.core.service.http.HostAllowListHandler;
import tech.pegasys.web3signer.core.service.http.handlers.LogErrorHandler;
import tech.pegasys.web3signer.core.service.http.handlers.PublicKeysListHandler;
import tech.pegasys.web3signer.core.service.http.handlers.UpcheckHandler;
import tech.pegasys.web3signer.core.util.FileUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.ClientAuth;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.PfxOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.ResponseContentTypeHandler;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.tuweni.net.tls.VertxTrustOptions;
import org.hyperledger.besu.plugin.services.MetricsSystem;

public abstract class Runner implements Runnable {

  private static final Logger LOG = LogManager.getLogger();

  private static final String CONTENT_TYPE_TEXT_HTML = "text/html; charset=utf-8";
  private static final String CONTENT_TYPE_YAML = "text/x-yaml";

  public static final String OPENAPI_INDEX_RESOURCE = "openapi/index.html";

  private static final String SWAGGER_ENDPOINT = "/swagger-ui";
  protected static final String JSON_RPC_PATH = "/rpc/v1";

  protected final Config config;

  public Runner(final Config config) {
    this.config = config;
  }

  @Override
  public void run() {
    // set log level per CLI flags
    System.out.println("Setting logging level to " + config.getLogLevel().name());
    Configurator.setAllLevels("", config.getLogLevel());

    final MetricsEndpoint metricsEndpoint =
        new MetricsEndpoint(
            config.isMetricsEnabled(),
            config.getMetricsPort(),
            config.getMetricsNetworkInterface(),
            config.getMetricCategories(),
            config.getMetricsHostAllowList());

    final MetricsSystem metricsSystem = metricsEndpoint.getMetricsSystem();

    final Vertx vertx = Vertx.vertx();
    final LogErrorHandler errorHandler = new LogErrorHandler();

    try {
      metricsEndpoint.start(vertx);

      final OpenAPI3RouterFactory routerFactory = getOpenAPI3RouterFactory(vertx);
      registerUpcheckRoute(routerFactory, errorHandler);
      registerHttpHostAllowListHandler(routerFactory);

      final Context context = new Context(routerFactory, metricsSystem, errorHandler, vertx);
      final Router router = populateRouter(context);
      registerSwaggerUIRoute(router); // serve static openapi spec

      final HttpServer httpServer = createServerAndWait(vertx, router);
      LOG.info("Server is up, and listening on {}", httpServer.actualPort());

      persistPortInformation(httpServer.actualPort(), metricsEndpoint.getPort());
    } catch (final Throwable e) {
      vertx.close();
      metricsEndpoint.stop();
      LOG.error("Failed to initialised application", e);
    }
  }

  protected abstract Router populateRouter(final Context context);

  protected abstract String getOpenApiSpecResource();

  private OpenAPI3RouterFactory getOpenAPI3RouterFactory(final Vertx vertx)
      throws InterruptedException, ExecutionException {
    final CompletableFuture<OpenAPI3RouterFactory> completableFuture = new CompletableFuture<>();
    OpenAPI3RouterFactory.create(
        vertx,
        getOpenApiSpecResource(),
        ar -> {
          if (ar.succeeded()) {
            completableFuture.complete(ar.result());
          } else {
            completableFuture.completeExceptionally(ar.cause());
          }
        });

    final OpenAPI3RouterFactory openAPI3RouterFactory = completableFuture.get();

    // disable automatic response content handler as it doesn't handle some corner cases.
    // Our handlers must set content type header manually.
    openAPI3RouterFactory.getOptions().setMountResponseContentTypeHandler(false);
    return openAPI3RouterFactory;
  }

  protected void addPublicKeysListHandler(
      final OpenAPI3RouterFactory openAPI3RouterFactory,
      final Set<String> identifiers,
      final String operationId,
      final LogErrorHandler errorHandler) {
    openAPI3RouterFactory.addHandlerByOperationId(
        operationId, new BlockingHandlerDecorator(new PublicKeysListHandler(identifiers), false));
    openAPI3RouterFactory.addFailureHandlerByOperationId(operationId, errorHandler);
  }

  private void registerUpcheckRoute(
      final OpenAPI3RouterFactory openAPI3RouterFactory, final LogErrorHandler errorHandler) {
    openAPI3RouterFactory.addHandlerByOperationId(
        UPCHECK.name(), new BlockingHandlerDecorator(new UpcheckHandler(), false));
    openAPI3RouterFactory.addFailureHandlerByOperationId(UPCHECK.name(), errorHandler);
  }

  private void registerHttpHostAllowListHandler(final OpenAPI3RouterFactory openApiRouterFactory) {
    openApiRouterFactory.addGlobalHandler(new HostAllowListHandler(config.getHttpHostAllowList()));
  }

  private void registerSwaggerUIRoute(final Router router) throws IOException {
    final URL indexResourceUrl = Resources.getResource(OPENAPI_INDEX_RESOURCE);
    final URL openApiSpecUrl = Resources.getResource(getOpenApiSpecResource());
    final String indexHtml = Resources.toString(indexResourceUrl, Charsets.UTF_8);
    final String openApiSpecYaml = Resources.toString(openApiSpecUrl, Charsets.UTF_8);

    router
        .route(HttpMethod.GET, SWAGGER_ENDPOINT + "/web3signer.yaml")
        .produces(CONTENT_TYPE_YAML)
        .handler(ResponseContentTypeHandler.create())
        .handler(routingContext -> routingContext.response().end(openApiSpecYaml));

    router
        .routeWithRegex(HttpMethod.GET, SWAGGER_ENDPOINT + "|" + SWAGGER_ENDPOINT + "/*")
        .produces(CONTENT_TYPE_TEXT_HTML)
        .handler(ResponseContentTypeHandler.create())
        .handler(routingContext -> routingContext.response().end(indexHtml));
  }

  private HttpServer createServerAndWait(
      final Vertx vertx, final Handler<HttpServerRequest> requestHandler)
      throws ExecutionException, InterruptedException {
    final HttpServerOptions serverOptions =
        new HttpServerOptions()
            .setPort(config.getHttpListenPort())
            .setHost(config.getHttpListenHost())
            .setIdleTimeout(config.getIdleConnectionTimeoutSeconds())
            .setIdleTimeoutUnit(TimeUnit.SECONDS)
            .setReuseAddress(true)
            .setReusePort(true);
    final HttpServerOptions tlsServerOptions = applyConfigTlsSettingsTo(serverOptions);
    final HttpServer httpServer = vertx.createHttpServer(tlsServerOptions);
    final CompletableFuture<Void> serverRunningFuture = new CompletableFuture<>();
    httpServer
        .requestHandler(requestHandler)
        .listen(
            result -> {
              if (result.succeeded()) {
                serverRunningFuture.complete(null);
              } else {
                serverRunningFuture.completeExceptionally(result.cause());
              }
            });
    serverRunningFuture.get();

    return httpServer;
  }

  private HttpServerOptions applyConfigTlsSettingsTo(final HttpServerOptions input) {

    if (config.getTlsOptions().isEmpty()) {
      return input;
    }

    HttpServerOptions result = new HttpServerOptions(input);
    result.setSsl(true);
    final TlsOptions tlsConfig = config.getTlsOptions().get();

    result = applyTlsKeyStore(result, tlsConfig);

    if (tlsConfig.getClientAuthConstraints().isPresent()) {
      result = applyClientAuthentication(result, tlsConfig.getClientAuthConstraints().get());
    }

    return result;
  }

  private static HttpServerOptions applyTlsKeyStore(
      final HttpServerOptions input, final TlsOptions tlsConfig) {
    final HttpServerOptions result = new HttpServerOptions(input);

    try {
      final String keyStorePathname =
          tlsConfig.getKeyStoreFile().toPath().toAbsolutePath().toString();
      final String password =
          FileUtil.readFirstLineFromFile(tlsConfig.getKeyStorePasswordFile().toPath());
      result.setPfxKeyCertOptions(new PfxOptions().setPath(keyStorePathname).setPassword(password));
      return result;
    } catch (final NoSuchFileException e) {
      throw new InitializationException(
          "Requested file " + e.getMessage() + " does not exist at specified location.", e);
    } catch (final AccessDeniedException e) {
      throw new InitializationException(
          "Current user does not have permissions to access " + e.getMessage(), e);
    } catch (final IOException e) {
      throw new InitializationException("Failed to load TLS files " + e.getMessage(), e);
    }
  }

  private static HttpServerOptions applyClientAuthentication(
      final HttpServerOptions input, final ClientAuthConstraints constraints) {
    final HttpServerOptions result = new HttpServerOptions(input);

    result.setClientAuth(ClientAuth.REQUIRED);
    try {
      constraints
          .getKnownClientsFile()
          .ifPresent(
              whitelistFile ->
                  result.setTrustOptions(
                      VertxTrustOptions.whitelistClients(
                          whitelistFile.toPath(), constraints.isCaAuthorizedClientAllowed())));
    } catch (final IllegalArgumentException e) {
      throw new InitializationException("Illegally formatted client fingerprint file.");
    }

    return result;
  }

  private void persistPortInformation(final int httpPort, final Optional<Integer> metricsPort) {
    if (config.getDataPath() == null) {
      return;
    }

    final File portsFile = new File(config.getDataPath().toFile(), "web3signer.ports");
    portsFile.deleteOnExit();

    final Properties properties = new Properties();
    properties.setProperty("http-port", String.valueOf(httpPort));
    metricsPort.ifPresent(port -> properties.setProperty("metrics-port", String.valueOf(port)));

    LOG.info(
        "Writing web3signer.ports file: {}, with contents: {}",
        portsFile.getAbsolutePath(),
        properties);
    try (final FileOutputStream fileOutputStream = new FileOutputStream(portsFile)) {
      properties.store(
          fileOutputStream,
          "This file contains the ports used by the running instance of Web3Signer. "
              + "This file will be deleted after the node is shutdown.");
    } catch (final Exception e) {
      LOG.warn("Error writing ports file", e);
    }
  }
}
