/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.contrib

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._
import scala.util.{ Try, Success, Failure }
import scala.concurrent.Future

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
    GraphDSL.create(flow) { implicit b => origFlow =>
      import GraphDSL.Implicits._

      val elemCount = b.add(new CountElementsAndInterceptCompletion[(I, S)])
      val mergePref = b.add(MergePreferred[(I, S)](1, true))
      val partition = b.add(PartitionWith[(Try[O], S), Try[O], (I, S)] {
        case (success: Success[O], _) => Left(success)
        case (failedTry, s)           => retryWith(s).fold[Either[Try[O], (I, S)]](Left(failedTry))(Right.apply)
      })
      val coElemCount = b.add(new InterceptElemntsLeavingCycle[Try[O]])
      // format: OFF
      elemCount.out ~> mergePref ~> origFlow ~> partition.in
                                                partition.out0 ~> coElemCount.in
                       mergePref.preferred  <~  partition.out1
      elemCount.in1                         <~                    coElemCount.out1
      // format: ON
      FlowShape(elemCount.in0, coElemCount.out0)
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

  private[akka] class CountElementsAndInterceptCompletion[T] extends GraphStage[FanInShape2[T, Int, T]] {
    override val shape = new FanInShape2[T, Int, T]("countElementsAndInterceptCompletion")
    override def createLogic(attributes: Attributes) = new GraphStageLogic(shape) {

      import shape._
      var elementsInCycleCount = 0

      override def preStart() = pull(in1)

      setHandler(in0, new InHandler {
        override def onPush() = {
          push(out, grab(in0))
          elementsInCycleCount += 1
        }

        override def onUpstreamFinish() = {
          if (elementsInCycleCount == 0)
            completeStage()
        }
      })

      setHandler(in1, new InHandler {
        override def onPush() = {
          elementsInCycleCount += grab(in1)
          if (elementsInCycleCount == 0 && isClosed(in0)) completeStage()
          else pull(in1)
        }
      })

      setHandler(out, new OutHandler {
        override def onPull() =
          pull(in0)
      })
    }
  }

  private[akka] class InterceptElemntsLeavingCycle[T] extends GraphStage[FanOutShape2[T, T, Int]] {
    override val shape = new FanOutShape2[T, T, Int]("interceptElemntsLeavingCycle")
    override def createLogic(attributes: Attributes) = new GraphStageLogic(shape) {

      import shape._
      var elementsPassedCount = 0

      setHandler(in, new InHandler {
        override def onPush() = {
          push(out0, grab(in))
          elementsPassedCount -= 1
          if (isAvailable(out1)) {
            push(out1, elementsPassedCount)
            elementsPassedCount = 0
          }
        }
      })

      setHandler(out0, new OutHandler {
        override def onPull() =
          pull(in)
      })

      setHandler(out1, new OutHandler {
        override def onPull() = {
          if (elementsPassedCount != 0) {
            push(out1, elementsPassedCount)
            elementsPassedCount = 0
          }
        }
      })
    }
  }
}
