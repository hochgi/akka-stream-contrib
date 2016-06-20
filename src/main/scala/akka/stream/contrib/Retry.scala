/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.stream.contrib

import akka.stream._
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
    ???
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
}
