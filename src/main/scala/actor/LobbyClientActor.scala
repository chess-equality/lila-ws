package lila.ws

import akka.actor.typed.scaladsl.{ Behaviors, ActorContext }
import akka.actor.typed.{ ActorRef, Behavior, PostStop }
import play.api.libs.json.JsValue

import ipc._
import sm._

object LobbyClientActor {

  import ClientActor._

  case class State(
      idle: Boolean = false,
      site: ClientActor.State = ClientActor.State()
  )

  def start(deps: Deps): Behavior[ClientMsg] = Behaviors.setup { ctx =>
    import deps._
    onStart(deps, ctx)
    deps.user foreach { u =>
      deps.queue(_.user, UserSM.ConnectSilently(u, ctx.self))
    }
    queue(_.connect, LilaIn.ConnectSri(req.sri, user.map(_.id)))
    bus.subscribe(ctx.self, _.lobby)
    apply(State(), deps)
  }

  private def apply(state: State, deps: Deps): Behavior[ClientMsg] = Behaviors.receive[ClientMsg] { (ctx, msg) =>

    import deps._

    def forward(payload: JsValue): Unit = queue(_.lobby, LilaIn.TellSri(req.sri, user.map(_.id), payload))

    msg match {

      case ctrl: ClientCtrl => ClientActor.socketControl(state.site, deps.flag, ctrl)

      case in: ClientIn.NonIdle =>
        if (!state.idle) clientIn(in)
        Behavior.same

      case in: ClientIn =>
        clientIn(in)
        Behavior.same

      case ClientCtrl.Disconnect =>
        Behavior.stopped

      case msg: ClientOut.Ping =>
        clientIn(LobbyPongStore.get)
        apply(state.copy(site = sitePing(state.site, deps, msg)), deps)

      case ClientOut.Forward(payload) =>
        forward(payload)
        Behavior.same

      case ClientOut.Idle(value, payload) =>
        forward(payload)
        apply(state.copy(idle = value), deps)

      // default receive (site)
      case msg: ClientOutSite =>
        val siteState = globalReceive(state.site, deps, ctx, msg)
        if (siteState == state.site) Behavior.same
        else apply(state.copy(site = siteState), deps)
    }

  }.receiveSignal {
    case (ctx, PostStop) =>
      import deps._
      onStop(state.site, deps, ctx)
      queue(_.disconnect, LilaIn.DisconnectSri(req.sri))
      Behaviors.same
  }
}
