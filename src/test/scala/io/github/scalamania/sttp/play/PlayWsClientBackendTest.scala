package io.github.scalamania.sttp.play

import akka.actor.ActorSystem
import play.api.libs.ws.ahc.AhcWSClient
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import sttp.client3._
import sttp.client3.testing.{ConvertToFuture, HttpTest}

class PlayWsClientBackendTest extends HttpTest[Future] {
  implicit val system: ActorSystem = ActorSystem()
  import system._

  override implicit val backend: SttpBackend[Future, Any] =
    PlayWSClientBackend(AhcWSClient(), SttpBackendOptions.Default, closeClient = true)

  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future

  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))

  override protected def throwsExceptionOnUnsupportedEncoding: Boolean = false
  override protected def supportsAutoDecompressionDisabling: Boolean = false
  override protected def supportsCustomMultipartEncoding: Boolean = false
  override protected def supportsCancellation: Boolean = false

  override def afterAll(): Unit = {
    super.afterAll()
    Await.result(system.terminate(), 15.seconds)
  }
}
