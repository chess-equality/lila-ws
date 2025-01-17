package lila.ws

import akka.actor.typed.{ ActorSystem, Scheduler }
import com.google.inject.{ AbstractModule, Guice, Provides }
import com.typesafe.config.{ Config, ConfigFactory }
import javax.inject._
import scala.annotation.unused
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import util.Util.nowSeconds

object Boot extends App {

  private val injector = Guice.createInjector(new AbstractModule {
    @Provides def config: Config = ConfigFactory.load
    @Provides def clientSystem: ClientSystem = ActorSystem(Clients.behavior, "clients")
    @Provides def scheduler: Scheduler = clientSystem.scheduler
    @Provides def executionContext: ExecutionContext = clientSystem.executionContext
  })

  injector.getInstance(classOf[LilaWsServer]).start
}

@Singleton
final class LilaWsServer @Inject() (
    nettyServer: netty.NettyServer,
    @unused handlers: LilaHandler, // must eagerly instanciate!
    monitor: Monitor,
    scheduler: Scheduler
)(implicit ec: ExecutionContext) {

  def start: Unit = {

    monitor.start

    scheduler.scheduleWithFixedDelay(30.seconds, 7211.millis) { () =>
      Bus.publish(_.all, ipc.ClientCtrl.Broom(nowSeconds - 30))
    }

    nettyServer.start // blocks
  }
}

object LilaWsServer {

  val connections = new java.util.concurrent.atomic.AtomicInteger
}
