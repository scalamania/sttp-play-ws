/*
 * Copyright 2019 Rui Batista
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.scalamania.sttp.play

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source, StreamConverters}
import akka.util.ByteString
import java.io.File
import play.api.libs.ws._
import play.api.libs.ws.ahc.AhcWSClient
import play.api.mvc.MultipartFormData
import play.core.formatters.{Multipart => PlayMultipart}
import scala.concurrent.{ExecutionContext, Future}
import sttp.capabilities.Effect
import sttp.client3._
import sttp.model._
import sttp.monad.{FutureMonad, MonadError}

final class PlayWSClientBackend private (
    wsClient: WSClient,
    mustCloseClient: Boolean,
    backendOptions: SttpBackendOptions
)(implicit
    ec: ExecutionContext,
    mat: Materializer
) extends SttpBackend[Future, Source[ByteString, Any]] {

  private val maybeProxyServer = backendOptions.proxy.map { sttpProxy =>
    DefaultWSProxyServer(sttpProxy.host, sttpProxy.port, if (sttpProxy.port == 443) Some("https") else None)
  }

  private type S = Source[ByteString, Any] with Effect[Future]

  private def convertRequest[T](request: Request[T, S]): WSRequest = {
    val holder = wsClient.url(request.uri.toJavaUri.toASCIIString)

    val holderWithProxy =
      maybeProxyServer.fold(holder)(holder.withProxyServer)

    val maybeInputContentType = request.headers
      .collectFirst {
        case h if h.name == HeaderNames.ContentType => h.value
      }

    val (maybeBody, maybeContentType) = requestBodyToWsBodyAndContentType(request.body, maybeInputContentType)
    val contentType: String = maybeContentType.getOrElse(MediaType.ApplicationOctetStream.toString())

    val w: BodyWritable[WSBody] = BodyWritable(identity, contentType)

    maybeBody
      .fold(holderWithProxy)(b => holderWithProxy.withBody(b)(w))
      .withFollowRedirects(false) // Wrapper backend will handle this
      .withHttpHeaders(request.headers.map(h => (h.name, h.value)): _*)
      .withMethod(request.method.method)
      .withRequestTimeout(request.options.readTimeout)
  }

  private def requestBodyToWsBodyAndContentType[T](
      requestBody: RequestBody[S],
      maybeContentType: Option[String]
  ): (Option[WSBody], Option[String]) = {
    def contentType(ct: String): String = maybeContentType.getOrElse(ct)

    requestBody match {
      case StringBody(s, encoding, ct) =>
        (Some(InMemoryBody(ByteString(s, encoding))), Some(contentType(ct.toString())))
      case ByteArrayBody(a, ct) =>
        (Some(InMemoryBody(ByteString(a))), Some(contentType(ct.toString())))
      case ByteBufferBody(b, ct) =>
        (Some(InMemoryBody(ByteString(b))), Some(contentType(ct.toString())))
      case InputStreamBody(in, ct) =>
        (Some(SourceBody(StreamConverters.fromInputStream(() => in))), Some(contentType(ct.toString())))
      case StreamBody(s) =>
        (Some(SourceBody(s.asInstanceOf[S])), None)
      case NoBody =>
        (None, None)
      case FileBody(file, ct) =>
        (Some(SourceBody(FileIO.fromPath(file.toPath))), Some(contentType(ct.toString())))
      case MultipartBody(parts) =>
        val contentType = maybeContentType.getOrElse("multipart/form-data")
        val boundary = PlayMultipart.randomBoundary()
        val finalContentType = s"$contentType; boundary=$boundary"
        val playParts = Source(parts.map(toPlayMultipart))
        (Some(SourceBody(PlayMultipart.transform(playParts, boundary))), Some(finalContentType))
    }
  }

  private def toPlayMultipart(part: Part[RequestBody[S]]) = {
    def byteStringPart(bstr: ByteString, ct: Option[String]) =
      byteSourcePart(Source.single(bstr), ct)

    def byteSourcePart(source: Source[ByteString, Any], ct: Option[String]) = {
      MultipartFormData.FilePart(part.name, part.fileName.getOrElse(""), part.contentType orElse ct, source)
    }

    def nameWithFilename =
      part.fileName.fold(part.name) { fn =>
        s"""${part.name}"; filename="$fn"""
      }

    part.body match {
      case MultipartBody(_) | NoBody | StreamBody(_) =>
        ???
      case StringBody(s, _, _) =>
        MultipartFormData.DataPart(nameWithFilename, s)
      case ByteArrayBody(a, ct) =>
        byteStringPart(ByteString(a), Some(ct.toString()))
      case ByteBufferBody(b, ct) =>
        byteStringPart(ByteString(b), Some(ct.toString()))
      case InputStreamBody(in, ct) =>
        byteSourcePart(StreamConverters.fromInputStream(() => in), Some(ct.toString()))
      case FileBody(file, ct) =>
        MultipartFormData.FilePart(
          part.name,
          part.fileName.getOrElse(file.name),
          part.contentType orElse Some(ct.toString()),
          FileIO.fromPath(file.toPath)
        )
    }
  }

  def send[T, R >: S](r: Request[T, R]): Future[Response[T]] =
    adjustExceptions(r) {
      val request = convertRequest(r)

      val execute =
        r.response match {
          case ResponseAsStream(_, _) => request.stream _
          case _                      => request.execute _
        }

      execute().flatMap(readResponse(_, r.response))
    }

  private def readResponse[T](response: WSResponse, responseAs: ResponseAs[T, S]) = {
    val headers = response.headers.toList.flatMap {
      case (name, values) => values.map(v => Header.unsafeApply(name, v))
    }

    val metadata =
      ResponseMetadata(
        _headers = headers,
        statusCode = StatusCode.unsafeApply(response.status),
        _statusText = response.statusText
      )

    val body =
      readBody(response, metadata, responseAs)

    body.map(b => Response(b, metadata.code, metadata.statusText, metadata.headers))
  }

  private def readBody[T](
      response: StandaloneWSResponse,
      metadata: ResponseMetadata,
      responseAs: ResponseAs[T, S]
  ): Future[T] =
    responseAs match {
      case MappedResponseAs(raw, g, _) =>
        readBody(response, metadata, raw).map(r => g(r, metadata))
      case r @ ResponseAsFromMetadata(_, _) =>
        readBody(response, metadata, r(metadata))
      case ResponseAsByteArray =>
        Future { response.bodyAsBytes.toArray }
      case ResponseAsStream(s, f) =>
        f.asInstanceOf[(Any, ResponseMetadata) => Future[T]](s, metadata)
      case r: ResponseAsStreamUnsafe[_, S] =>
        Future { r.s.asInstanceOf[T] }
      case ResponseAsFile(file) =>
        saveFile(file.toFile, response).map(_ => file)
      case ResponseAsBoth(l, r) =>
        readBody(response, metadata, l.asInstanceOf[ResponseAs[T, S]]).flatMap { lResult =>
          readBody(response, metadata, r).map { rResult =>
            (lResult, Some(rResult))
          }
        }
      case IgnoreResponse =>
        response.bodyAsSource.runWith(Sink.ignore).map(_ => ())
    }

  def close(): Future[Unit] =
    if (mustCloseClient) Future(wsClient.close())
    else Future.successful(())

  private def saveFile(file: File, response: StandaloneWSResponse) = {
    if (!file.exists()) {
      file.getParentFile.mkdirs()
      file.createNewFile()
    }

    response.bodyAsSource.runWith(FileIO.toPath(file.toPath))
  }

  override val responseMonad: MonadError[Future] = new FutureMonad

  private def adjustExceptions[T](request: Request[_, _])(t: => Future[T]): Future[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(exception(request, _))

  private def exception(request: Request[_, _], e: Exception): Option[Exception] =
    e match {
      case e: akka.stream.ConnectionException => Some(new SttpClientException.ConnectException(request, e))
      case e: akka.stream.StreamTcpException =>
        e.getCause match {
          case ee: Exception =>
            exception(request, ee).orElse(Some(new SttpClientException.ReadException(request, e)))
          case _ => Some(new SttpClientException.ReadException(request, e))
        }
      case e: akka.stream.scaladsl.TcpIdleTimeoutException => Some(new SttpClientException.ReadException(request, e))
      case e: Exception                                    => SttpClientException.defaultExceptionToSttpClientException(request, e)
    }
}

object PlayWSClientBackend {
  private def defaultClient(implicit mat: Materializer) =
    AhcWSClient()
  def apply(backendOptions: SttpBackendOptions)(implicit ec: ExecutionContext, mat: Materializer) =
    new FollowRedirectsBackend[Future, Source[ByteString, Any]](
      new PlayWSClientBackend(defaultClient, true, backendOptions)
    )

  def apply(client: WSClient, backendOptions: SttpBackendOptions)(implicit ec: ExecutionContext, mat: Materializer) =
    new FollowRedirectsBackend[Future, Source[ByteString, Any]](
      new PlayWSClientBackend(client, false, backendOptions)
    )
}
