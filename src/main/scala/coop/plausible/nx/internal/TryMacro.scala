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

/**
 * Try() macro implementation.
 */
object TryMacro extends MacroTypes {
  /**
   * Private implementation of the Try macro.
   *
   * @param c Compiler context.
   * @param expr Expression to be wrapped.
   * @tparam E Error type.
   * @tparam T Expression type.
   * @return An expression that will evaluate `expr` with a result type of Either[E, T].
   */
  def Try[E <: Throwable, T] (c: Context) (expr: c.Expr[T]): c.Expr[Either[E, T]] = {
    /* <= 2.10 compatibility shims */
    val compat = new MacroCompat with MacroTypes { override val context: c.type = c }
    import compat.{TypeName, TermName}
    import c.universe.{TypeName => _, TermName => _, Try => TryT, _}

    /* Set up an NX context within our macro universe */
    val nx = new Validator {
      override val universe: c.universe.type = c.universe
    }

    /* Determine the compiler's checked exception strategy */
    val checkedExceptionStrategy = CompilerPlugin.parseCheckedExceptionStrategy(nx, CompilerPlugin.macroTimeOptionPrefix, c.compilerSettings).getOrElse {
      nx.StandardCheckedExceptionStrategy
    }

    /* Fetch the list of unhandled exceptions */
    val validator = new nx.ThrowableValidator(checkedExceptionStrategy)
    val validationErrors = validator.check(expr.tree)
    val unhandled = Macro.extractUnhandledExceptionTypes(nx)(validationErrors)

    /* If no exceptions are unhandled, there's nothing to do */
    val LeftClass = typeOf[Left[_, _]]
    val RightClass = typeOf[Right[_, _]]

    val Right_apply = Select(Ident(RightClass.typeSymbol.companionSymbol), TermName("apply"))
    val Left_apply = Select(Ident(LeftClass.typeSymbol.companionSymbol), TermName("apply"))

    if (unhandled.size == 0) {
      /* Simply wrap the original expression in a Right[Nothing, T](expr) */
      c.Expr(Apply(TypeApply(Right_apply, List(Ident(definitions.NothingClass), Ident(expr.tree.tpe.typeSymbol))), List(expr.tree)))
    } else {
      /* Otherwise, determine a sufficiently wide type that can serve as a superclass of all the found exceptions. */
      val baseClasses = unhandled.head.baseClasses.filter(_.asType.toType <:< typeOf[Throwable])

      /* Find a base class that *all* exceptions are subclasses of */
      val baseClass = baseClasses.find(baseClass => unhandled.count(_ <:< baseClass.asType.toType) == unhandled.size).getOrElse {
        c.abort(c.enclosingPosition, s"The discovered throwables $unhandled do not share a common base class in $baseClasses. This should be impossible.")
      }

      /* Generate try ( Right(expr) ) { catch e:baseClass => Left(e) } */
      c.Expr(
        TryT(
          /* Right(expr) */
          Apply(
            TypeApply(Right_apply, List(Ident(baseClass), Ident(expr.tree.tpe.typeSymbol))),
            List(expr.tree)
          ),
          List (
            /* case e:baseClass => Left(e) */
            CaseDef(
              Bind(TermName("e"), Typed(Ident(nme.WILDCARD), Ident(baseClass))),
              EmptyTree,
              Apply(
                TypeApply(Left_apply, List(Ident(baseClass), Ident(expr.tree.tpe.typeSymbol))),
                List(Ident(TermName("e")))
              )
            )
          ),
          EmptyTree
        )
      )
    }
  }
}
