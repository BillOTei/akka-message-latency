import akka.actor._
import akka.pattern.AskTimeoutException
import akka.pattern.gracefulStop

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

case class Mess(it: Int, start: Long, r: Result)
case class Acknowledgement(it: Int, start: Long, messageDuration: Duration, r: Result)
case class Result(refIt: Int, round: Duration, hop: Duration)
case object CantUnderstand
case object Stop

object Remote extends App  {
  val system = ActorSystem("remote")
  val remoteActor = system.actorOf(Props[RemoteActor], name = "remote-actor")
}

class RemoteActor extends Actor {
  def receive = {
    case Mess(it, start, result) => println("got a message: %d".format(start))
      val now = System.currentTimeMillis
      sender ! Acknowledgement(it, now, (now - start).millis, result)
    case Stop => shutProcessDown()
    case _ =>
      sender ! CantUnderstand
      context.stop(self)
  }

  def shutProcessDown() = {
    try {
      val stopped: Future[Boolean] = gracefulStop(self, 5 seconds)
      Await.result(stopped, 6 seconds)
    } catch {
      case e: AskTimeoutException => println("Couldn't stop")
    }
  }
}