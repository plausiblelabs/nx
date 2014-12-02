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
  import scala.language.experimental.macros

  /**
   * Execute `expr`, catching all checked exceptions.
   *
   * Example usage:
   *
   * {{{
   *   val result: Either[UnknownHostException, InetAddress] = Try {
   *     java.net.InetAddress.getByName("www.example.org")
   *   }
   * }}}
   *
   * @tparam E The expression's exception type.
   * @tparam T The expression's result type.
   * @return A new `Try` instance that may be applied to any expression throwing checked exceptions of type `E`.
   */
  def apply[E <: Throwable, T] (expr: T): Either[Throwable, T] = macro Macros.Try_macro[E, T]
}
