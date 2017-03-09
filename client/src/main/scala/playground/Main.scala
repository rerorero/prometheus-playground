package playground

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.{Duration, _}

class Sampler extends Actor with ActorLogging {
  import context.dispatcher
  private[this] val tick_1 = context.system.scheduler.schedule(
    1 second,
    1 second,
    self,
    "tick_1"
  )
  private[this] val tick_3 = context.system.scheduler.schedule(
    1 second,
    3 second,
    self,
    "tick_3"
  )

  def receive = {
    case "tick_1" =>
      log.info("tick 1")
      Metrix.counter.labels("tick1", "foo").inc()

    case "tick_3" =>
      log.info("tick 3")
      Metrix.counter.labels("tick3", "foo").inc()
  }

  override def postStop(): Unit = {
    tick_1.cancel()
    tick_3.cancel()
  }
}

object Sampler {
  def props() = Props(new Sampler)
}

object Main {
  implicit val timeout = Timeout(5000, TimeUnit.MILLISECONDS)
  val system = ActorSystem("playground")
  implicit val ec = system.dispatcher

  def main(args: Array[String]): Unit = {
    val sampler = system.actorOf(Sampler.props())
    val port = args.headOption.map(_.toInt).getOrElse(8988)
    val server = system.actorOf(MetrixServer.props(port))

    sys.addShutdownHook {
      server ? "stop"
      Await.result(system.terminate(), Duration.Inf)
    }

    server ! "start"
  }
}
