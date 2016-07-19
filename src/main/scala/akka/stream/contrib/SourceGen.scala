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
   */
  def unfoldFlow[S, E, M](seed: S)(flow: Graph[FlowShape[S, (S, E)], M]): Source[E, M] = {

    val fanOut2Shape = new GraphStage[FanOutShape2[(S, E), S, E]] {

      override val shape = new FanOutShape2[(S, E), S, E]("unfoldFlow")
      override def createLogic(attributes: Attributes) = {

        new GraphStageLogic(shape) {

          import shape._

          var pending: (S, E) = seed -> null.asInstanceOf[E]
          var pushedToCycle = false

          setHandler(in, new InHandler {
            override def onPush() = {
              pending = grab(in)
              push(out1, pending._2)
              pushedToCycle = false
            }
          })

          setHandler(out0, new OutHandler {
            override def onPull() = if (!pushedToCycle) {
              push(out0, pending._1)
              pushedToCycle = true
            }
          })

          setHandler(out1, new OutHandler {
            override def onPull() = {
              pull(in)
              if (!pushedToCycle && isAvailable(out0)) {
                push(out0, pending._1)
                pushedToCycle = true
              }
            }
          })
        }
      }
    }

    Source.fromGraph(GraphDSL.create(flow) {
      implicit b =>
        {
          f =>
            {
              import GraphDSL.Implicits._

              val fo2 = b.add(fanOut2Shape)
              fo2.out0 ~> f ~> fo2.in
              SourceShape(fo2.out1)
            }
        }
    })
  }

  /**
   * Create a `Source` that will unfold a value of type `S` by
   * passing it through a flow. The flow should emit an output
   * value of type `O`, that when fed to the unfolding function,
   * generates a pair of the next state `S` and output elements of type `E`.
   */
  def unfoldFlowWith[E, S, O, M](seed: S, flow: Graph[FlowShape[S, O], M])(unfoldWith: O => Option[(E, S)]): Source[E, M] = {

    val fanOut2Shape = new GraphStage[FanOutShape2[O, S, E]] {

      override val shape = new FanOutShape2[O, S, E]("unfoldFlowWith")
      override def createLogic(attributes: Attributes) = {

        new GraphStageLogic(shape) {

          import shape._

          var ePending: E = null.asInstanceOf[E]
          var iPending: S = seed

          override def preStart() = pull(in)

          setHandler(in, new InHandler {
            override def onPush() = {
              val o = grab(in)
              unfoldWith(o) match {
                case None => completeStage()
                case Some((e, i)) => {
                  pull(in)
                  if (isAvailable(out0)) {
                    push(out0, i)
                    iPending = null.asInstanceOf[S]
                  } else iPending = i
                  if (isAvailable(out1)) push(out1, e)
                  else ePending = e
                }
              }
            }
          })

          setHandler(out0, new OutHandler {
            override def onPull() = {
              if (iPending != null) {
                push(out0, iPending)
                iPending = null.asInstanceOf[S]
              }
            }
          })

          setHandler(out1, new OutHandler {
            override def onPull() = {
              if (ePending != null) {
                push(out1, ePending)
                ePending = null.asInstanceOf[E]
              }
              if (isAvailable(out0) && iPending != null) {
                push(out0, iPending)
                iPending = null.asInstanceOf[S]
              }
            }
          })
        }
      }
    }

    Source.fromGraph(GraphDSL.create(flow) {
      implicit b =>
        {
          f =>
            {
              import GraphDSL.Implicits._

              val fo2 = b.add(fanOut2Shape)
              fo2.out0 ~> f ~> fo2.in
              SourceShape(fo2.out1)
            }
        }
    })
  }
}
