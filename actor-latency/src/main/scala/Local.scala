import akka.actor._
import akka.pattern.gracefulStop
import akka.pattern.AskTimeoutException

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

case class Start(it: Int)
case class ToDo(it: Int, r: Result)
case class Mess(it: Int, start: Long, r: Result)
case class Acknowledgement(it: Int, start: Long, messageDuration: Duration, r: Result)
case class Result(refIt: Int, round: Duration, hop: Duration)
case object CantUnderstand
case object Stop

class LocalActor extends Actor {
  val remoteActor = context.actorSelection("akka.tcp://remote@127.0.0.1:5150/user/remote-actor")
  def receive = {
    case Start(it) => self ! ToDo(it, Result(it, 0.millis, 0.millis))
    case ToDo(0, result) =>
      remoteActor ! Stop
      println(f"Average latencies for ${result.refIt} messages:" +
        f" ${result.round / result.refIt} A->B->A / ${result.hop / result.refIt} A->B")
      shutProcessDown()
    case ToDo(it, result) =>
      remoteActor ! Mess(it, System.currentTimeMillis, result)
    case Acknowledgement(it, start, messageDuration, result) =>
      self ! ToDo(
        it - 1,
        Result(
          result.refIt,
          result.round + messageDuration + (System.currentTimeMillis - start).millis,
          result.hop + messageDuration
        )
      )
    case _ =>
      sender ! CantUnderstand
      shutProcessDown()
  }

  def shutProcessDown() = {
    try {
      // Todo not sure how to properly stop the system
      val stopped: Future[Boolean] = gracefulStop(self, 5 seconds)
      Await.result(stopped, 6 seconds)
    } catch {
      case e: AskTimeoutException => println("Couldn't stop")
    }
  }
}

object Local {
  def main(args: Array[String]) {
    measure(args.toList.headOption)
  }

  def measure(it: Option[String]) = {
    val system = ActorSystem("local")
    val localActor = system.actorOf(Props[LocalActor], name = "local-actor")
    it match {
      case Some(n) => localActor ! Start(n.toInt)
      case None => localActor ! Start(10)
    }
  }
}