/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.contrib

import akka.Done
import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import scala.util.{ Try, Success, Failure }
import scala.concurrent.Future
import scala.annotation.unchecked.uncheckedVariance
import scala.collection.immutable

/**
 * This object defines a factory methods for retry operaions.
 */
object Retry {

  /**
   * Factory for RetrySingle flow
   *
   * @param flow the flow to retry
   * @param retryWith if output was failure, we can optionaly recover from it,
   *        and retry with a new pair of input & new state we get from this function.
   * @tparam I input elements type
   * @tparam O output elements type
   * @tparam S state to create a new (input,state): (I,S) to retry with
   * @tparam M materialized value type
   * @return `Graph[FlowShape[(I,S),Try[O]],M]` instance
   */
  def apply[I, O, S, M](flow: Graph[FlowShape[(I, S), (Try[O], S)], M])(retryWith: S => Option[(I, S)]): Graph[FlowShape[(I, S), Try[O]], M] = {
    GraphDSL.create(flow) { implicit b => origFlw =>
      import GraphDSL.Implicits._

      val tracker = b.add(new TrackNumberOfElementsInCycle[(I, S)])
      val mrgPref = b.add(MergePreferred[(I, S)](1))
      val coTrack = b.add(new CoTrackNumberOfElementsInCycle[(Try[O], S), Try[O], (I, S)]({
        case (f: Failure[O], s) => retryWith(s).fold[Either[Try[O], (I, S)]](Left(f))(Right.apply)
        case (success, _)       => Left(success)
      }))

      tracker.out2 ~> coTrack.trackCompletion
      tracker.out1 ~> mrgPref ~> origFlw ~> coTrack.in
      mrgPref.preferred <~ coTrack.cycle
      tracker.in2 <~ coTrack.signal

      FlowShape(tracker.in1, coTrack.out)
    }
  }

  /**
   * Factory for RetryAsync flow
   *
   * @param flow the flow to retry
   * @param retryWith if output was failure, we can optionaly recover from it,
   *        and retry with a new pair of input & new state we get from this function.
   * @tparam I input elements type
   * @tparam O output elements type
   * @tparam S state to create a new (input,state): (I,S) to retry with
   * @tparam M materialized value type
   * @return `Graph[FlowShape[(I,S),Try[O]],M]` instance
   */
  def async[I, O, S, M](flow: Graph[FlowShape[(I, S), (Try[O], S)], M])(retryWith: S => Future[Option[(I, S)]]): Graph[FlowShape[(I, S), Try[O]], M] = {
    ???
  }

  /**
   * Factory for RetryConcat flow
   *
   * @param flow the flow to retry
   * @param retryWith if output was failure, we can optionaly recover from it,
   *        and retry with a sequence of input & new state pairs we get from this function.
   * @tparam I input elements type
   * @tparam O output elements type
   * @tparam S state to create a new (input,state): (I,S) to retry with
   * @tparam M materialized value type
   * @return `Graph[FlowShape[(I,S),Try[O]],M]` instance
   */
  def concat[I, O, S, M](flow: Graph[FlowShape[(I, S), (Try[O], S)], M])(retryWith: S => Seq[(I, S)]): Graph[FlowShape[(I, S), Try[O]], M] = {
    ???
  }

  /**
   * Factory for RetryConcat flow
   *
   * @param flow the flow to retry
   * @param retryWith if output was failure, we can optionaly recover from it,
   *        and retry with a sequence of input & new state pairs we get from this function.
   * @tparam I input elements type
   * @tparam O output elements type
   * @tparam S state to create a new (input,state): (I,S) to retry with
   * @tparam M materialized value type
   * @return `Graph[FlowShape[(I,S),Try[O]],M]` instance
   */
  def concatAsync[I, O, S, M](flow: Graph[FlowShape[(I, S), (Try[O], S)], M])(retryWith: S => Future[Seq[(I, S)]]): Graph[FlowShape[(I, S), Try[O]], M] = {
    ???
  }

