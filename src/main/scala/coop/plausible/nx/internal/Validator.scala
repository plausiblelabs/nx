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

package coop.plausible.nx.internal

import scala.annotation.tailrec
import coop.plausible.nx.{UncheckedExceptions, internal}

/**
 * No Exceptions Implementation.
 *
 * This trait may be mixed in with any valid reflection global, including:
 * - As a compiler plugin (see [[internal.CompilerPlugin]])
 * - As a compile-time macro (see [[internal.Macro]])
 */
private trait Validator extends Core with Errors with CheckedExceptionStrategies {
  import universe._

  /**
   * Finds all unhandled throwables at a given tree node.
   *
   * @param checkedStrategy The checked exception strategy to be used when validating exceptions.
   */
  class ThrowableValidator (checkedStrategy: CheckedExceptionStrategy) {
    /**
     * Traverse `tree` and return all validation errors. Note that this will contain '''all''' unhandled `Throwable`
     * types, not just subclasses of `Exception`.
     *
     * The top-level node is treated as an exception propagation point; any exceptions that could be thrown
     * at the top of the tree are treated as unhandled exceptions.
     *
     * For example, given the following input, a value of Seq[UnhandledThrowable(... IOException)] will be provided.
     *
     * {{{
     *   @throws[IOException]("Bad data triggers failure") read () = ???
     *   read()
     * }}}
     *
     * @param tree The top-level node to be traversed.
     * @return An ordered sequence of validation errors.
     */
    def check (tree: Tree): Seq[ValidationError] = {
      /* Instantiate our traverse handler */
      val traverse = new ThrowableTraversal(checkedStrategy)

      /* Perform the traversal */
      traverse.validationErrors(tree)
    }
  }

  /**
   * Handles the actual (mutable) traversal of the tree.
   *
   * @param checkedStrategy The checked exception strategy to be used when validating exceptions.
   */
  private class ThrowableTraversal (checkedStrategy: CheckedExceptionStrategy) extends Traverser {
    import scala.collection.mutable

    /**
     * Traverse `tree` and return all validation errors. The top-level node is treated
     * as an exception propagation point; any exceptions that could be thrown at the top of the tree
     * are treated as unhandled exceptions.

     * @param tree The top-level node to be traversed.
     * @return An ordered sequence of validation errors.
     */
    def validationErrors (tree: Tree): Seq[ValidationError] = {
      /* The top of the tree is considered a propagation point */
      mutableState.propagationPoint(tree) {
        /* Perform traversal */
        traverse(tree)

        /* No exceptions are handled at the top of the tree */
        Set()
      }

      /* Provide the result */
      mutableState.validationErrors
    }

    /**
     * Represents a candidate throwing entity that may be caught or declared.
     *
     * @param pos The position at which the throwable may be raised.
     * @param tpe The throwable's type.
     */
    private case class Throwie (pos: Position, tpe: Type)

    /**
     * Mutable state required by the Traverser API. We vend a set of high-level APIs for operating on this state,
     * as to minimize the mutability headache.
     */
    private object mutableState {
      /**
       * The set of throwables types that are currently known to be throwable from the current position in the tree,
       * but may still be caught or declared.
       *
       * A new candidate list is pushed onto the stack upon entry into a propagation point, and this list is
       * popped at exit. An initial list is created for the root tree node.
       */
      private val candidateThrowieStack = mutable.Stack[mutable.MutableList[Throwie]]()

      /**
       * The set of validation errors that are found across the entire tree. This will be populated explicitly
       * during walking, as well as from the `candidateThrowies` at throwable propagation points.
       */
      private val validationErrorList = mutable.MutableList[ValidationError]()

      /**
       * Discard all candidate throwables that are a valid (sub)type of one of `throwTypes`
       *
       * @param throwTypes Types (and transitively, subtypes) to be removed from the set of candidate throwables.
       */
      private def filterCandidateThrowies (throwTypes: Set[Type]): Unit = throwTypes.foreach { throwType =>
        val filtered = candidateThrowieStack.pop.filterNot(_.tpe <:< throwType)
        candidateThrowieStack.push(filtered)
      }

