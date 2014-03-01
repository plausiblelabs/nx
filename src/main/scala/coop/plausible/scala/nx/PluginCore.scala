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

import scala.tools.nsc.Global

/**
 * No Exceptions plugin implementation.
 *
 * The plugin classes are implemented as a trait to allow use outside
 * of the NXPlugin concrete implementation.
 */
trait PluginCore {
  /** Compiler global state. */
  val global: Global

  import global._

  /**
   * Handles traversal of the tree.
   */
  class ExceptionTraversal extends Traverser {
    override def traverse (tree: Tree): Unit = {

      /* Let the superclass handle traversal */
      def traverseDefault () = {
        super.traverse(tree)
      }

      /* Look for exception-related constructs */
      tree match {
        /* try statement */
        case Try(_, catches, _) =>
          println(s"TRY: $tree")
          traverseDefault()

        /* Explicit throw */
        case thr:Throw =>
          println(s"THROW: $tree")
          println(s"Throws: ${thr.symbol}")
          traverseDefault()

        /* Method/function call */
        case apply:Apply =>
          println(s"APPLIED: $tree")
          println(s"Thrown: ${apply.symbol.throwsAnnotations()}")
          traverseDefault()

        case apply:TypeApply =>
          println(s"TAPPLY: $tree")
          traverseDefault()

        case _ =>
          println(s"u: ${tree.getClass.getSimpleName} - $tree")
          traverseDefault()
      }
    }
  }
}
