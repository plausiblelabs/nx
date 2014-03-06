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
 * NX validation results.
 *
 * These types mirror the internal validation types used by NX, but are made visible at runtime, rather than as
 * compilation errors.
 */
private[nx] object ValidationResult {
  /**
   * An NX validation error.
   */
  trait ValidationError {
    /** The error message. */
    val message: String
  }

  /**
   * An unhandled exception that is known to be throwable at `position`.
   *
   * @param throwable The throwable's type.
   */
  case class UnhandledThrowable[T <: Throwable] (throwable: Class[T]) extends ValidationError {
    override val message:String  = s"unreported exception ${throwable.getSimpleName}; must be caught or declared to be thrown. " +
      "Consider the use of monadic error handling, such as scala.util.Either."
  }

  /**
   * The overridden methodSymbol declares non-matching @throws annotations.
   *
   * @param throwable The throwable's type.
   */
  case class CannotOverride[T <: Throwable] (parentMethodName: String, throwable: Class[T]) extends ValidationError {
    override val message:String  = s"overridden methodSymbol $parentMethodName does not throw ${throwable.getSimpleName}"
  }

  /**
   * A @throws annotation could not be parsed.
   *
   * @param message A descriptive error message.
   */
  case class InvalidThrowsAnnotation (message: String) extends ValidationError
}

/**
 * Runtime validation result returned by [[Validator]]
 *
 * @param errors All errors encountered, in the order they were encountered.
 * @param unhandled The full set of unhandled throwable classes.
 */
private[nx] case class ValidationResult (errors: Seq[ValidationResult.ValidationError], unhandled: Set[Class[_ <: Throwable]])