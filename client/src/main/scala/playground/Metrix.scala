package playground

import akka.actor.{Actor, ActorLogging, Props}
import io.prometheus.client.exporter.MetricsServlet
import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.{Counter, Gauge, Histogram, Summary}
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.{ServletContextHandler, ServletHolder}

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
  val server = new Server(port)
  val handler = new ServletContextHandler()

  def receive = {
    case "start" =>
      handler.setContextPath("/")
      server.setHandler(handler)
      handler.addServlet(new ServletHolder(new MetricsServlet()), "/metrics")
      DefaultExports.initialize()
      server.start()
      log.info(s"server started on port $port")
      context.become(started())
    case "stop" =>
      log.warning("server has not been started.")
  }

  def started(): Receive = {
    case "start" =>
      log.warning("server has been started.")
    case "stop" =>
      log.info("server to stop")
      server.stop()
      log.info("server stopped")
  }
}

object MetrixServer {
  def props(port: Int) = Props(new MetrixServer(port))
}
