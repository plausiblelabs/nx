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

package coop.plausible.scala.nx

import scala.reflect.macros.Context

/**
 * No Exceptions macro implementation.
 */
object NXMacro {
  import scala.language.experimental.macros

  /**
   * Implementation of the nx macro.
   *
   * @param c Compiler context.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return The original expression, or a compiler error.
   */
  def nx_macro[T] (c: Context)(expr: c.Expr[T]): c.Expr[T] = {
    import c.universe._

    /* Instantiate an instance of the plugin core */
    val core = new NXCore {
      override val global: c.universe.type = c.universe
    }

    /* Kick off our traversal */
    val traverse = new core.ExceptionTraversal
    traverse.traverse(expr.tree)

    /* Return the original, unmodified expression */
    expr
  }

  /**
   * Scan `expr` for unhandled exceptions.
   *
   * @param expr The expression to be scanned.
   * @tparam T The expression type.
   * @return The expression result, or a compiler error if the expression contained unchecked exceptions.
   */
  def nx[T] (expr: T): T = macro nx_macro[T]
}
