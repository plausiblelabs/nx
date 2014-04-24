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
 * Scala 2.10 compatibility types.
 */
trait MacroTypes {
  /**
   * The blackbox context type for this Scala release.
   * Refer to [[http://docs.scala-lang.org/overviews/macros/blackbox-whitebox.html]] for more details.
   */
  type Context = scala.reflect.macros.Context

  /**
   * The whitebox context type for this Scala release.
   * Refer to [[http://docs.scala-lang.org/overviews/macros/blackbox-whitebox.html]] for more details.
   */
  type WhiteboxContext = scala.reflect.macros.Context
}

/**
 * Scala 2.10 compatibility APIs.
 */
trait MacroCompat { self:MacroTypes =>
  /** The compile-time universe */
  val universe: Universe

  import universe._

  /** Compatibility alias for `nme`. `nme` was was renamed to `termNames` in >= 2.11 */
  def termNames = nme

  /** 2.10 compatibility shims. */
  implicit class TypeCompat (val tpe: Type) {
    /** `declarations` was renamed to `decls` in 2.11 */
    def decls = tpe.declarations
  }

  /** 2.10 compatibility shims. */
  implicit class AnnotationCompat (val annotation: Annotation) {
    /** The AnnotationExtractor API was deprecated in 2.11; This mock-up of the new tree-based API provides
      * just enough of the API to satisfy our usage requirements in a way that's source-compatible with 2.11 */
    object tree {
      def tpe = annotation.tpe
      object children {
        def tail = annotation.scalaArgs
      }
    }
  }

  /** 2.10 compatibility shims. */
  implicit class SymbolCompat (val symbol: Symbol) {
    /** `allOverriddenSymbols` was renamed to `overrides` in 2.11 */
    def overrides = symbol.allOverriddenSymbols
  }

  /** 2.10 compatibility shims. */
  implicit class SymbolApiCompat (val api: SymbolApi) {
    /** `companionSymbol` was renamed to `companion` in 2.11 */
    def companion = api.companionSymbol
  }

  /**
   * 2.10 compatibility shims for the 2.11 TermName API.
   */
  object TermName {
    def apply (s: String) = newTermName(s)
    def unapply (name: TermName): Option[String] = Some(name.toString)
  }

  /**
   * 2.10 compatibility shims for the 2.11 TypeName API.
   */
  object TypeName {
    def apply(s: String) = newTypeName(s)
    def unapply(name: TypeName): Option[String] = Some(name.toString)
  }
}