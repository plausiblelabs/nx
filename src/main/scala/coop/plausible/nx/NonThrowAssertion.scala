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

import coop.plausible.nx.internal.Macro

/**
 * May be applied to checked exception-throwing expressions to assert that the given exception is not thrown.
 *
 * @see [[NX.assertNonThrow]]
 *
 * @note This API is considered experimental and subject to change; this class may be removed.
 */
class NonThrowAssertion[E <: Throwable] {
  import scala.language.experimental.macros

  /**
   * Assert that the given checked exception `T` is not thrown by `expr`. If the exception is thrown,
   * it will be wrapped in a `java.lang.AssertionError` and rethrown.
   *
   * @param expr The expression that may throw exceptions of type `E`.
   * @tparam T The expression result type.
   * @return The expression result.
   */
  def apply[T] (expr: T): T = macro Macro.assertNonThrow[E, T]
}