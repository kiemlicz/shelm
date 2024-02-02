package io.github.kiemlicz

import scala.util.control.NonFatal

package object shelm {
  def resultOrThrow[R](r: Either[Throwable, R]): R = r match {
    case Right(value) => value
    case Left(err) => throw err
  }

  def sequence[A, B](a: List[Either[A, B]]): Either[A, List[B]] = a match {
    case Nil => Right(List.empty[B])
    case h :: t => h.flatMap(r => sequence(t).map(r :: _))
  }

  def throwableToLeft[T](block: => T): Either[Throwable, T] =
    try {
      Right(block)
    } catch {
      case NonFatal(ex) => Left(ex)
    }
}
