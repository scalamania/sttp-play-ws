package io.github.scalamania.sttp.play

import akka.actor.ActorSystem
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import sttp.client3._
import sttp.client3.testing.server.HttpServer
import sttp.client3.testing.{ConvertToFuture, HttpTest}

class PlayWsClientBackendTest extends HttpTest[Future] {
  implicit val system: ActorSystem = ActorSystem()
  import system._

  val server = new HttpServer(51823, println(_))

  override implicit val backend: SttpBackend[Future, Any] =
    PlayWSClientBackend(SttpBackendOptions.Default)

  override implicit val convertToFuture: ConvertToFuture[Future] =
    ConvertToFuture.future

  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = t.map(Some(_))

  override protected def throwsExceptionOnUnsupportedEncoding: Boolean = false
  override protected def supportsAutoDecompressionDisabling: Boolean = false
  override protected def supportsCustomMultipartEncoding: Boolean = false
  override protected def supportsCancellation: Boolean = false

  override protected def beforeAll(): Unit = {
    Await.result(server.start(), 10.seconds)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.close()
    Await.result(system.terminate(), 5.seconds)
  }
}
