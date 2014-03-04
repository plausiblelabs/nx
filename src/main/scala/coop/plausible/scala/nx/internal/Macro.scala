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

package coop.plausible.scala.nx.internal

import coop.plausible.scala.nx.ValidationResult.{ValidationError, UnhandledThrowable, InvalidThrowsAnnotation, CannotOverride}
import coop.plausible.scala.nx.{NX, ValidationResult}

/**
 * No Exceptions macro implementation.
 */
object Macro extends MacroTypes {

  /**
   * Private implementation of the nx macro; rather than triggering compilation errors,
   * it simply returns the set of unhandled exceptions.
   *
   * No other validation errors are returned.
   *
   * @param c Compiler context.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return An expression that will vend the validation results.
   */
  def nx_macro_unhandled[T] (c: Context)(expr: c.Expr[T]): c.Expr[Set[Class[_ <: Throwable]]] = {
    /* <= 2.10 compatibility shims */
    val compat = new MacroCompat with MacroTypes {
      override val context: c.type = c
    }
    import compat.TermName
    import c.universe.{TermName => _, _}

    /* Return just the unhandled values (ValidationResult.unhandled) */
    val resultExpr = nx_macro_check(c)(expr).in(c.universe.rootMirror)
    c.Expr(Select(resultExpr.tree, TermName("unhandled")))
  }

  /**
   * Private implementation of the nx macro; rather than triggering compilation errors,
   * it simply returns the result of the validation.
   *
   * @param c Compiler context.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return An expression that will vend the validation results.
   */
  def nx_macro_check[T] (c: Context)(expr: c.Expr[T]): c.Expr[ValidationResult] = {
    /* <= 2.10 compatibility shims */
    val compat = new MacroCompat with MacroTypes {
      override val context: c.type = c
    }
    import compat.{TypeName, TermName}
    import c.universe.{TypeName => _, TermName => _, _}

    /* Instantiate a macro global-based instance of the plugin core */
    val nx = new NX {
      override val universe: c.universe.type = c.universe
    }

    /* Perform the validation */
    val validator = new nx.ThrowableValidator()
    val errors = validator.check(expr.tree)

    /* Extract the set of unhandled throwables */
    val types = errors.view.filter {
      case nx.UnhandledThrowable(_, tpe) => true
      case _ => false
    }.map {
      case nx.UnhandledThrowable(_, tpe) => tpe
    }.toSet

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
   * Implementation of the nx macro. Refer to [[NX.nx]] for the public API.
   *
   * @param c Compiler context.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return The original expression, or a compiler error.
   */
  def nx_macro[T] (c: Context)(expr: c.Expr[T]): c.Expr[T] = {
    /* Instantiate a macro global-based instance of the plugin core */
    val core = new NX {
      override val universe: c.universe.type = c.universe
    }

    /* Perform the validation */
    val validator = new core.ThrowableValidator()
    val unhandled = validator.check(expr.tree)

    /* Report any unhandled throwables */
    if (unhandled.size > 0)
      println(s"Unhandled: $unhandled")

    /* Return the original, unmodified expression */
    expr
  }
}