      /**
       * Declare an explicit validation error. This will be added to the set of validation errors
       * available upon completion of a traversal.
       */
      def declareValidationError (error: ValidationError): Unit = {
        validationErrorList += error
      }

      /**
       * Declare one or more candidate throwables at the given point in the tree. The provided throwies will
       * be validated against the active checked exception strategy; any non-checked throwable types will be
       * discarded.
       *
       * @param throwies The throwies that may be thrown at this point.
       */
      def declareCandidateThrowies (throwies: Seq[Throwie]): Unit = {
        candidateThrowieStack.head ++= throwies.filterNot(t => checkedStrategy.isUnchecked(t.tpe))
      }

      /**
       * This methodSymbol should be called at catch pints to declare any caught throwable types (eg, from try-catch blocks).
       *
       * Any throwable types declared in `throwTypes` will be removed from the set of ''candidate'' uncaught throwables.
       *
       * @param throwTypes The throwable types declared as caught at this point.
       */
      def declareCatchPoint (throwTypes: Set[Type]): Unit = filterCandidateThrowies(throwTypes)

      /**
       * This should be called to define a propigation point; a new entry in the candidate throwies
       * stack will be created, and the provided function executed.
       *
       * Any throwable types returned by the expression will be removed from the set of ''candidate'' uncaught
       * throwables, and any undeclared types will be moved to the list of ''known'' unhandled throwables.
       *
       * @param tree The propigation point's node. If marked with an @unchecked annotation, `expr` will not be
       *             executed.
       * @param expr Expression to execute to generate the set of handled/declared throwable types.
       */
      def propagationPoint (tree: Tree) (expr: => Set[Type]): Unit = {
        /* We skip this entire propagation point if it's marked @unchecked */
        if (tree.symbol == null || !uncheckedAnnotated(tree.symbol.annotations)) {
          /* Create a new entry in the candidate stack. */
          candidateThrowieStack.push(mutable.MutableList())

          /* Gather declared throwables. */
          val declared = expr

          /* Filter declared types from the candidates */
          filterCandidateThrowies(declared)

          /* Move all remaining candidates to the list of unhandled throwables */
          validationErrorList ++= candidateThrowieStack.head.map { t => UnhandledThrowable(t.pos, t.tpe) }
          candidateThrowieStack.pop()
        }
      }

      /**
       * Return a snapshot of the currently collected set of validation errors. The errors will be ordered
       * according to the original point in the tree in which they occurred.
       */
      def validationErrors: Seq[ValidationError] = {
        validationErrorList.toSeq
      }
    }

