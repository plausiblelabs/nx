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

  /**
   * Scan `expr` for unhandled exceptions and return the results; rather than triggering compilation errors,
   * this simply returns the result of the validation.
   *
   * Example usage:
   * {{{
   *   val unhandledExceptions: Set[Class[_ <: Throwable]] = NX.check {
   *      java.inet.InetAddress.getByName("some host")
   *   }
   * }}}
   *
   * Since java.inet.InetAddress.getByName() declares that it throws an UnknownHostException, the result
   * of NX.check will be a Set(classOf[UnknownHostException]).
   *
   * @param expr The expression to be scanned.
   * @tparam T The expression type.
   * @return All uncaught exception classes.
   */
  private[nx] def check[T] (expr: T): Set[Class[_ <: Throwable]] = macro NXMacro.nx_macro_check[T]
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

    /**
     * The set of throwables types propagated from the current position in the tree.
     *
     * This is updated as the tree is walked; an unfortunate bit of mutability that we
     * can't escape due to the constraints of the tree API.
     */
    private val throwies = mutable.HashSet[Type]()

    /**
     * Fetch the set of unhandled exceptions.
     *
     * @return All unhandled exceptions in the given tree.
     */
    def unhandledExceptions: Set[Type] = throwies.toSet

    /**
     * Remove all throwables from the throwables set that are equal to or a subtype
     * of the given exception type.
     *
     * @param throwType The exception supertype for which all matching throwable
     *                  types will be discarded.
     */
    private def filterMatchingThrowies (throwType:Type): Unit = {
      throwies --= throwies.filter(_ <:< throwType)
    }

    /** @inheritdoc */
    override def traverse (tree: Tree): Unit = {
      /* Traverse children; we work from the bottom up. */
      def defaultTraverse (): Unit = {
        super.traverseTrees(tree.children)
      }

      /* Look for exception-related constructs */
      tree match {
        /*
         * try statement
         */
        case Try(block, catches, finalizer) =>
          /* Traverse into the try body to find all throwables */
          traverse(block)

          /* Extract the exception types of all viable catches. We must filter any that define a guard; there's no way
           * for us to known whether the guard will match all possible values at runtime. */
          val thrown = catches.filter(_.guard.isEmpty).map(_.pat.tpe)

          /* Extract the actual exception types and remove them from the propagation set. */
          thrown.foreach(filterMatchingThrowies)

          /* Now extract any throwables from subtrees that are *not* caught by the try's catch() block. This must be done
           * in the same order as they're declared in code so that we report issues in
           * the correct order:
           * - Guard blocks.
           * - Case statement bodies.
           * - Finalizer block
           */
          catches.foreach { c =>
            traverse(c.pat)
            traverse(c.guard)
            traverse(c.body)
          }
          traverse(finalizer)

        /*
         * Method, function, or constructor definition
         */
        case defdef:DefDef =>
          /* Traverse all children */
          defaultTraverse()

          /* Find @throws annotations */
          val throws = defdef.symbol.annotations.filterNot(_.tpe =:= typeOf[throws[_]])

          /* Extract the actual exception types and remove them from the propagation set. */
          throws.foreach { (annotation:Annotation) =>
            extractThrowsAnnotation(annotation) match {
              case Some(tpe) => filterMatchingThrowies(tpe)
              case None => error(defdef.pos, s"Unsupported @throws annotation parameters '$annotation' on called method")
            }
          }

        /*
         * Explicit throw
         */
        case thr:Throw =>
          /* Traverse all children */
          defaultTraverse()

          /* Add the type to the propagated throwable set. */
          throwies.add(thr.expr.tpe)

        /*
         * Method/function call
         */
        case apply:Apply =>
          /* Traverse all children */
          defaultTraverse()

          /* Filter non-@throws annotations */
          val throws = apply.symbol.annotations.filterNot(_.tpe =:= typeOf[throws[_]])

          /*
           * Extract the actual exception types.
           */
          throws.foreach { (annotation:Annotation) =>
            extractThrowsAnnotation(annotation) match {
              case Some(tpe) =>
                throwies += tpe
              case None =>
                error(apply.pos, s"Unsupported @throws annotation parameters '$annotation' on called method")
            }
          }

        case _ =>
          /* Hand off to default traversal method */
          super.traverse(tree)
      }
    }

    /**
     * Extract the exception type from a @throws annotation.
     *
     * We support both 'new-style' and 'old-style' @throws constructors:
     *
     * - @throws(clazz: Class[T]) (old style)
     * - @throws[T](cause: String) (new style)
     *
     * @return Returns Some(Class[T]) on success, or None if the Annotation's arguments were in an unknown format.
     */
    private def extractThrowsAnnotation (annotation: Annotation): Option[Type] = annotation match {
      // old-style: @throws(classOf[Exception]) (which is throws[T](classOf[Exception]))
      case Annotation(_, List(Literal(Constant(tpe: Type))), _) => Some(tpe)

      // new-style: @throws[Exception], @throws[Exception]("cause")
      case Annotation(TypeRef(_, _, args), _, _) => Some(args.head)

      // Unknown
      case other => None
    }
  }
}
