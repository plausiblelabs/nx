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
 * Defines the NX error types and operations.
 */
private trait Errors { self:Core =>
  import universe._

  /**
   * An NX validation error.
   */
  trait ValidationError {
    /** The error position. */
    val pos: Position

    /** The error message. */
    val message: String
  }

  /**
   * An unhandled exception that is known to be throwable at `position`.
   *
   * @param pos The position at which the throwable may be raised.
   * @param throwableType The throwable's type.
   */
  case class UnhandledThrowable (pos: Position, throwableType: Type) extends ValidationError {
    override val message:String  = s"unreported exception ${throwableType.typeSymbol.name}; must be caught or declared to be thrown. " +
      "Consider the use of monadic error handling, such as scala.util.Either."
  }

  /**
   * The overridden method declares non-matching @throws annotations.
   *
   * @param pos The position of the error in the overridding method definition.
   * @param throwableType The non-useable throwable's type.
   * @param method The overridding method's symbol.
   * @param parentMethod The overridden method's symbol.
   */
  case class CannotOverride (pos: Position, throwableType: Type, method: Symbol, parentMethod: Symbol) extends ValidationError {
    override val message:String  = s"overridden method ${parentMethod.name} does not throw ${throwableType.typeSymbol.name}"
  }

  /**
   * A @throws annotation could not be parsed.
   *
   * @param pos The error position.
   * @param message A descriptive error message.
   */
  case class InvalidThrowsAnnotation (pos: Position, message: String) extends ValidationError
}