    /** @inheritdoc */
    override def traverse (tree: Tree): Unit = {
      /* Traverse children; we work from the bottom up. */
      def defaultTraverse (): Unit = {
        super.traverseTrees(tree.children)
      }

      /* Look for exception-related constructs */
      tree match {
        /* Class body and constructors. This is a propagation point. */
        case cls:ClassDef => mutableState.propagationPoint(tree) {
          /*
           * Find the primary constructor declaration.
           *
           * Primary constructor annotations are attached to the constructor methodSymbol, but the constructor's code is
           * actually found in the class' body. We have to collect exception types from this constructor, and *then* descend
           * into the class body.
           */
          val primaryAnnotations = cls.impl.tpe.declarations.collectFirst {
            case m: MethodSymbol if m.isPrimaryConstructor => m.annotations
          }.getOrElse(Seq())



          /* Find @throws annotations; we can't yet declare the propagation point, as the primary constructor's body is not
           * actually within the methodSymbol; we have to traverse into the class first. */
          val throws: Seq[Type] = extractAnnotatedThrows(cls, primaryAnnotations) match {
            case Right(exceptions) => exceptions
            case Left(err) =>
              /* Report the error, return an empty set */
              mutableState.declareValidationError(err)
              Seq()
          }

          /* Traverse into the class to populate the set of candidate throwables -- unless the primary constructor is
           * marked as unchecked. */
          if (!uncheckedAnnotated(primaryAnnotations)) {
            defaultTraverse()
          }

          /* Return the set of handled throwable types; this uses the annotations found on the primary constructor, and the throwables
           * found within the class body itself. */
          throws.toSet
        }


        /* try statement. This is a catch point. */
        case Try(block, catches, finalizer) =>
          /* Traverse into the try body to find all throwables */
          traverse(block)

          /*
           * Extract the exception types of all viable catches. We must exclude the following (usually valid) matches
           * that rely on runtime pattern matching:
           *
           * - Case statements that define a guard.
           * - Unapply-based statements (eg, Fatal(e))
           *
           * Since the matching is dynamic in those cases, we have no way to assert that the guard will match all
           * possible values at runtime.
           *
           * Medieval, isn't it? This perfectly illustrated how unchecked exceptions destroy type safety.
           */
          val caught = catches.filter(_.guard.isEmpty).map(_.pat).filter { pattern =>
            /* Recursively search for any function calls within the catch pattern. */
            val fcalls = for (fcall @ Apply(_, _) <- pattern) yield fcall

            /* Disallow patterns that contain unapply calls. */
            if (fcalls.size > 0) {
              false
            } else {
              true
            }
          }.map(_.tpe)

          /* Declare the catch point */
          mutableState.declareCatchPoint(caught.toSet)

          /*
           * Now extract any throwables from subtrees that should *not* be covered by the try's catch() block. This
           * must be done in the same order as they're declared in code so that we report issues in the correct
           * order:
           *
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

        /* Method or constructor definition. This is a propagation point. */
        case defdef:DefDef => mutableState.propagationPoint(tree) {
          /* Traverse all children */
          defaultTraverse()

          /*
           * Find exception types declared to be thrown by either the definition, or any of the symbols it overrides,
           * and verify that the subtype does not widen the declared superclass @throws.
           */
          val throws = for (
          /* Find directly attached @throws-annotated throwable types. */
            childThrows <- extractAnnotatedThrows(defdef, defdef.symbol.annotations).left.map(Seq(_)).right;
            /* Fetch the list of overridden symbols (may be none). */
            overriddenSymbols <- Right(defdef.symbol.allOverriddenSymbols).right;

            /* Fetch all @throws-annotated throwable types from overridden symbols. */
            parentThrows <- extractAnnotatedThrows(defdef, overriddenSymbols.map(_.annotations).flatten).left.map(Seq(_)).right;

            /* Unify the parent and child throw sets, after first validating that the overriding method does not widen the
             * types declared by the parent(s). */
            result <- {
              /* Generate errors for any incorrect @throws declarations; if the parent-declared @throws don't specify a
               * superclass, the child is widening the throw set. */
              val invalidThrows = if (overriddenSymbols.size > 0) {
                for (throws <- childThrows if !parentThrows.exists(throws <:< _)) yield throws
              } else {
                /* If there are no parents, there can be no errors */
                Set()
              }

              /* Declare errors for incorrect @throw declarations */
              invalidThrows.foreach { throws =>
                mutableState.declareValidationError(CannotOverride(defdef.pos, throws, defdef.symbol))
              }

              /* We intentionally provide the unmodified set of declared exceptions; this avoids triggering spurious
               * UnhandledThrowable errors caused by a CannotOverride error stripping the @throws declaration. */
              Right(childThrows ++ parentThrows)
            }.right
          ) yield result

          /* Return the handled exceptions, or any errors. */
          throws match {
            case Right(types) =>
              types.toSet
            case Left(errs) =>
              for (err <- errs) mutableState.declareValidationError(err)
              Set()
          }
        }

        /* Explicit throw */
        case thr:Throw =>
          /* Traverse all children */
          defaultTraverse()

          /* Add the type to the set of candidate throwables. */
          mutableState.declareCandidateThrowies(Seq(Throwie(thr.pos, thr.expr.tpe)))

        /*
         * Method/function call
         */
        case apply:Apply =>
          /* Traverse all children */
          defaultTraverse()

          /* Extract both the target's @throws, as well as any symbols overridden by target. */
          val throws = for (
            applyThrows <- extractAnnotatedThrows(apply, apply.symbol.annotations).right;
            parentThrows <- extractAnnotatedThrows(apply, apply.symbol.allOverriddenSymbols.map(_.annotations).flatten).right
          ) yield applyThrows ++ parentThrows

          /* Find exception types declared to be thrown by the target; declare them as candidate throwables */
          throws match {
            case Right(exceptions) =>
              mutableState.declareCandidateThrowies(exceptions.map(tpe => Throwie(apply.fun.pos, tpe)))
            case Left(err) =>
              mutableState.declareValidationError(err)
          }

        case _ =>
          /* Hand off to default traversal methodSymbol */
          super.traverse(tree)
      }
    }

