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

/**
 * No Exceptions macro implementation.
 */
object NXMacro extends MacroTypes {
  /**
   * Private implementation of the nx macro; rather than triggering compilation errors,
   * it simply returns the result of the validation.
   *
   * @param c Compiler context.
   * @param expr Expression to be scanned.
   * @tparam T Expression type.
   * @return An expression that will vend the validation results.
   */
  def nx_macro_check[T] (c: Context)(expr: c.Expr[T]): c.Expr[Set[Class[_ <: Throwable]]] = {
    import c.universe._

    /* Instantiate a macro global-based instance of the plugin core */
    val nx = new NX {
      override val universe: c.universe.type = c.universe
    }

    /* Perform the validation */
    val validator = new nx.ThrowableValidator()
    val unhandled = validator.check(expr.tree)

    /* Convert the set of unhandled throwables to an AST representing a classOf[Throwable] argument list. */
    // XXX TODO - We need to vend the errors, not just the underlying exception types.
    val types = unhandled.filter {
      case nx.UnhandledThrowable(_, tpe) => true
      case _ => false
    }.map {
      case nx.UnhandledThrowable(_, tpe) => tpe
    }
    val seqArgs = types.map(tpe => Literal(Constant(tpe))).toList

    /* Select the scala.Throwable class */
    val throwableClass = Select(Ident(definitions.ScalaPackage), newTypeName("Throwable"))

    /* Define our Class[_ <: Throwable] type */
    val existentialClassType = ExistentialTypeTree(
      /* Define a new applied Class[T] type of _$1 */
      AppliedTypeTree(Ident(definitions.ClassClass), List(Ident(newTypeName("_$1")))),

      /* Define type _$1 = Class[_ <: Throwable] */
      List(TypeDef(Modifiers(Flag.DEFERRED /* | SYNTHETIC */), newTypeName("_$1"), List(), TypeBoundsTree(Ident(definitions.NothingClass), throwableClass)))
    )

    /* Compose the Seq[_ <: Throwable](unhandled:_*) return value */
    c.Expr(Select(Apply(TypeApply(Ident(definitions.List_apply), List(existentialClassType)), seqArgs), newTermName("toSet")))
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
