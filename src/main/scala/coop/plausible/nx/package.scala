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

package coop.plausible

import coop.plausible.nx.internal.ValidationResult

/**
 * Compile time exception validation
 */
package object nx {
  import scala.language.experimental.macros
  import internal.Macros

  /**
   * Scan `expr` for unhandled exception errors. Compiler errors will be triggered for any unhandled exceptions.
   *
   * @param expr The expression to be scanned.
   * @tparam T The expression type.
   * @return The expression result, or a compiler error if the expression contained unchecked exceptions.
   */
  def exceptionChecked[T] (expr: T): T = macro Macros.exception_checked[T]

  /**
   * Scan `expr` for unhandled exception errors. Compiler errors will be triggered for any unhandled exceptions.
   *
   * @param checked The checked exception configuration to be used when scanning `expr`.
   * @param expr The expression to be scanned.
   * @tparam T The expression type.
   * @return The expression result, or a compiler error if the expression contained unchecked exceptions.
   */
  def exceptionChecked[T] (checked: CheckedExceptionConfig) (expr: T): T = macro Macros.excpetion_checked_config[T]


  /**
   * Assert that the given checked exception `T` is not thrown by `expr`, excluding `T` from checked exception validation
   * within.
   *
   * Example usage:
   *
   * {{{
   *   val result = assertNoThrows[UnknownHostException](java.net.InetAddress.getByName("127.0.0.1"))
   * }}}
   *
   * If the exception is thrown, it will be wrapped in a `java.lang.AssertionError` and rethrown.
   *
   * @tparam T The exception type to be asserted.
   * @return A new [[NonThrowAssertion]] instance that may be applied to a checked exception throwing expression.
   */
  def assertNonThrows[T <: Throwable]: NonThrowAssertion[T] = new NonThrowAssertion[T]


  /* Private testing methods */


  /**
   * Validate `expr` and return the validation results. Supports unit testing the checked exception implementation.
   *
   * Example usage:
   * {{{
   *   val result: ValidationResult = NX.check {
   *      java.inet.InetAddress.getByName("some host")
   *   }
   * }}}
   *
   * Since java.inet.InetAddress.getByName() declares that it throws an UnknownHostException, the result
   * of NX.check will be a ValidationResult(errors, Set(classOf[UnknownHostException])).
   *
   * @param expr The expression to be scanned.
   * @tparam T The expression type.
   * @return The validation result.
   */
  private[nx] def verify[T] (expr: T): ValidationResult = macro Macros.nx_verify[T]

  /**
   * Validate `expr` and return the validation results. Supports unit testing the checked exception implementation.
   *
   * Example usage:
   * {{{
   *   val result: ValidationResult = NX.check {
   *      java.inet.InetAddress.getByName("some host")
   *   }
   * }}}
   *
   * Since java.inet.InetAddress.getByName() declares that it throws an UnknownHostException, the result
   * of NX.check will be a ValidationResult(errors, Set(classOf[UnknownHostException])).
   *
   * @param checked The checked exception configuration to be used when scanning `expr`.
   * @param expr The expression to be scanned.
   * @tparam T The expression type.
   * @return The validation result.
   */
  private[nx] def verify[T] (checked: CheckedExceptionConfig) (expr: T): ValidationResult = macro Macros.nx_verify_config[T]
}
