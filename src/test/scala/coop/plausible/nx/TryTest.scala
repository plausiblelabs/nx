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

package coop.plausible.nx

import org.specs2.mutable.Specification
import java.io.{IOException, FileNotFoundException, EOFException}

/**
 * Try() tests.
 */
class TryTest extends Specification {
  "Try()" should {
    "widen the type as necessary" in {
      /* This should compile with the widened `IOException` type. */
      val result: Either[IOException, Int] = Try {
        if (false) throw new EOFException()
        if (false) throw new FileNotFoundException()
        42
      }

      result must beRight(42)
    }

    "catch matching exceptions" in {
      /* This bit of indirection allows us to avoid dead-code warnings on
       * code that immediately throws an exception. */
      @throws[IOException] def throwException (flag: Boolean) = if (flag) throw new IOException()

      Try { throwException(true) } must beLeft.like {
        case e:IOException => ok
      }
    }
  }
}
