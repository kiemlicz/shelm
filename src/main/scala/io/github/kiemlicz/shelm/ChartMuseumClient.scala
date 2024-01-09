package io.github.kiemlicz.shelm

import io.github.kiemlicz.shelm.ChartRepositoryAuth.{Bearer, Cert, NoAuth, UserPassword}
import io.github.kiemlicz.shelm.exception.HelmPublishException
import sbt.util.Logger
import sbt.{Artifact, PublishConfiguration}

import java.io.{File, FileInputStream}
import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture

class ChartMuseumClient(httpClient: HttpClient, requestTimeout: Duration, pushRetries: Int = 1) {

  def chartMuseumPublishBlocking(
    repository: ChartMuseumRepository,
    publishConfiguration: PublishConfiguration,
    logger: Logger,
    outstanding: Int = 1 // but this will make overloading not work? make this a setting
  ): Either[HelmPublishException, Unit] = {
    logger.info(s"Publishing to: ${repository.uri}")
    val f = chartMuseumPublish(
      repository, publishConfiguration, logger, outstanding
    ) //without outstanding does it really copmmile?
    f.join()
  }

  /**
    * There are no retries in publishing
    *
    * @param repository
    * @param publishConfiguration
    * @param logger
    * @param outstanding
    * @return
    */
  def chartMuseumPublish(
    repository: ChartMuseumRepository,
    publishConfiguration: PublishConfiguration,
    logger: Logger,
    outstanding: Int // but this will make overloading not work?
  ): CompletableFuture[Either[HelmPublishException, Unit]] = {
    val packagedArtifacts = publishConfiguration.artifacts.toMap
    val overwrite = publishConfiguration.overwrite

    packagedArtifacts.grouped(outstanding)
      .foldLeft(CompletableFuture.supplyAsync(() => Right(()): Either[HelmPublishException, Unit])) { (acc, e) =>
        acc.thenCompose {
          case Right(_) => chartMuseumPublish(repository, e, overwrite, logger)
          case l: Left[HelmPublishException, Unit] => CompletableFuture.supplyAsync(() => l)
        }
      }
  }

  private def chartMuseumPublish(
    repository: ChartMuseumRepository,
    artifacts: Map[Artifact, File],
    overwrite: Boolean,
    logger: Logger,
  ): CompletableFuture[Either[HelmPublishException, Unit]] = {
    val ongoingPublish = for {
      (_, artifactLocation) <- artifacts
      r = HttpRequest.newBuilder()
        .uri(if (overwrite) URI.create(s"${repository.uri.toString}?force") else repository.uri
        ) // fixme something smarter
        .header("Content-Type", "application/octet-stream")
        .timeout(requestTimeout)
        .POST(HttpRequest.BodyPublishers.ofInputStream(() => new FileInputStream(artifactLocation))
        )
    } yield {
      val request = withAuth(r, repository.auth)
      logger.debug(s"Scheduling publish to ${repository.uri}")
      httpClient
        .sendAsync(request, BodyHandlers.ofString())
        .thenComposeAsync(withRetry(request, _, logger))
    }
    CompletableFuture.allOf(ongoingPublish.toSeq *).thenApply { _ =>
      //allOf returns Void, must iterate over original futures
      sequence(ongoingPublish.map { f =>
        val response = f.join()
        Either.cond(
          response.statusCode() < 400,
          (),
          new HelmPublishException(repository, response.statusCode(), Some(response.body()))
        )
      }.toList
      ).map(_ => ())
    } //stop map if failed???????????????????
  }

  private def withRetry(
    request: HttpRequest, response: HttpResponse[String], logger: Logger, attempt: Int = 0
  ): CompletableFuture[HttpResponse[String]] = if (response.statusCode() >= 400 && attempt < pushRetries) {
    logger.debug(s"Request for: ${request.uri()}, retry")
    httpClient
      .sendAsync(request, BodyHandlers.ofString())
      .thenComposeAsync(r => withRetry(request, r, logger, attempt + 1))
  } else {
    CompletableFuture.completedFuture(response)
  }

  private def withAuth(requestBuilder: HttpRequest.Builder, auth: ChartRepositoryAuth): HttpRequest = {
    auth match {
      case UserPassword(user, password) =>
        val r = s"${user}:${password}"
        requestBuilder.header("Authorization", s"Basic ${Base64.getEncoder.encodeToString(r.getBytes())}")
      case Bearer(token) =>
        requestBuilder.header("Authorization", s"Bearer ${token}")
      case Cert(certFile, keyFile, ca) => ??? //this is also valid option
      case NoAuth =>
    }
    requestBuilder.build()
  }

}
