package io.github.kiemlicz.shelm

import io.github.kiemlicz.shelm.ChartRepositoryAuth.{Bearer, Cert, NoAuth, UserPassword}
import io.github.kiemlicz.shelm.exception.HelmPublishException
import sbt.util.Logger
import sbt.{Artifact, PublishConfiguration}

import java.io.{File, FileInputStream}
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture

class ChartMuseumClient(httpClient: HttpClient, requestTimeout: Duration, pushRetries: Int = 1) {

  /**
    *
    * @param publishConfiguration SBT's PublishConfiguration (artifacts and their meta data)
    * @param outstanding          up to how many concurrent requests can be scheduled
    * @return
    */
  def chartMuseumPublishBlocking(
    repository: ChartMuseumRepository,
    publishConfiguration: PublishConfiguration,
    logger: Logger,
    outstanding: Int = 1
  ): Either[HelmPublishException, Unit] = {
    logger.info(s"Publishing to: ${repository.uri}")
    chartMuseumPublish(
      repository,
      publishConfiguration,
      logger,
      outstanding
    ).join()
  }

  /**
    * Each publish is `pushRetries` times retried
    *
    * @param publishConfiguration SBT's PublishConfiguration (artifacts and their meta data)
    * @param outstanding          up to how many concurrent requests can be scheduled
    * @return
    */
  def chartMuseumPublish(
    repository: ChartMuseumRepository,
    publishConfiguration: PublishConfiguration,
    logger: Logger,
    outstanding: Int = 1
  ): CompletableFuture[Either[HelmPublishException, Unit]] = {
    val packagedArtifacts = publishConfiguration.artifacts.toMap
    val overwrite = publishConfiguration.overwrite

    packagedArtifacts
      .grouped(outstanding)
      .foldLeft(CompletableFuture.supplyAsync[Either[HelmPublishException, Unit]](() => Right(()))) { (acc, artifactsBatch) =>
        acc.thenCompose {
          case Right(_) => chartMuseumPublish(repository, artifactsBatch, overwrite, logger)
          case l: Left[HelmPublishException, Unit] => CompletableFuture.supplyAsync(() => l)
        }
      }
  }

  /**
    *
    * @param artifacts artifacts that will be concurrently uploaded
    * @param overwrite force upload
    * @return
    */
  private def chartMuseumPublish(
    repository: ChartMuseumRepository,
    artifacts: Map[Artifact, File],
    overwrite: Boolean,
    logger: Logger,
  ): CompletableFuture[Either[HelmPublishException, Unit]] = {
    val ongoingPublishes = for {
      (_, artifactLocation) <- artifacts
      r = HttpRequest.newBuilder()
        .uri(if (overwrite) ChartMuseumRepository.forcePushUrl(repository.uri) else repository.uri)
        .header("Content-Type", "application/octet-stream")
        .timeout(requestTimeout)
        .POST(HttpRequest.BodyPublishers.ofInputStream(() => new FileInputStream(artifactLocation)))
    } yield {
      val request = withAuth(r, repository.auth, logger)
      logger.debug(s"Scheduling publish to ${repository.uri}")
      httpClient
        .sendAsync(request, BodyHandlers.ofString())
        .thenComposeAsync(withRetry(request, _, logger))
    }

    CompletableFuture.allOf(ongoingPublishes.toSeq *)
      .thenApply { _ =>
        /*
        allOf returns Void
        Must iterate over original futures to check if their successfully published (code < 400)
        */
        sequence(
          ongoingPublishes.map { f =>
            val response = f.join() //result must be ready due to allOf
            Either.cond(
              response.statusCode() < 400,
              (),
              new HelmPublishException(repository, response.statusCode(), Some(response.body()))
            )
          }.toList
        ).map(_ => ())
      }
  }

  private def withRetry(
    request: HttpRequest,
    response: HttpResponse[String],
    logger: Logger,
    attempt: Int = 0
  ): CompletableFuture[HttpResponse[String]] =
    if (response.statusCode() >= 400 && attempt < pushRetries) {
      logger.debug(s"Request for: ${request.uri()}, retry: $attempt")
      httpClient
        .sendAsync(request, BodyHandlers.ofString())
        .thenComposeAsync(withRetry(request, _, logger, attempt + 1))
    } else
      CompletableFuture.completedFuture(response)


  private def withAuth(requestBuilder: HttpRequest.Builder, auth: ChartRepositoryAuth, logger: Logger): HttpRequest = {
    auth match {
      case UserPassword(user, password) =>
        val encodedAuth = Base64.getEncoder.encodeToString(s"${user}:${password}".getBytes())
        requestBuilder.header("Authorization", s"Basic $encodedAuth")
      case Bearer(token, _) =>
        requestBuilder.header("Authorization", s"Bearer ${token}")
      case Cert(_, _, _) =>
        //for now just omitting, todo consider failing now
        logger.error("Omitting Cert auth method for ChartMuseum - unsupported")
      case NoAuth =>
    }
    requestBuilder.build()
  }
}
