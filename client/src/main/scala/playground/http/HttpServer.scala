package playground.http

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.{LogLevel, LoggingHandler}

case class HttpServerConf(
  port: Int,
  bossNum: Int = 1,
  workerNum: Int = 1,
  backlog: Integer = 128,   // requested maximum length of the queue of incoming connections.
  logLevel: LogLevel = LogLevel.INFO
)

class SimpleHttpServer(conf: HttpServerConf, endpoints: Seq[HttpEndpoint]) {
  private[this] val bossGroup: EventLoopGroup = new NioEventLoopGroup(conf.bossNum)
  private[this] val workerGroup: EventLoopGroup = new NioEventLoopGroup(conf.workerNum)
  @volatile private[this] var channel: Channel = null

  def start(): Unit = {
    synchronized {
      if (channel == null) {
        // Configure the server.
        val b = new ServerBootstrap()
        b.option(ChannelOption.SO_BACKLOG, conf.backlog)
        b.group(bossGroup, workerGroup)
          .channel(classOf[NioServerSocketChannel])
          .handler(new LoggingHandler(conf.logLevel))
          .childHandler(SimpleHttpHandler.newChannelInitializer(endpoints))
        channel = b.bind(conf.port).sync().channel()
      } else {
        throw new IllegalStateException("Can't start() after shutdown() called.")
      }
    }
  }

  def shutdown(): Unit = {
    synchronized {
      if (channel != null) channel.close().await(10000)
      workerGroup.shutdownGracefully().await(10000)
      bossGroup.shutdownGracefully().await(10000)
    }
  }

  def isRunning() = channel != null
}

object SimpleHttpServer {
  def apply(conf: HttpServerConf)(endpoints: HttpEndpoint*): SimpleHttpServer = {
    new SimpleHttpServer(conf, endpoints)
  }
}
