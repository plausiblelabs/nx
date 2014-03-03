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

import scala.reflect.api.{Annotations, Universe}
import scala.annotation.tailrec

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
   * Represents an NX-defined compiler error.
   *
   * @param pos Source code position
   * @param message Error message.
   */
  case class NXCompilerError (pos: Position, message: String)

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
    private val activeThrowies = mutable.HashSet[Type]()

    /**
     * The set of throwables that are known errors. This will be populated at throwable propagation points,
     * such as method definitions.
     */
    private val unhandledThrowies = mutable.HashSet[Type]()

    /**
     * Fetch the set of unhandled exceptions.
     *
     * @return All unhandled exceptions in the given tree.
     *
     * TODO: We need to break this down into active throwables (eg, throwables that are either declared or unhandled)
     * versus *unhandled* throwables (eg, throwables that are not declared, but could be fired). This will
     * require defining a new 'propagation point' that occurs not just at ClassDef and DefDef points, but also
     * at the top-level of the traversal.
     */
    def unhandledExceptions: Set[Type] = unhandledThrowies.toSet ++ activeThrowies

    /**
     * Remove all throwables from the throwables set that are equal to or a subtype
     * of the given exception type.
     *
     * @param throwType The exception supertype for which all matching throwable
     *                  types will be discarded.
     */
    private def filterActiveThrowies (throwType: Type): Unit = {
      activeThrowies --= activeThrowies.filter(_ <:< throwType)
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
         * Class definition
         */
        case cls:ClassDef =>
          /*
           * Find the primary constructor declaration and extract any @throws annotations.
           *
           * Primary constructor annotations are attached to the constructor method, but the constructor's code is
           * actually found in the class' body. We have to collect exception types here, and *then* descend
           * into the class body.
           */
          val exceptionTypes = cls.impl.tpe.declarations.collectFirst {
            case m: MethodSymbol if m.isPrimaryConstructor =>
              annotatedThrows(cls, m.annotations) match {
                case Right(exceptions) =>
                  exceptions
                case Left(err) =>
                  /* Report the error, return an empty set */
                  error(err.pos, err.message)
                  Set()
              }
          }.getOrElse(Set())

          /* Traverse into the class to populate the set of active throwables. */
          defaultTraverse()

          /* Filter declared throwables from the propagation set; these are correctly handled. */
          exceptionTypes.foreach(filterActiveThrowies)

          /* Any undeclared throwables at this point are unhandled errors. */
          unhandledThrowies ++= activeThrowies

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
          thrown.foreach(filterActiveThrowies)

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

          /* Find @throws declared exception types */
          annotatedThrows(defdef, defdef.symbol.annotations) match {
            case Right(types) =>
              /* Filter declared throwables from the propagation set; these are correctly handled. */
              types.foreach(filterActiveThrowies)

              /* Any undeclared throwables at this point are unhandled errors. */
              unhandledThrowies ++= activeThrowies
            case Left(err) => error(err.pos, err.message)
          }

        /*
         * Explicit throw
         */
        case thr:Throw =>
          /* Traverse all children */
          defaultTraverse()

          /* Add the type to the propagated throwable set. */
          activeThrowies.add(thr.expr.tpe)

        /*
         * Method/function call
         */
        case apply:Apply =>
          /* Traverse all children */
          defaultTraverse()

          /* Find exception types declared to be thrown by the target; filter them from the propagation set. */
          annotatedThrows(apply, apply.symbol.annotations) match {
            case Right(exceptions) => exceptions.foreach(activeThrowies += _)
            case Left(err) => error(err.pos, err.message)
          }

        case _ =>
          /* Hand off to default traversal method */
          super.traverse(tree)
      }
    }


    /**
     * Given a sequence of annotations, extract the exception type from any @throws annotations.
     *
     * @param owner The tree element to which the annotations are attached.
     * @param annotations The annotations from which to fetch @throws exception declarations.
     * @return The set of throwable types declared using @throws annotations.
     */
    private def annotatedThrows (owner: Tree, annotations: Seq[Annotation]): Either[NXCompilerError, Set[Type]] = {
      /* Filter non-@throws annotations */
      val throwsAnnotations = annotations.filterNot(_.tpe =:= typeOf[throws[_]])

      /* Perform the actual extraction (recursively) */
      @tailrec def extractor (head: Annotation, tail: Seq[Annotation], accum: Set[Type]): Either[NXCompilerError, Set[Type]] = {
        /* Parse this annotation's argument. */
        val parsed = head match {
          /* Scala 2.9 API: @throws(classOf[Exception]) (which is throws[T](classOf[Exception])) */
          case Annotation(_, List(Literal(Constant(tpe: Type))), _) => Right(tpe)
            
          /* Scala 2.10 API: @throws[Exception], @throws[Exception]("cause") */
          case Annotation(TypeRef(_, _, args), _, _) => Right(args.head)
            
          /* Unsupported annotation arguments. */
          case _ => Left(NXCompilerError(owner.pos, s"Unsupported @throws annotation parameters on annotation `$head`"))
        }
        
        /* On success, recursively parse the next annotation. On failure, return immediately */
        parsed match {
          /* Extraction succeeded */
          case Right(tpe) =>
            if (tail.size == 0) {
              Right(accum + tpe)
            } else {
              extractor(tail.head, tail.drop(1), accum + tpe)
            }
          case Left(error) => Left(error)
        }
      }

      /* If there are any annotations, extract them */
      if (throwsAnnotations.size > 0)
        extractor(throwsAnnotations.head, throwsAnnotations.drop(1), Set())
      else
        Right(Set())
    }
  }
}
