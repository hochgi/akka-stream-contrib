/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.contrib

import akka.stream._
import akka.stream.scaladsl._
import akka.stream.stage._

/**
 * Source factory methods are placed here
 */
object SourceGen {

  /**
   * Create a `Source` that will unfold a value of type `S` by
   * passing it through a flow. The flow should emit a
   * pair of the next state `S` and output elements of type `E`.
   * Source completes when the flow completes.
   *
   * IMPORTANT CAVEAT:
   * The given flow must not change the number of elements passing through it (i.e. it should output
   * exactly one element for every received element). Ignoring this, will have an unpredicted result,
   * and may result in a deadlock.
   */
  def unfoldFlow[S, E, M](seed: S)(flow: Graph[FlowShape[S, (S, E)], M]): Source[E, M] = {

    val fanOut2Stage = fanOut2unfoldingStage[(S, E), S, E](seed, {
      (handleStateElement, grabIn, completeStage) =>
        new InHandler {
          override def onPush() = {
            val (s, e) = grabIn()
            handleStateElement(s, e)
          }
        }
    })

    unfoldFlowGraph(fanOut2Stage, flow)
  }

  /**
   * Create a `Source` that will unfold a value of type `S` by
   * passing it through a flow. The flow should emit an output
   * value of type `O`, that when fed to the unfolding function,
   * generates a pair of the next state `S` and output elements of type `E`.
   *
   * IMPORTANT CAVEAT:
   * The given flow must not change the number of elements passing through it (i.e. it should output
   * exactly one element for every received element). Ignoring this, will have an unpredicted result,
   * and may result in a deadlock.
   */
  def unfoldFlowWith[E, S, O, M](seed: S, flow: Graph[FlowShape[S, O], M])(unfoldWith: O => Option[(S, E)]): Source[E, M] = {

    val fanOut2Stage = fanOut2unfoldingStage[O, S, E](seed, {
      (handleStateElement, grabIn, completeStage) =>
        new InHandler {
          override def onPush() = {
            val o = grabIn()
            unfoldWith(o) match {
              case None         => completeStage()
              case Some((s, e)) => handleStateElement(s, e)
            }
          }
        }
    })

    unfoldFlowGraph(fanOut2Stage, flow)
  }

  private[akka] def unfoldFlowGraph[E, S, O, M](
    fanOut2Stage: GraphStage[FanOutShape2[O, S, E]],
    flow:         Graph[FlowShape[S, O], M]
  ): Source[E, M] = Source.fromGraph(GraphDSL.create(flow) {
    implicit b =>
      {
        f =>
          {
            import GraphDSL.Implicits._

            val fo2 = b.add(fanOut2Stage)
            fo2.out0 ~> f ~> fo2.in
            SourceShape(fo2.out1)
          }
      }
  })

  private[akka] def fanOut2unfoldingStage[O, S, E](seed: S, withInHandler: ((S, E) => Unit, () => O, () => Unit) => InHandler) = new GraphStage[FanOutShape2[O, S, E]] {

    override val shape = new FanOutShape2[O, S, E]("unfoldFlow")
    val feedback = shape.out0
    val output = shape.out1
    val nextElem = shape.in

    override def createLogic(attributes: Attributes) = {

      new GraphStageLogic(shape) {

        var pending: S = seed
        var pushedToCycle = false

        setHandler(nextElem, withInHandler((s, e) => {
          pending = s
          push(output, e)
          pushedToCycle = false
        }, () => grab(nextElem), completeStage))

        setHandler(feedback, new OutHandler {
          override def onPull() = if (!pushedToCycle && isAvailable(output)) {
            push(feedback, pending)
            pending = null.asInstanceOf[S]
            pushedToCycle = true
          }

          override def onDownstreamFinish() = {
            //Do Nothing, intercept completion as downstream
          }
        })

        setHandler(output, new OutHandler {
          override def onPull() = {
            pull(nextElem)
            if (!pushedToCycle && isAvailable(feedback)) {
              push(feedback, pending)
              pending = null.asInstanceOf[S]
              pushedToCycle = true
            }
          }
        })
      }
    }
  }
}
