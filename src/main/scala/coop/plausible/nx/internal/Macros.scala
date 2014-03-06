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

import ValidationResult.{ValidationError, UnhandledThrowable, InvalidThrowsAnnotation, CannotOverride}
import coop.plausible.nx._
import ValidationResult.InvalidThrowsAnnotation
import ValidationResult.UnhandledThrowable
import ValidationResult.CannotOverride

/**
 * No Exceptions macro implementation.
 */
object Macros extends MacroTypes {
  /**
   * Private implementation of the nx.verify macro; rather than triggering compilation errors,
   * it simply returns the result of the validation.
   *
   * @param c Compiler context.
   * @param checked Checked exception configuration, or null to use the standard config.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return An expression that will vend the validation results.
   */
  def nx_verify_config[T] (c: Context)(checked:c.Expr[CheckedExceptionConfig])(expr: c.Expr[T]): c.Expr[ValidationResult] = {
    /* <= 2.10 compatibility shims */
    val compat = new MacroCompat with MacroTypes { override val context: c.type = c }
    import compat.{TypeName, TermName}
    import c.universe.{TypeName => _, TermName => _, _}

    /* Instantiate a macro global-based instance of the plugin core */
    val nx = new Validator {
      override val universe: c.universe.type = c.universe
    }

    /* Perform the validation */
    val validator = new nx.ThrowableValidator(determineCheckStrategy(c, nx)(checked))
    val errors = validator.check(expr.tree)

    /* Extract the set of unhandled throwables */
    val types = extractUnhandledExceptionTypes(nx)(errors)

    /* Convert the set of unhandled throwables to an AST representing a classOf[Throwable] argument list. */
    val unhandledArgs = types.map(tpe => Literal(Constant(tpe))).toList

    /* Map our validation results into runtime-instantiated ValidationError values. */
    val errorArgs = {
      /* Generics generic calls to calls to <Class>.apply(constantArgs) */
      def ValidationErrorCreate (tpe:Type, args:Any*) = {
        val Case_apply = Select(Ident(tpe.typeSymbol.companionSymbol), TermName("apply"))
        Apply(Case_apply, args.map(arg => Literal(Constant(arg))).toList)
      }

      /* Perform the error mapping */
      errors.map {
        case e:nx.CannotOverride => ValidationErrorCreate(typeOf[CannotOverride[_]], e.methodSymbol.name.toString, e.throwableType)
        case e:nx.InvalidThrowsAnnotation => ValidationErrorCreate(typeOf[InvalidThrowsAnnotation], e.message)
        case e:nx.UnhandledThrowable => ValidationErrorCreate(typeOf[UnhandledThrowable[_]], e.throwableType)
      }.toList
    }

    /* Select the NX.ValidationResult class */
    val ValidationResult_apply = Select(Ident(typeOf[ValidationResult].typeSymbol.companionSymbol), TermName("apply"))

    /* Define our Class[_ <: Throwable] type */
    val existentialClassType = ExistentialTypeTree(
      /* Define a new applied Class[T] type of _$1 */
      AppliedTypeTree(Ident(definitions.ClassClass), List(Ident(TypeName("_$1")))),

      /* Define type _$1 = Class[_ <: Throwable] */
      List(TypeDef(Modifiers(Flag.DEFERRED /* | SYNTHETIC */), TypeName("_$1"), List(), TypeBoundsTree(Ident(definitions.NothingClass), Ident(typeOf[Throwable].typeSymbol))))
    )

    /* Compose the Seq[_ <: Throwable](unhandled:_*) return value */
    val Set_apply = Select(Ident(weakTypeOf[Set[_]].typeSymbol.companionSymbol), TermName("apply"))
    val AllErrors = Apply(TypeApply(Ident(definitions.List_apply), List(Ident(typeOf[ValidationError].typeSymbol))), errorArgs)
    val UnhandledSet = Apply(TypeApply(Set_apply, List(existentialClassType)), unhandledArgs)
    c.Expr(Apply(ValidationResult_apply, List(AllErrors, UnhandledSet)))
  }

  /**
   * Private implementation of the nx.verify macro; rather than triggering compilation errors,
   * it simply returns the result of the validation.
   *
   * @param c Compiler context.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return An expression that will vend the validation results.
   */
  def nx_verify[T] (c: Context)(expr: c.Expr[T]): c.Expr[ValidationResult] = {
    import c.universe._
    /* Generate call to CheckedExceptionConfig.Default.apply() */
    val resultExpr = nx_verify_config(c)(null)(expr).in(rootMirror)
    c.Expr(resultExpr.tree)
  }

