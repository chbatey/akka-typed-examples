package info.batey.akka.re

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.persistence.multidc.{PersistenceMultiDcSettings, ReplicatedEventContext, SelfEventContext}
import akka.persistence.multidc.scaladsl._

import scala.io.StdIn

sealed trait Command
case class Deposit(amount: Int) extends Command
case class Withdraw(amount: Int) extends Command

sealed trait Event
case class Deposited(amount: Int) extends Event
case class Withdrawn(amount: Int) extends Event

case class State(balance: Int)

class Account extends ReplicatedEntity[Command, Event, State] {

  def initialState: State = State(0)

  override def detectConcurrentUpdates = true

  override def commandHandler = CommandHandler { (cmd, state, ctx) =>
    cmd match {
      case Deposit(amount) =>
        Effect.persist(Deposited(amount))
      case Withdraw(amount) =>
        Effect.persist(Withdrawn(amount))
      case _ =>
        Effect.unhandled
    }
  }

  def applyEvent(event: Event, state: State) = {
    val newState = event match {
      case Deposited(a) => state.copy(state.balance + a)
      case Withdrawn(a) => state.copy(state.balance - a)
    }
    println(newState)
    newState
  }

  override def recoveryCompleted(state: State, ctx: ActorContext) = {
    println("Recovery completed: " + ctx)
    super.recoveryCompleted(state, ctx)
  }

  override def applySelfEvent(event: Event, state: State, ctx: SelfEventContext) =
    super.applySelfEvent(event, state, ctx)

  override def applyReplicatedEvent(event: Event, state: State, ctx: ReplicatedEventContext) =
    super.applyReplicatedEvent(event, state, ctx)

  //  override def applySelfEvent(event: Event, state: State, ctx: ReplicatedEntity.SelfEventContext) = {
  //    println("Self event: " + ctx)
  //    applyEvent(event, state)
  //  }


  //  override def applyReplicatedEvent(event: Event, state: State, ctx: ReplicatedEntity.ReplicatedEventContext) = {
  //    println("Replicated event: " + ctx)
  //    applyEvent(event, state)
  //  }

}

object ReplicatedEntityApp extends App {
  val system = ActorSystem("ClusterSystem")
  val cluster = Cluster(system)

  val props = ReplicatedEntity.props(
    persistenceIdPrefix = "account",
    entityId = Option("account1"),
    entityFactory = () => new Account,
    settings = PersistenceMultiDcSettings(system))

  val account1 = system.actorOf(props)

  var input = ""
  while (input != ":q") {
    input = StdIn.readLine()
    if (!input.equals(":q")) {
      val amount = input.toInt
      account1 ! Deposit(amount)
    }
  }

  system.terminate()
}
