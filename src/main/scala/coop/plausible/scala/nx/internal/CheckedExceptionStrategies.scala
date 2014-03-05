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

import coop.plausible.scala.nx.NX

/**
 * Defines the NX checked/unchecked strategies.
 */
private[nx] trait CheckedExceptionStrategies { self:Core =>
  import universe._

  /**
   * A strategy to use when marking exceptions as checked/unchecked. Unchecked exceptions will
   * not trigger an error if left unhandled.
   */
  trait CheckedExceptionStrategy {
    /**
     * Return true if the given throwable type should be considered unchecked, false if checked.
     *
     * @param tpe A throwable type.
     * @return True if unchecked, false otherwise.
     */
    def isUnchecked (tpe: Type): Boolean
  }

  /**
   * The standard (Java) check strategy. Subtypes of `RuntimeException` and `Error` will be treated as
   * unchecked.
   */
  object StandardCheckedExceptionStrategy extends CheckedExceptionStrategy {
    override def isUnchecked (tpe: Type): Boolean = {
      if (tpe <:< typeOf[RuntimeException]) {
        true
      } else if (tpe <:< typeOf[Error]) {
        true
      } else {
        false
      }
    }
  }

  /**
   * Strict exception check strategy. Only subtypes of `Error` will be treated as unchecked.
   */
  object StrictCheckedExceptionStrategy extends CheckedExceptionStrategy {
    override def isUnchecked (tpe: Type): Boolean = {
      if (tpe <:< typeOf[Error]) {
        true
      } else {
        false
      }
    }
  }

  /**
   * Non-fatal check strategy. Only VM "fatal" exceptions will be treated as unchecked. These include:
   *
   * - VirtualMachineError
   * - AssertionError
   * - LinkageError
   */
  object NonFatalCheckedExceptionStrategy extends CheckedExceptionStrategy {
    override def isUnchecked (tpe: Type): Boolean = {
      if (tpe <:< typeOf[VirtualMachineError]) {
        true
      } else if (tpe <:< typeOf[AssertionError]) {
        true
      } else if (tpe <:< typeOf[LinkageError]) {
        true
      } else {
        false
      }
    }
  }
}