  /**
   * Internal implementation of the nx.exceptionChecked macro. Refer to `nx.exceptionChecked` for the public API
   *
   * @param c Compiler context.
   * @param checked Checked exception configuration, or null to use the standard config.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return The original expression, or a compiler error.
   */
  def excpetion_checked_config[T] (c: Context)(checked:c.Expr[CheckedExceptionConfig])(expr: c.Expr[T]): c.Expr[T] = {
    /* Instantiate a macro global-based instance of the plugin core */
    val nx = new Validator {
      override val universe: c.universe.type = c.universe
    }

    /* Perform the validation */
    val validator = new nx.ThrowableValidator(determineCheckStrategy(c, nx)(checked))
    validator.check(expr.tree).foreach { err =>
      c.error(err.pos, err.message)
    }

    /* Return the original, unmodified expression */
    expr
  }

  /**
   * Internal implementation of the nx.exceptionChecked macro. Refer to `nx.exceptionChecked` for the public API
   *
   * @param c Compiler context.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return The original expression, or a compiler error.
   */
  def exception_checked[T] (c: Context)(expr: c.Expr[T]): c.Expr[T] = {
    import c.universe._
    val resultExpr = excpetion_checked_config(c)(null)(expr).in(rootMirror)
    c.Expr(resultExpr.tree)
  }

  /**
   * Internal implementation of the 'assertNoThrows' macro. Refer to `nx.assertNonThrow` for the
   * public API.
   *
   * @param c Compiler context.
   * @param expr Expression to be evaluated.
   * @tparam E Asserted exception type.
   * @tparam T Expression type.
   * @return The expression, wrapped in an appropriate try-assert block for `E`.
   */
  def assertNonThrow[E <: Throwable : c.WeakTypeTag, T] (c: Context) (expr: c.Expr[T]): c.Expr[T] = {
    /* <= 2.10 compatibility shims */
    val compat = new MacroCompat with MacroTypes { override val context: c.type = c }
    import compat.{TypeName, TermName}
    import c.universe.{TypeName => _, TermName => _, _}

    /* Asserted Throwable's type */
    val eType = implicitly[c.WeakTypeTag[E]].tpe

    /* AssertionError class */
    val AssertionErrorClass = c.mirror.staticClass("java.lang.AssertionError")

    /* Generate try ( expr ) { catch e:E => throw new AssertionError() } */
    c.Expr[T](
      Try(
        expr.tree,
        List (
          /* case e:E => throw new AssertionError() */
          CaseDef(
            Bind(TermName("e"), Typed(Ident(nme.WILDCARD), Ident(eType.typeSymbol))),
            EmptyTree,
            Throw(
              Apply(
                Select(
                  New(Ident(AssertionErrorClass)),
                  nme.CONSTRUCTOR
                ),
                List(
                  Literal(Constant("Exception asserted as unthrowable was thrown")),
                  Ident(TermName("e"))
                )
              )
            )
          )
        ),
        EmptyTree
      )
    )
  }


  /**
   * Extract the unhandled exception types from `errors`.
   *
   * @param nx Our NX context.
   * @param errors A set of NX validation errors.
   * @return The unhandled exception types in `errors`, if any.
   */
  private[internal] def extractUnhandledExceptionTypes (nx: Validator) (errors: Seq[nx.ValidationError]): Set[nx.universe.Type] = {
    errors.view.filter {
      case nx.UnhandledThrowable(_, tpe) => true
      case _:nx.CannotOverride => false
      case _:nx.InvalidThrowsAnnotation => false
    }.map {
      case nx.UnhandledThrowable(_, tpe) => tpe
    }.toSet[nx.universe.Type]
  }

  /**
   * Determine the appropriate CheckedExceptionStrategy value from the given expression. If the expression does not
   * contain a literal constant, a context error will be reported and StandardCheckedExceptionStrategy will be returned.
   *
   * @param c The compiler context.
   * @param expr The expression returning a `CheckedExceptionConfig` type, or null to use the standard config.
   * @return True or false.
   */
  private def determineCheckStrategy (c: Context, nx:Validator) (expr: c.Expr[CheckedExceptionConfig]): nx.CheckedExceptionStrategy = {
    import c.universe._

    if (expr == null) {
      /* Use the compiler default */
      CompilerPlugin.parseCheckedExceptionStrategy(nx, CompilerPlugin.macroTimeOptionPrefix, c.compilerSettings).getOrElse {
        nx.StandardCheckedExceptionStrategy
      }
    } else if (expr.tree.tpe <:< typeOf[CheckedExceptionConfig.Standard.type]) {
      nx.StandardCheckedExceptionStrategy
    } else if (expr.tree.tpe <:< typeOf[CheckedExceptionConfig.Strict.type]) {
      nx.StrictCheckedExceptionStrategy
    } else if (expr.tree.tpe <:< typeOf[CheckedExceptionConfig.Fatal.type]) {
      nx.FatalCheckedExceptionStrategy
    } else {
      c.error(expr.tree.pos, "Unknown checked exception configuration")
      nx.StandardCheckedExceptionStrategy
    }
  }
}
