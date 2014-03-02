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
 * This trait may be mixed in with any valid reflection global, including:
 * - As a compiler plugin (see [[NXPlugin]], and
 * - As a compile-time macro (see [[NXMacro]]
 */
trait NX {
  /** Reflection universe. */
  val universe: Universe
  import universe._

  /**
   * This trait may be mixed in to support compiler (or macro, or reflection) error reporting.
   */
  trait ErrorReporting {
    /**
     * Report an error at `pos` with the given `message`.
     *
     * @param pos The position of the error.
     * @param message The error message.
     */
    def error (pos: Position, message: String)
  }

  /**
   * Handles traversal of the tree.
   */
  abstract class ExceptionTraversal extends Traverser with ErrorReporting {
    import scala.collection.mutable

    /* An unfortunate bit of mutability that we can't escape */
    private val throwies = mutable.HashSet[Type]()

    /** @inheritdoc */
    override def traverse (tree: Tree): Unit = {
      /* Traverse children; we work from the bottom up. */
      val childTraverser = new ExceptionTraversal() {
        /* Hand any errors off to our parent */
        override def error (pos: Position, message: String): Unit = ExceptionTraversal.this.error(pos, message)
      }

      childTraverser.traverseTrees(tree.children)
      throwies ++ childTraverser.throwies

      /* Look for exception-related constructs */
      tree match {
        /* try statement */
        case Try(_, catches, _) =>
          // TODO - Clear caught throwies
          println(s"TRY: $tree")

        /* Method, function, or constructor. */
        case defdef:DefDef =>
          // TODO - Report undeclared throwies
          println(s"DEF: $defdef")

        /* Explicit throw */
        case thr:Throw =>
          /* Add the type to the list of throwies. */
          throwies.add(thr.expr.tpe)

        /* Method/function call */
        case apply:Apply =>
          // TODO - Gather throws annotations
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
