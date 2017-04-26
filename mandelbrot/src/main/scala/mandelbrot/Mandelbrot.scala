package mandelbrot

import java.awt.Color
import scala.collection.mutable
import scala.swing._
import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.actor.Props
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import javax.swing.ImageIcon
import mandelbrot.ArithmeticComplex._
import scala.concurrent.Future
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Await
import scala.io.StdIn


object Mandelbrot extends App {

  abstract class MandelbrotMessage
  case object Start extends MandelbrotMessage
  case class Calcul(x_deb:Int, y_fin:Int) extends MandelbrotMessage
  case class Resultat(map: mutable.Map[(Int, Int), Int]) extends MandelbrotMessage
  case class Fini(map: mutable.Map[(Int, Int), Int]) extends MandelbrotMessage

  
	val x_min = -2.0
	val x_max =  1.0
	val y_min = -1.0
	val y_max =  1.0
  	val iter_max = 1000
  
  val width= 1024
  val height = 768
  
  class Master(nbActeurs:Int, nbThreads:Int) extends Actor{
    
    var mandelbrot_map: mutable.Map[(Int, Int), Int] = mutable.Map()
    var completude = 0
    val start: Long = System.currentTimeMillis()



    val workerPool = context.actorOf(
        Props[Worker].withRouter(RoundRobinPool(nbActeurs)), name = "workerPool")
        
    def getColor(iter:Int):Color ={
      new Color(0, (iter/2) % 256, (iter)%256)
    }
    
	  def receive = {
	    case Start => {
	      if ((width % nbThreads) != 0){
	        println("Choisir un multiple de la largeur de la fenetre")
	        self ! PoisonPill
	        sender ! 0
	      }
	      else {
	        sender ! 1
	        val tailleThread = width/nbThreads
	        for (i<-0 until nbThreads){
	          workerPool ! Calcul(i*tailleThread, tailleThread)
	        }
	      }
	    }
	    case Resultat(map) =>{
	      mandelbrot_map ++= map
	      completude +=1
	      if (completude == nbThreads){
	      	val temps = (System.currentTimeMillis()-start)
	      	println("Calculer en "+temps)
	        self ! Fini(mandelbrot_map)
	      }
	    }
	    case Fini(map_termine) =>{
	      val mandelbrot_BitMap = new RgbBitmap(width, height)
	      for (x <- 0 until width){
          for (y <- 0 until height){
            mandelbrot_BitMap.setPixel(x, y, getColor(map_termine(x,y)))
          }
        }
	      val mainframe=new MainFrame(){
	        title="Mandelbort"; visible=true
          contents=new Label(){
	          icon=new ImageIcon(mandelbrot_BitMap.image)
	        }
        }
	    }
	  }
	}
  
  
  class Worker extends Actor{
      
    def mandelbrot_calcul(x_d:Int, x_f:Int):mutable.Map[(Int,Int), Int] = {
      var mandelbrot_map_partielle: mutable.Map[(Int, Int), Int] = mutable.Map()
      
      val zoom_x=width/(x_max-x_min)
      val zoom_y=height/(y_max-y_min)
      
      for (y <- 0 until height){
        for (x <- x_d until x_d+x_f){
          val c=Complex(x/zoom_x + x_min, y/zoom_y + y_min)
          
          var iter = 0
          var z=Complex()
          while(z.abs < 4 && iter < iter_max){
            z=z*z+c;
            iter+=1
          }
          mandelbrot_map_partielle += ((x,y) -> iter)
        }
      }
      mandelbrot_map_partielle
    }
    
    def receive = {
      case Calcul(x_deb, x_fin) =>{
        sender ! Resultat(mandelbrot_calcul(x_deb, x_fin))
      }
    }
  
  }
  
  val system = ActorSystem("mandelbrot")
  val master = system.actorOf(Props(new Master(128,128)), name="master")
  
  implicit val timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))
  val future:Future[Int] = ask(master, Start).mapTo[Int]
  val result = Await.result(future, timeout.duration).asInstanceOf[Int]
  
  if (result == 0){
    system.terminate()
  }
  else {
    println("Appuyez sur une touche pour finir")
    StdIn.readLine()
    system.terminate()
  }
}