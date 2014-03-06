/*
 * Copyright (c) 2014 Plausible Labs Cooperative, Inc.
 * All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package coop.plausible.nx

import coop.plausible.nx.internal.Macros

/**
 * Type-safe mapping from exception-throwing expressions to Either types.
 *
 * This is intended to be a type-safe checked-exception-enabled replacement for the standard `scala.util.Try` and
 * `scala.util.control.Exception`
 */
object Try {
  /**
   * Execute `expr`, catching all checked exceptions.
   *
   * Example usage:
   *
   * {{{
   *   val result: Either[UnknownHostException, InetAddress] = Try[UnknownHostException] {
   *     java.net.InetAddress.getByName("www.example.org")
   *   }
   * }}}
   *
   * If type `E` is not wide enough to catch all checked exceptions in `expr`, a compiler type
   * error will be triggered.
   *
   * @tparam E The exception type to catch.
   * @return A new `Try` instance that may be applied to any expression throwing checked exceptions of type `E`.
   *
   * @note This API is considered experimental and subject to change; specifically, it may be extended to support
   *       compiler inference of `E` based on the target expression.
   */
  def apply[E <: Throwable]: Try[E] = new Try()
}

/**
 * Type-safe mapping from exception-throwing expressions to Either types. Try instances should be instantiated via
 * [[Try.apply]]
 *
 * This is intended to be a type-safe replacement for the standard `scala.util.Try` and
 * `scala.util.control.Exception`
 *
 * @note This API is considered experimental and subject to change; this class may be removed.
 */
class Try[E <: Throwable] {
  import scala.language.experimental.macros

  /**
   * Execute `expr`, catching all checked exceptions.
   *
   * If type `E` is not wide enough to catch all checked exceptions in `expr`, a compiler type
   * error will be triggered.
   *
   * @param expr The expression to execute.
   * @tparam T The expression's result type.
   * @return The expression result or the thrown exception.
   */
  def apply[T] (expr: T): Either[E, T] = macro Macros.Try_macro[E, T]
}
