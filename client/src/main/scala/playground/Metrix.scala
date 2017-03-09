package playground

import java.io.{ByteArrayOutputStream, OutputStreamWriter}

import akka.actor.{Actor, ActorLogging, Props}
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client._
import io.prometheus.client.exporter.common.TextFormat
import playground.http.{HttpActionResponse, HttpEndpoint, HttpServerConf, SimpleHttpServer}

import scala.util.Try

object Metrix {
  val counter = Counter.build()
    .name("playground_counter")
    .help("Playground sample counter.")
    .labelNames("key1", "key2")
    .register()

  val gauge = Gauge.build()
    .name("playground_gauge")
    .help("Playground sample gauge.")
    .register()

  val summary = Summary.build()
    .name("playground_summary")
    .help("Playground sample summary.")
    .register()

  val histogram = Histogram.build()
    .name("playground_histgram")
    .help("Playground sample histogram.")
    .register()
}


class MetrixServer(port: Int) extends Actor with ActorLogging {

  val conf = HttpServerConf(port)
  val server = SimpleHttpServer(conf)(
    HttpEndpoint.get("/metrics") { req =>
      //HttpActionResponse.ok("received!!" + req.toString)

      val os = new ByteArrayOutputStream()
      val writer = new OutputStreamWriter(os)
      val body = try {
        TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples())
        writer.flush()
        os.toByteArray
      } finally {
        writer.close()
        os.close()
      }
      HttpActionResponse.ok(body, TextFormat.CONTENT_TYPE_004)
    }
  )

  def receive = {
    case "start" =>
      log.info(s"server start: $port")
      server.start()
      DefaultExports.initialize()
      log.info(s"server started on port $port")
      context.become(started())
    case "stop" =>
      log.warning("server has not been started.")
  }

  def started(): Receive = {
    case "start" =>
      log.warning("server has been started.")
    case "stop" =>
      server.shutdown()
      log.info("server shutdown")
  }
}

object MetrixServer {
  def props(port: Int) = Props(new MetrixServer(port))
}
