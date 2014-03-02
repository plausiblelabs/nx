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

import scala.reflect.api.Universe

/**
 * No Exceptions
 */
object NX {
  import scala.language.experimental.macros

  /**
   * Scan `expr` for unhandled exceptions.
   *
   * @param expr The expression to be scanned.
   * @tparam T The expression type.
   * @return The expression result, or a compiler error if the expression contained unchecked exceptions.
   */
  def nx[T] (expr: T): T = macro NXMacro.nx_macro[T]
}

/**
 * No Exceptions Implementation.
 *
 * This trait may be mixed in with any valid reflection universe, including:
 * - As a compiler plugin (see [[NXPlugin]], and
 * - As a compile-time macro (see [[NXMacro]]
 */
trait NX {
  /** Reflection universe. */
  val global: Universe
  import global._

  /**
   * Handles traversal of the tree.
   */
  class ExceptionTraversal extends Traverser {


    override def traverse (tree: Tree): Unit = {

      /* Traverse children; we work from the bottom up. */
      super.traverse(tree)

      /* Look for exception-related constructs */
      tree match {
        /* try statement */
        case Try(_, catches, _) =>
          println(s"TRY: $tree")

        /* Method, function, or constructor. */
        case defdef:DefDef =>
          println(s"DEF: $defdef")

        /* Explicit throw */
        case thr:Throw =>
          val excType = thr.expr.tpe

          println(s"THROW: $tree")
          println(s"Throws: excType")

        /* Method/function call */
        case apply:Apply =>
          println(s"APPLIED: $tree")
          if (apply.symbol.annotations.hasDefiniteSize && apply.symbol.annotations.size > 0) {
            println(s"$tree annotations: ${apply.symbol.annotations}")
          }

          //println(s"Thrown: ${apply.symbol.throwsAnnotations()}")

        case _ =>
          //println(s"u: ${tree.getClass.getSimpleName} - $tree")
      }
    }
  }
}
