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

import scala.reflect.api.Universe

/**
 * Scala 2.11+ compatibility types.
 */
trait MacroTypes {
  /**
   * The blackbox context type for this Scala release.
   * Refer to [[http://docs.scala-lang.org/overviews/macros/blackbox-whitebox.html]] for more details.
   */
  type Context = scala.reflect.macros.blackbox.Context

  /**
   * The whitebox context type for this Scala release.
   * Refer to [[http://docs.scala-lang.org/overviews/macros/blackbox-whitebox.html]] for more details.
   */
  type WhiteboxContext = scala.reflect.macros.whitebox.Context
}

/**
 * Scala 2.11+ compatibility APIs.
 */
trait MacroCompat { self:MacroTypes =>
  /** The compile-time universe */
  val universe: Universe

  class AnnotationCompat
  class SymbolCompat
  class SymbolApiCompat
  class TypeCompat

  def termNames = universe.termNames
  def TypeName = universe.TypeName
  def TermName = universe.TermName
}