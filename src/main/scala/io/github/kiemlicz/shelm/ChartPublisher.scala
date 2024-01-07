package io.github.kiemlicz.shelm

import sbt.Artifact

import java.io.{File, FileInputStream}
import java.net.URI
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest}
import java.util.concurrent.CompletableFuture

object ChartPublisher {
  def chartMuseumPublishBlocking(
    httpClient: HttpClient,
    uri: URI,
    packagedArtifacts: Map[Artifact, File],
    outstanding: Int //=1 but this will make overloading not work?
  ): Unit = {
    val f = chartMuseumPublish(httpClient, uri, packagedArtifacts, outstanding)
    f.join()
  }

  def chartMuseumPublish(
    httpClient: HttpClient,
    uri: URI,
    packagedArtifacts: Map[Artifact, File],
    outstanding: Int //=1 but this will make overloading not work?
  ): CompletableFuture[Unit] = {
    //retry failed
    packagedArtifacts.grouped(outstanding).foldLeft(CompletableFuture.supplyAsync(() => ())) { (acc, e) =>
      acc.thenCompose(_ => chartMuseumPublish(httpClient, uri, e))
    }
  }

  def chartMuseumPublish(
    httpClient: HttpClient, uri: URI, packagedArtifacts: Map[Artifact, File]
  ): CompletableFuture[Unit] = {
    val ongoingPublish = for {
      (artifact, artifactLocation) <- packagedArtifacts
      r = HttpRequest.newBuilder()
        .uri(uri)
        .header("Content-Type", "application/octet-stream")
        .POST(HttpRequest.BodyPublishers.ofInputStream(() => new FileInputStream(artifactLocation))
        ) // configurable timeout handling?
        .build()
    } yield {
      httpClient.sendAsync(r, BodyHandlers.ofString()) //fixme create bodyhandler
    }

    CompletableFuture.allOf(ongoingPublish.toSeq *).thenApply(_ => ())
  }
}
