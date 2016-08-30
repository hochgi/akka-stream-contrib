/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.contrib

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import com.typesafe.config.ConfigFactory
import org.scalameter._

object Benchmark {

  val standardConfig = config(
    Key.exec.minWarmupRuns -> 10,
    Key.exec.maxWarmupRuns -> 100,
    Key.exec.benchRuns -> 100,
    Key.verbose -> true
  ) withWarmer (new Warmer.Default)

  def main(args: Array[String]): Unit = {

    val fusing = args.headOption.fold("true") { arg =>
      val r = arg.toLowerCase
      require(r.matches("true|false"), "not boolean")
      r
    }

    val seed = {
      if (args.length > 1) args(1).toInt
      else 1487492288
    }

    implicit val system = {
      def config = ConfigFactory.parseString("akka.stream.materializer.auto-fusing=" + fusing)
        .withFallback(ConfigFactory.load())
      ActorSystem("default", config)
    }

    implicit val mat = ActorMaterializer()

    val flow = Flow.fromFunction[Int, (Int, Int)]({
      case 1                    => throw new Exception
      case n: Int if n % 2 == 0 => (n / 2, n)
      case n: Int               => (n * 3 + 1, n)
    }).recover {
      case _ => (1, 1)
    }

    val wrappedTime = standardConfig measure {
      SourceGen.unfoldFlow2(seed)(flow).to(Sink.ignore).run()
    }

    val bareTime = standardConfig measure {
      SourceGen.unfoldFlow(seed)(flow).to(Sink.ignore).run()
    }

    println(s"wrappedTime: $wrappedTime")
    println(s"bareTime: $bareTime")

    scala.concurrent.Await.ready(system.terminate(), scala.concurrent.duration.Duration.Inf)
  }
}