  private[akka] class TrackNumberOfElementsInCycle[I] extends GraphStage[BidiShape[I, I, Int, Done]] {

    override val shape = BidiShape[I, I, Int, Done](Inlet[I]("tracker.source"), Outlet[I]("tracker.sink"), Inlet[Int]("tracker.add"), Outlet[Done]("tracker.done"))
    override def createLogic(attributes: Attributes) = new GraphStageLogic(shape) {
      import shape._
      private var count: Int = 0

      override def preStart() = pull(in2)

      setHandler(in1, new InHandler {
        override def onPush() = {
          push(out1, grab(in1))
          count += 1
        }

        override def onUpstreamFinish() = {
          if (count == 0)
            completeStage()
        }
      })

      setHandler(in2, new InHandler {
        override def onPush() = {
          count += grab(in2)
          if (count == 0 && isClosed(in1)) completeStage()
          else pull(in2)
        }
      })

      setHandler(out1, new OutHandler {
        override def onPull() =
          if (!isClosed(in1))
            pull(in1)
      })

      setHandler(out2, new OutHandler {
        override def onPull() = {
          println("Should not happen (TrackNumberOfElementsInCycle)")
          push(out2, Done)
        }
      })
    }
  }

  private[akka] class TrackerShape[-In, +Out, +Cycle, +Signal](
    val in:              Inlet[In @uncheckedVariance],
    val out:             Outlet[Out @uncheckedVariance],
    val cycle:           Outlet[Cycle @uncheckedVariance],
    val trackCompletion: Inlet[Done],
    val signal:          Outlet[Signal @uncheckedVariance]
  ) extends Shape {
    //#implementation-details-elided
    override val inlets: immutable.Seq[Inlet[_]] = List(in, trackCompletion)
    override val outlets: immutable.Seq[Outlet[_]] = List(out, cycle, signal)

    override def deepCopy(): TrackerShape[In, Out, Cycle, Signal] =
      new TrackerShape(in.carbonCopy(), out.carbonCopy(), cycle.carbonCopy(), trackCompletion.carbonCopy(), signal.carbonCopy())

    override def copyFromPorts(inlets: immutable.Seq[Inlet[_]], outlets: immutable.Seq[Outlet[_]]): Shape = {
      require(inlets.size == 2, s"proposed inlets [${inlets.mkString(", ")}] do not fit TrackerShape")
      require(outlets.size == 3, s"proposed outlets [${outlets.mkString(", ")}] do not fit TrackerShape")
      new TrackerShape(inlets(0), outlets(0), outlets(1), inlets(1).asInstanceOf[Inlet[Done]], outlets(2))
    }
  }

  private[akka] class CoTrackNumberOfElementsInCycle[In, Out, Cycle](outOrCycle: In => Either[Out, Cycle]) extends GraphStage[TrackerShape[In, Out, Cycle, Int]] {

    override val shape = new TrackerShape(Inlet[In]("track.in"), Outlet[Out]("track.out"), Outlet[Cycle]("trackCycle.out"), Inlet[Done]("trackDone.in"), Outlet[Int]("trackCount.out"))
    override def createLogic(attributes: Attributes) = new GraphStageLogic(shape) {

      import shape._

      private var pending: Either[Out, Cycle] = null
      private var counter: Int = 0

      private def signalTracker() = {
        counter -= 1
        if (isAvailable(signal)) {
          push(signal, counter)
          counter = 0
        }
      }

      setHandler(in, new InHandler {
        override def onPush() = {
          val elem = grab(in)
          outOrCycle(elem) match {
            case Left(o) if isAvailable(out) => {
              push(out, o)
              signalTracker()
              if (isAvailable(cycle)) pull(in)
            }
            case Right(o) if isAvailable(cycle) => {
              push(cycle, o)
              if (isAvailable(out)) pull(in)
            }
            case either => pending = either
          }
        }
      })

      setHandler(out, new OutHandler {
        override def onPull() = if (pending ne null) pending.left.foreach { o =>
          push(out, o)
          signalTracker()
          pending = null
          if (isAvailable(cycle)) pull(in)
        }
        else if (!hasBeenPulled(in)) pull(in)
      })

      setHandler(cycle, new OutHandler {
        override def onPull() = if (pending ne null) pending.right.foreach { c =>
          push(cycle, c)
          pending = null
          if (isAvailable(out)) pull(in)
        }
        else if (!hasBeenPulled(in)) pull(in)
      })

      setHandler(trackCompletion, new InHandler {
        override def onPush() = println("Should not happen (CoTrackNumberOfElementsInCycle)")
        override def onUpstreamFinish() = complete(cycle)
      })

      setHandler(signal, new OutHandler {
        override def onPull() = {
          if (counter != 0) {
            push(signal, counter)
            counter = 0
          }
        }
      })
    }
  }
}