    /** Class symbol for our `unchecked` annotation, or None if the runtime library is not available */
    private lazy val UncheckedClass: Option[Symbol] = try {
      Some(rootMirror.staticClass(classOf[UncheckedExceptions].getCanonicalName))
    } catch {
      case mre:Exception => None
    }

    /**
     * Return true if `annotations` contains an @unchecked annotation, false otherwise.
     *
     * @param annotations The annotations to be checked.
     * @return true if the @unchecked is included in the annotations, false otherwise.
     */
    private def uncheckedAnnotated (annotations: Seq[Annotation]): Boolean = {
      UncheckedClass.exists(uncheckedClass => annotations.exists(_.tpe.typeSymbol == uncheckedClass))
    }

    /** The class symbol for the 'scala.throws' annotation */
    private lazy val ThrowsClass = rootMirror.staticClass("scala.throws")

    /**
     * Given a sequence of annotations, extract the exception type from any @throws annotations.
     *
     * @param owner The tree element to which the annotations are attached.
     * @param annotations The annotations from which to fetch @throws exception declarations.
     * @return The sequence of throwable types declared using @throws annotations.
     */
    private def extractAnnotatedThrows (owner: Tree, annotations: Seq[Annotation]): Either[ValidationError, Seq[Type]] = {
      /* Filter non-@throws annotations */
      val throwsAnnotations = annotations.filter(_.tpe.typeSymbol == ThrowsClass)

      /* Perform the actual extraction (recursively) */
      @tailrec def extractor (head: Annotation, tail: Seq[Annotation], accum: Seq[Type]): Either[ValidationError, Seq[Type]] = {
        /* Parse this annotation's argument. */
        val parsed = head match {
          /* Scala 2.9 API: @throws(classOf[Exception]) (which is throws[T](classOf[Exception])) */
          case Annotation(_, List(Literal(Constant(tpe: Type))), _) => Right(tpe)
            
          /* Scala 2.10 API: @throws[Exception], @throws[Exception]("cause") */
          case Annotation(TypeRef(_, _, args), _, _) if args.size > 0 => Right(args.head)

          /* Unsupported annotation arguments. */
          case _ => Left(InvalidThrowsAnnotation(owner.pos, s"Unsupported @throws annotation '$head' on `$owner`"))
        }
        
        /* On success, recursively parse the next annotation. On failure, return immediately */
        parsed match {
          /* Extraction succeeded */
          case Right(tpe) =>
            if (tail.size == 0) {
              Right(accum :+ tpe)
            } else {
              extractor(tail.head, tail.drop(1), accum :+ tpe)
            }
          case Left(error) => Left(error)
        }
      }

      /* If there are any annotations, extract them */
      if (throwsAnnotations.size > 0)
        extractor(throwsAnnotations.head, throwsAnnotations.drop(1), Vector())
      else
        Right(Vector())
    }
  }
}
