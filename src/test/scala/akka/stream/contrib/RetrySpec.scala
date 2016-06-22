/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.contrib

import akka.stream.scaladsl._
import akka.stream.testkit.scaladsl.{ TestSink, TestSource }
import scala.util.{ Try, Failure, Success }

class RetrySpecAutoFusingOn extends { val autoFusing = true } with RetrySpec
class RetrySpecAutoFusingOff extends { val autoFusing = false } with RetrySpec

trait RetrySpec extends BaseStreamSpec {

  val failedElem: Try[Int] = Failure(new Exception("cooked failure"))

  val flow = Flow.fromGraph(GraphDSL.create() { implicit b =>

    val f = Flow[(Int, Int)].map {
      case (i, j) if i % 2 == 0 => (failedElem, j)
      case (i, j)               => (Success(i + 1), j)
    }

    b.add(Retry(f) { s =>
      if (s < 42) Some((s + 1, s + 1))
      else None
    })
  })

  "Retry" should {
    "retry ints according to their parity" in {

      val (source, sink) = TestSource.probe[Int]
        .map(i => (i, i))
        .via(flow)
        .toMat(TestSink.probe)(Keep.both)
        .run()
      sink.request(99)
      source.sendNext(1)
      assert(sink.expectNext() === Success(2))
      source.sendNext(2)
      assert(sink.expectNext() === Success(4))
      source.sendNext(42)
      assert(sink.expectNext() === failedElem)
      source.sendComplete()
      sink.expectComplete()
    }
  }
}
