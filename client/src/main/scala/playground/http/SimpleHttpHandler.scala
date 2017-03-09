package playground.http

import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

case class HttpActionRequest(
  method: String,
  path: String,
  queryParams: Map[String, List[String]],
  headers: Map[String, String]
)

object HttpActionRequest {
  private[http] def fromHttpReq(httpRequest: HttpRequest): HttpActionRequest = {
    val queryDecoder = new QueryStringDecoder(httpRequest.uri())
    HttpActionRequest(
      method = httpRequest.method().name(),
      path = queryDecoder.path(),
      queryParams = queryDecoder.parameters().asScala.mapValues(_.asScala.toList).toMap,
      headers = httpRequest.headers.iteratorAsString.asScala.map(e => (e.getKey, e.getValue)).toMap
    )
  }
}

case class HttpActionResponse(
  headers: Map[String, String],
  status: Int,
  body: Array[Byte]
) {
  private[http] def toResponse: FullHttpResponse = {
    val r = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.valueOf(status), Unpooled.copiedBuffer(body))
    headers.foreach(h => r.headers.add(h._1, h._2))
    r
  }
}

object HttpActionResponse {

  val TEXT_CONTENT_TYPE = "text/plain; charset=UTF-8"

  def ok(body: Array[Byte], contetType: String): HttpActionResponse =
    HttpActionResponse(
      headers = Map(HttpHeaderNames.CONTENT_TYPE.toString -> contetType),
      status = OK.code(),
      body = body
    )

  def asText(status: Int, body: String = "", contentType: String = TEXT_CONTENT_TYPE): HttpActionResponse =
    HttpActionResponse(
      headers = Map(HttpHeaderNames.CONTENT_TYPE.toString -> contentType),
      status = status,
      body = body.getBytes("UTF-8")
    )

  def internalServerError(msg: String = "Internal Server Error") = asText(INTERNAL_SERVER_ERROR.code(), msg)

  def notFound(msg: String = "Not Found") = asText(NOT_FOUND.code(), msg)
}

case class HttpEndpoint(
  method: String,
  path: String,
  action: (HttpActionRequest) => HttpActionResponse
)

object HttpEndpoint {
  def get(path: String)(action: (HttpActionRequest) => HttpActionResponse): HttpEndpoint = {
    HttpEndpoint("GET", path, action)
  }
}

// Thread unsafety (Do not be @Sharable)
class SimpleHttpHandler(endpoints: Seq[HttpEndpoint]) extends SimpleChannelInboundHandler[Any]{

  private[this] var currentRequest: Option[HttpRequest] = None

  override def channelReadComplete(ctx: ChannelHandlerContext) = {
    ctx.flush()
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: Any) = {
    if (msg.isInstanceOf[HttpRequest]) {
      val req = msg.asInstanceOf[HttpRequest]
      val queryDecoder = new QueryStringDecoder(req.uri())
      currentRequest = Some(req)

      if (HttpUtil.is100ContinueExpected(req)) {
        ctx.write(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE))
      }
    }

    if (msg.isInstanceOf[LastHttpContent]) {
      currentRequest match {
        case None =>
          respond(HttpActionResponse.internalServerError().toResponse, keepAlive = false, ctx)
        case Some(httpReq) =>
          // Decide whether to close the connection or not.
          val keepAlive = HttpUtil.isKeepAlive(httpReq);
          val actionReq = HttpActionRequest.fromHttpReq(httpReq)

          resolveEndpoint(actionReq) match {
            case None =>
              respond(HttpActionResponse.notFound().toResponse, keepAlive, ctx)
            case Some(endpoint) =>
              Try(endpoint.action(actionReq)) match {
                case Success(r) => // 200
                  respond(r.toResponse, keepAlive, ctx)
                case Failure(e) => // 500
                  respond(HttpActionResponse.internalServerError(e.getMessage).toResponse, keepAlive, ctx)
              }
          }
      }
    }
  }

  private[this] def resolveEndpoint(req: HttpActionRequest): Option[HttpEndpoint] = {
    endpoints.find(e => e.method == req.method && e.path == req.path)
  }

  private[this] def respond(response: FullHttpResponse, keepAlive: Boolean, ctx: ChannelHandlerContext) = {
    if (keepAlive) {
      // Add 'Content-Length' header only for a keep-alive connection.
      response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())
      // Add keep alive header as per:
      // - http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    }

    ctx.write(response)

    if (!keepAlive) {
      ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) = {
    cause.printStackTrace()
    ctx.close()
  }
}

object SimpleHttpHandler {
  def newChannelInitializer(endpoints: Seq[HttpEndpoint]): ChannelInitializer[SocketChannel] = {
    new ChannelInitializer[SocketChannel] {
      override def initChannel(c: SocketChannel): Unit = {
        val p = c.pipeline()
        p.addLast(new HttpServerCodec)
        p.addLast(new SimpleHttpHandler(endpoints))
      }
    }
  }
}

