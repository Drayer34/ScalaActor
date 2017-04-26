package threadRing

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ActorSystem

sealed trait ThreadRingMessage
case class StartMessage(next:ActorRef) extends ThreadRingMessage
case class GameMessage(n:Int) extends ThreadRingMessage 


class ThreadRing(id:Int) extends Actor {
  
  private var nextThread:ActorRef = null
  
  import context._
  
    def receive = {
      case StartMessage(premier) => {
        if (id == 503){
          nextThread = premier
          println("generation over !")
          nextThread ! GameMessage(1000)
        }else {
          nextThread = context.actorOf(Props(new ThreadRing(id+1)), name=s"threadRing_${id+1}")
          nextThread ! StartMessage(premier)
        }
      }
      
      case GameMessage(n) => {
        if (n==0){
          println (s"last thread :$id")
        }else{
          nextThread ! GameMessage(n-1)
        }
      } 
    }
}

object threadRingApp extends App {
  
  val system = ActorSystem("threadRingSystem")
  val firstThread =  system.actorOf(Props(new ThreadRing(1)), name="threadRing_1")

  firstThread ! StartMessage(firstThread)
  
}