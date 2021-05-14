package io.github

package object shelm {
  def resultOrThrow[R](r: Either[Throwable, R]): R = r match {
    case Right(value) => value
    case Left(err) => throw err
  }
}
