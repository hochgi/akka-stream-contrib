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

          var pending: S = seed
          var pushedToCycle = false

          setHandler(in, new InHandler {
            override def onPush() = {
              val (s, e) = grab(in)
              pending = s
              push(out1, e)
              pushedToCycle = false
            }
          })

          setHandler(out0, new OutHandler {
            override def onPull() = if (!pushedToCycle && isAvailable(out1)) {
              push(out0, pending)
              pending = null.asInstanceOf[S]
              pushedToCycle = true
            }
          })

          setHandler(out1, new OutHandler {
            override def onPull() = {
              pull(in)
              if (!pushedToCycle && isAvailable(out0)) {
                push(out0, pending)
                pending = null.asInstanceOf[S]
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
  def unfoldFlowWith[E, S, O, M](seed: S, flow: Graph[FlowShape[S, O], M])(unfoldWith: O => Option[(S, E)]): Source[E, M] = {

    val fanOut2Shape = new GraphStage[FanOutShape2[O, S, E]] {

      override val shape = new FanOutShape2[O, S, E]("unfoldFlowWith")
      override def createLogic(attributes: Attributes) = {

        new GraphStageLogic(shape) {

          import shape._

          var pending: S = seed
          var pushedToCycle = false

          setHandler(in, new InHandler {
            override def onPush() = {
              val o = grab(in)
              unfoldWith(o) match {
                case None => completeStage()
                case Some((s, e)) => {
                  pending = s
                  push(out1, e)
                  pushedToCycle = false
                }
              }
            }
          })

          setHandler(out0, new OutHandler {
            override def onPull() = if (!pushedToCycle && isAvailable(out1)) {
              push(out0, pending)
              pending = null.asInstanceOf[S]
              pushedToCycle = true
            }
          })

          setHandler(out1, new OutHandler {
            override def onPull() = {
              pull(in)
              if (!pushedToCycle && isAvailable(out0)) {
                push(out0, pending)
                pending = null.asInstanceOf[S]
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
}
